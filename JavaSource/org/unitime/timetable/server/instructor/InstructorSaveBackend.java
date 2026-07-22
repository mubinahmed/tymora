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
package org.unitime.timetable.server.instructor;

import java.util.HashSet;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorEditResponse;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorSaveRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.InstructorAttribute;
import org.unitime.timetable.model.PositionType;
import org.unitime.timetable.model.dao.DepartmentDAO;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Add / Edit Instructor pages — creates a new
 * instructor (departmentId set) or updates an existing one (instructorId set),
 * mirroring {@code InstructorAction.doUpdate()}'s identity-field subset. Gated by
 * {@link Right#InstructorAdd} / {@link Right#InstructorEdit}, transactional, and
 * change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(InstructorSaveRequest.class)
public class InstructorSaveBackend implements GwtRpcImplementation<InstructorSaveRequest, InstructorEditResponse> {

	@Override
	public InstructorEditResponse execute(InstructorSaveRequest request, SessionContext context) {
		if (request.getLname() == null || request.getLname().trim().isEmpty())
			throw new GwtRpcException("Last name is required.");

		boolean isEdit = request.getInstructorId() != null;
		DepartmentalInstructorDAO idao = DepartmentalInstructorDAO.getInstance();

		DepartmentalInstructor inst;
		Department department;
		if (isEdit) {
			context.checkPermission(request.getInstructorId(), "DepartmentalInstructor", Right.InstructorEdit);
			inst = idao.get(request.getInstructorId());
			if (inst == null)
				throw new GwtRpcException("Instructor " + request.getInstructorId() + " was not found.");
			department = inst.getDepartment();
		} else {
			if (request.getDepartmentId() == null)
				throw new GwtRpcException("No department was specified for the new instructor.");
			context.checkPermission(request.getDepartmentId(), "Department", Right.InstructorAdd);
			department = DepartmentDAO.getInstance().get(request.getDepartmentId());
			if (department == null)
				throw new GwtRpcException("Department " + request.getDepartmentId() + " was not found.");
			inst = new DepartmentalInstructor();
			inst.setAttributes(new HashSet<InstructorAttribute>());
			inst.setDepartment(department);
		}

		// (external id, department) must be unique.
		String externalId = trimToNull(request.getExternalId());
		if (externalId != null && department != null) {
			String hql = "from DepartmentalInstructor where externalUniqueId = :puid and department.uniqueId = :deptId"
					+ (isEdit ? " and uniqueId != :uid" : "");
			var q = idao.getSession().createQuery(hql, DepartmentalInstructor.class)
					.setParameter("puid", externalId)
					.setParameter("deptId", department.getUniqueId());
			if (isEdit) q.setParameter("uid", inst.getUniqueId());
			if (!q.list().isEmpty())
				throw new GwtRpcException("An instructor with this external id already exists in this department.");
		}

		org.hibernate.Session hibSession = idao.getSession();
		Transaction tx = hibSession.beginTransaction();
		try {
			inst.setFirstName(trimToNull(request.getFname()));
			inst.setMiddleName(trimToNull(request.getMname()));
			inst.setLastName(request.getLname().trim());
			inst.setAcademicTitle(trimToNull(request.getTitle()));
			inst.setExternalUniqueId(externalId);
			inst.setCareerAcct(trimToNull(request.getCareerAcct()));
			inst.setEmail(request.getEmail());
			if (request.getPositionTypeId() != null)
				inst.setPositionType(PositionType.findById(request.getPositionTypeId()));
			else
				inst.setPositionType(null);
			String note = request.getNote();
			if (note != null && !note.isEmpty())
				inst.setNote(note.length() > 2048 ? note.substring(0, 2048) : note);
			else
				inst.setNote(null);
			inst.setIgnoreToFar(Boolean.valueOf(request.isIgnoreTooFar()));

			if (inst.getUniqueId() == null) {
				department.getInstructors().add(inst);
				hibSession.persist(inst);
			} else {
				hibSession.merge(inst);
			}

			ChangeLog.addChange(
					hibSession, context, inst,
					ChangeLog.Source.INSTRUCTOR_EDIT,
					isEdit ? ChangeLog.Operation.UPDATE : ChangeLog.Operation.CREATE,
					null, inst.getDepartment());

			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save instructor: " + e.getMessage(), e);
		}

		InstructorEditResponse response = new InstructorEditResponse();
		InstructorEditBackend.fillInstructor(response, inst);
		InstructorEditBackend.fillPositionTypes(response);
		response.setSaved(true);
		return response;
	}

	private static String trimToNull(String s) {
		if (s == null) return null;
		s = s.trim();
		return s.isEmpty() ? null : s;
	}
}
