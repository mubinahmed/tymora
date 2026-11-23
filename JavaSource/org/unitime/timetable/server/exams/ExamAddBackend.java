/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
*/
package org.unitime.timetable.server.exams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.unitime.timetable.util.NameFormat;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.defaults.UserProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamAddInterface.ExamAddRequest;
import org.unitime.timetable.gwt.shared.ExamAddInterface.ExamAddResponse;
import org.unitime.timetable.gwt.shared.ExamAddInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.ExamAddInterface.IdLabel;
import org.unitime.timetable.gwt.shared.ExamAddInterface.Operation;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Preference;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.SolverParameterDef;
import org.unitime.timetable.model.SolverParameterGroup;
import org.unitime.timetable.model.comparators.DepartmentalInstructorComparator;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.ExamTypeDAO;
import org.unitime.timetable.model.dao.InstrOfferingConfigDAO;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * Add Examination backend (legacy examEdit.action in add mode, reached with
 * firstType / firstId). LOAD returns the defaults (first applicable examination
 * type, preferring Final; length from the first examination period of the type;
 * maximum number of rooms from the Exams.MaxRooms solver parameter; Exam
 * seating), the owner label and the instructors of the owner's department.
 * SAVE creates the Exam with a single ExamOwner, instructors, generated or
 * given name and default preferences, mirroring ExamEditAction.doUpdate, and
 * records a ChangeLog entry. Period / room preferences are left at the
 * defaults (edited on their own screens). Gated by {@link Right#ExaminationAdd}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamAddRequest.class)
public class ExamAddBackend implements GwtRpcImplementation<ExamAddRequest, ExamAddResponse> {

	@Override
	public ExamAddResponse execute(ExamAddRequest request, SessionContext context) {
		context.checkPermission(Right.ExaminationAdd);
		if (context.getUser() == null || context.getUser().getCurrentAcademicSessionId() == null)
			throw new GwtRpcException("No academic session is selected.");

		if (request.getOperation() == Operation.SAVE)
			return save(request, context);
		return load(request, context);
	}

	/** Applicable (used) examination types, as the legacy page lists them. */
	protected List<ExamType> examTypes(SessionContext context) {
		return ExamType.findAllUsedApplicable(context.getUser(),
				context.getUser().getCurrentAuthority().hasRight(Right.DepartmentIndependent) ? DepartmentStatusType.Status.ExamTimetable : DepartmentStatusType.Status.ExamEdit);
	}

	protected Integer defaultLength(Long sessionId, ExamType type) {
		try {
			TreeSet<ExamPeriod> periods = ExamPeriod.findAll(sessionId, type.getUniqueId());
			if (!periods.isEmpty() && periods.first().getLength() != null)
				return Constants.SLOT_LENGTH_MIN * periods.first().getLength();
		} catch (Exception e) {}
		return 120;
	}

	protected ExamAddResponse load(ExamAddRequest request, SessionContext context) {
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		ExamAddResponse response = new ExamAddResponse();

		ExamOwner owner = createOwner(request.getFirstType(), request.getFirstId());
		response.setOwnerType(request.getFirstType());
		response.setOwnerLabel(ownerLabel(owner));

		ExamType selected = null;
		for (ExamType type : examTypes(context)) {
			Integer length = defaultLength(sessionId, type);
			response.addExamType(new ExamTypeInfo(type.getUniqueId(), type.getLabel(), type.getType() == ExamType.sExamTypeFinal, length));
			if (selected == null || (selected.getType() != ExamType.sExamTypeFinal && type.getType() == ExamType.sExamTypeFinal))
				selected = type;
		}
		if (selected == null)
			throw new GwtRpcException("There are no examination types with examination periods in the current academic session.");
		response.setExamTypeId(selected.getUniqueId());
		response.setLength(defaultLength(sessionId, selected));

		Integer maxRooms = 1;
		try {
			SolverParameterDef maxRoomsParam = SolverParameterDef.findByNameType("Exams.MaxRooms", SolverParameterGroup.SolverType.EXAM);
			if (maxRoomsParam != null && maxRoomsParam.getDefault() != null)
				maxRooms = Integer.valueOf(maxRoomsParam.getDefault());
		} catch (Exception e) {}
		response.setMaxNbrRooms(maxRooms);
		response.setSeatingType(Exam.sSeatingTypeExam);
		response.addSeatingOption(new IdLabel(Long.valueOf(Exam.sSeatingTypeNormal), Exam.getSeatingTypeLabel(Exam.sSeatingTypeNormal)));
		response.addSeatingOption(new IdLabel(Long.valueOf(Exam.sSeatingTypeExam), Exam.getSeatingTypeLabel(Exam.sSeatingTypeExam)));

		if (ApplicationProperty.ExaminationSizeUseLimitInsteadOfEnrollment.isTrue(selected.getReference(), selected.getType() != ExamType.sExamTypeFinal))
			response.setSizeNote("A number of enrolled students or a total limit of selected classes/courses (whichever is bigger) is used when blank");
		else
			response.setSizeNote("A number of enrolled students is used when blank");

		// Instructors of the owner's (course) department, like ExamEditAction.setupInstructors;
		// falls back to the user's departments when there is no owner.
		Set<Long> deptIds = new HashSet<Long>();
		if (owner != null && owner.getCourse() != null && owner.getCourse().getDepartment() != null)
			deptIds.add(owner.getCourse().getDepartment().getUniqueId());
		if (deptIds.isEmpty())
			for (Department d : Department.getUserDepartments(context.getUser()))
				deptIds.add(d.getUniqueId());
		if (!deptIds.isEmpty()) {
			List<DepartmentalInstructor> instructors = DepartmentalInstructorDAO.getInstance().getSession().createQuery(
					"select distinct i from DepartmentalInstructor i where i.department.session.uniqueId = :sessionId and i.department.uniqueId in (:deptIds)",
					DepartmentalInstructor.class)
					.setParameter("sessionId", sessionId)
					.setParameterList("deptIds", deptIds)
					.setCacheable(true).list();
			String nameFormat = NameFormat.toFull(UserProperty.NameFormat.get(context.getUser())); // drop-down: full names
			List<DepartmentalInstructor> sorted = new ArrayList<DepartmentalInstructor>(instructors);
			if (ApplicationProperty.InstructorsDropdownFollowNameFormatting.isTrue())
				Collections.sort(sorted, new DepartmentalInstructorComparator(nameFormat));
			else
				Collections.sort(sorted, new DepartmentalInstructorComparator());
			for (DepartmentalInstructor di : sorted)
				response.addInstructor(new IdLabel(di.getUniqueId(), di.getName(nameFormat)));
		}

		return response;
	}

	protected ExamAddResponse save(ExamAddRequest request, SessionContext context) {
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		if (request.getExamTypeId() == null)
			throw new GwtRpcException("Examination type is required.");
		if (request.getLength() == null || request.getLength() <= 0)
			throw new GwtRpcException("Examination length must be a positive number of minutes.");
		if (request.getMaxNbrRooms() != null && request.getMaxNbrRooms() < 0)
			throw new GwtRpcException("Maximum number of rooms cannot be negative.");
		if (request.getNote() != null && request.getNote().length() > 999)
			throw new GwtRpcException("Note is too long.");

		org.hibernate.Session hibSession = ExamDAO.getInstance().getSession();
		Transaction tx = null;
		Exam exam = null;
		try {
			tx = hibSession.beginTransaction();

			ExamType type = ExamTypeDAO.getInstance().get(request.getExamTypeId(), hibSession);
			if (type == null)
				throw new GwtRpcException("The selected examination type no longer exists.");
			boolean applicable = false;
			for (ExamType t : examTypes(context))
				if (t.getUniqueId().equals(type.getUniqueId())) { applicable = true; break; }
			if (!applicable)
				throw new GwtRpcException("Examination type " + type.getLabel() + " cannot be used.");

			ExamOwner owner = createOwner(request.getFirstType(), request.getFirstId());
			if (owner == null)
				throw new GwtRpcException("At least one class/course has to be specified.");

			exam = new Exam();
			exam.setSession(SessionDAO.getInstance().get(sessionId, hibSession));
			exam.setExamType(type);
			exam.setPreferences(new HashSet<Preference>());
			exam.setNote(request.getNote() == null || request.getNote().trim().isEmpty() ? null : request.getNote());
			exam.setSeatingType(request.getSeatingType() == null ? Exam.sSeatingTypeExam : request.getSeatingType());
			exam.setLength(request.getLength());
			exam.setExamSize(request.getExamSize());
			exam.setPrintOffset(request.getPrintOffset() == null || request.getPrintOffset() == 0 ? null : request.getPrintOffset());
			exam.setMaxNbrRooms(request.getMaxNbrRooms() == null ? 0 : request.getMaxNbrRooms());

			exam.setInstructors(new HashSet<DepartmentalInstructor>());
			exam.setOwners(new HashSet<ExamOwner>());
			exam.getOwners().add(owner);
			owner.setExam(exam);

			if (request.getName() == null || request.getName().trim().isEmpty())
				exam.setName(exam.generateName());
			else
				exam.setName(request.getName().trim());

			// Save the examination (and its owner) BEFORE anything persistent points at it: linking it into an
			// instructor's exam set, or running a query (generateDefaultPreferences does), triggers an auto-flush,
			// and Hibernate refuses to flush a saved instructor that references an unsaved examination
			// (TransientObjectException).
			hibSession.persist(exam);

			if (request.getInstructorIds() != null)
				for (Long instructorId : new LinkedHashSet<Long>(request.getInstructorIds())) {
					if (instructorId == null) continue;
					DepartmentalInstructor instructor = DepartmentalInstructorDAO.getInstance().get(instructorId, hibSession);
					if (instructor != null) {
						exam.getInstructors().add(instructor);
						instructor.getExams().add(exam);
					}
				}

			exam.generateDefaultPreferences(false);
			hibSession.flush();

			ChangeLog.addChange(
					hibSession,
					context,
					exam,
					ChangeLog.Source.EXAM_EDIT,
					ChangeLog.Operation.CREATE,
					exam.firstSubjectArea(),
					exam.firstDepartment());

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException("Failed to create examination: " + t.getMessage(), t);
		}

		ExamAddResponse response = new ExamAddResponse();
		response.setExamId(exam.getUniqueId());
		return response;
	}

	/** Mirrors ExamEditAction.doLoad (firstType / firstId) + ExamEditForm.getExamOwner. */
	protected static ExamOwner createOwner(String firstType, Long firstId) {
		if (firstType == null || firstId == null) return null;
		ExamOwner owner = new ExamOwner();
		if ("Class_".equals(firstType) || "Class".equals(firstType)) {
			Class_ clazz = Class_DAO.getInstance().get(firstId);
			if (clazz == null) throw new GwtRpcException("Class " + firstId + " does not exist.");
			owner.setOwner(clazz);
		} else if ("SchedulingSubpart".equals(firstType)) {
			SchedulingSubpart subpart = SchedulingSubpartDAO.getInstance().get(firstId);
			if (subpart == null) throw new GwtRpcException("Subpart " + firstId + " does not exist.");
			owner.setOwner(subpart.getInstrOfferingConfig());
		} else if ("InstrOfferingConfig".equals(firstType)) {
			InstrOfferingConfig config = InstrOfferingConfigDAO.getInstance().get(firstId);
			if (config == null) throw new GwtRpcException("Configuration " + firstId + " does not exist.");
			owner.setOwner(config);
		} else if ("InstructionalOffering".equals(firstType)) {
			InstructionalOffering offering = InstructionalOfferingDAO.getInstance().get(firstId);
			if (offering == null) throw new GwtRpcException("Offering " + firstId + " does not exist.");
			owner.setOwner(offering);
		} else if ("CourseOffering".equals(firstType)) {
			CourseOffering course = CourseOfferingDAO.getInstance().get(firstId);
			if (course == null) throw new GwtRpcException("Course " + firstId + " does not exist.");
			owner.setOwner(course);
		} else {
			throw new GwtRpcException("Unsupported examination owner type " + firstType + ".");
		}
		return owner;
	}

	protected static String ownerLabel(ExamOwner owner) {
		if (owner == null) return "";
		Object o = owner.getOwnerObject();
		try {
			if (o instanceof Class_) return ((Class_) o).getClassLabel();
			if (o instanceof InstrOfferingConfig) return ((InstrOfferingConfig) o).getCourseName() + " [" + ((InstrOfferingConfig) o).getName() + "]";
			if (o instanceof InstructionalOffering) return ((InstructionalOffering) o).getCourseName() + " (offering)";
			if (o instanceof CourseOffering) return ((CourseOffering) o).getCourseNameWithTitle();
		} catch (Exception e) {}
		return owner.getLabel();
	}
}
