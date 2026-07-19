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
package org.unitime.timetable.server.courses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ClassDetailInterface.ClassDetailRequest;
import org.unitime.timetable.gwt.shared.ClassDetailInterface.ClassDetailResponse;
import org.unitime.timetable.gwt.shared.ClassDetailInterface.ClassInstructorInfo;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.comparators.InstructorComparator;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backend for the migrated Class Detail page (legacy classDetail.action).
 * See {@link org.unitime.timetable.gwt.shared.ClassDetailInterface}. Mirrors the
 * read path of {@code ClassDetailAction.doLoad()}; gated by {@link Right#ClassDetail}.
 * Assigned time/room come from the class' committed assignment only.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ClassDetailRequest.class)
public class ClassDetailBackend implements GwtRpcImplementation<ClassDetailRequest, ClassDetailResponse> {

	@Override
	public ClassDetailResponse execute(ClassDetailRequest request, SessionContext context) {
		Long classId = request.getClassId();
		if (classId == null)
			throw new GwtRpcException("No class was specified.");

		context.checkPermission(classId, "Class_", Right.ClassDetail);

		Class_ c = Class_DAO.getInstance().get(classId);
		if (c == null)
			throw new GwtRpcException("Class " + classId + " was not found.");

		SchedulingSubpart ss = c.getSchedulingSubpart();
		InstrOfferingConfig ioc = ss.getInstrOfferingConfig();
		InstructionalOffering io = ioc.getInstructionalOffering();
		CourseOffering cco = ss.getControllingCourseOffering();

		ClassDetailResponse r = new ClassDetailResponse();
		r.setClassId(c.getUniqueId());
		r.setOfferingId(io.getUniqueId());
		r.setSubpartId(ss.getUniqueId());
		r.setSection(str(c.getSectionNumberString()));
		r.setClassName(str(c.getClassLabel()));

		String itypeDesc = str(c.getItypeDesc());
		if (io.hasMultipleConfigurations())
			itypeDesc += " [" + ioc.getName() + "]";
		r.setItypeDesc(itypeDesc);

		r.setCourseName(io.getCourseName());
		r.setCourseTitle(str(cco.getTitle()));
		r.setCrosslisted(io.getCourseOfferings().size() > 1);
		r.setCancelled(c.isCancelled());

		if (c.getParentClass() != null) {
			r.setParentClassName(c.getParentClass().toString());
			r.setParentClassId(c.getParentClass().getUniqueId());
		}

		r.setExpectedCapacity(limit(c));
		r.setEnrollment(c.getEnrollment() == null ? "" : c.getEnrollment().toString());
		r.setSnapshotLimit(c.getSnapshotLimit() == null ? "" : c.getSnapshotLimit().toString());
		r.setDatePattern(datePattern(c));
		r.setDatePatternId(datePatternId(c));
		r.setRoomRatio(c.getRoomRatio() == null ? "" : c.getRoomRatio().toString());
		r.setNbrRooms(c.getNbrRooms());
		r.setSplitAttendance(c.isRoomsSplitAttendance());
		r.setManagingDept(c.getManagingDept() == null ? "" : str(c.getManagingDept().getManagingDeptLabel()));
		r.setFundingDept(fundingDept(c));
		r.setLms(c.getLms() == null ? "" : str(c.getLms().getLabel()));
		r.setNotes(c.getNotes() == null ? "" : c.getNotes());
		r.setSchedulePrintNote(c.getSchedulePrintNote() == null ? "" : c.getSchedulePrintNote());
		r.setDisplayInstructor(c.isDisplayInstructor());
		r.setEnabledForStudentScheduling(c.isEnabledForStudentScheduling());

		Assignment a = committedAssignment(c);
		r.setTime(assignedTime(a));
		r.setRoom(assignedRoom(a));

		List<ClassInstructor> instructors = new ArrayList<ClassInstructor>(c.getClassInstructors());
		Collections.sort(instructors, new InstructorComparator(context));
		for (ClassInstructor classInstr : instructors) {
			if (classInstr.getInstructor() == null) continue;
			ClassInstructorInfo ci = new ClassInstructorInfo();
			ci.setInstructorId(classInstr.getInstructor().getUniqueId());
			ci.setName(classInstr.getInstructor().getNameLastFirst());
			ci.setLead(classInstr.isLead());
			ci.setShare(share(classInstr));
			r.addInstructor(ci);
		}

		return r;
	}

	private static String str(String s) { return s == null ? "" : s.trim(); }

	private static String datePattern(Class_ c) {
		try {
			DatePattern dp = c.getDatePattern();
			if (dp == null) dp = c.effectiveDatePattern();
			return dp == null ? "" : dp.getName();
		} catch (Exception e) {
			return "";
		}
	}

	private static Long datePatternId(Class_ c) {
		try {
			DatePattern dp = c.getDatePattern();
			if (dp == null) dp = c.effectiveDatePattern();
			return dp == null ? null : dp.getUniqueId();
		} catch (Exception e) {
			return null;
		}
	}

	private static String fundingDept(Class_ c) {
		try {
			if (c.getEffectiveFundingDept() != null)
				return str(c.getEffectiveFundingDept().getLabel());
		} catch (Exception e) {}
		return "";
	}

	private static String share(ClassInstructor ci) {
		try {
			int pct = ci.getPercentShare();
			return pct != 0 ? pct + "%" : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static String limit(Class_ c) {
		Integer exp = c.getExpectedCapacity();
		Integer max = c.getMaxExpectedCapacity();
		if (exp == null) return max == null ? "" : max.toString();
		if (max == null || max.equals(exp)) return exp.toString();
		return exp + "-" + max;
	}

	private static Assignment committedAssignment(Class_ c) {
		try {
			return c.getCommittedAssignment();
		} catch (Exception e) {
			return null;
		}
	}

	private static String assignedTime(Assignment a) {
		if (a == null) return "";
		try {
			return str(a.getTimeLocation().getDayHeader()) + " " + str(a.getTimeLocation().getStartTimeHeader(true));
		} catch (Exception e) {
			return "";
		}
	}

	private static String assignedRoom(Assignment a) {
		if (a == null) return "";
		try {
			StringBuilder sb = new StringBuilder();
			for (Location loc : a.getRooms()) {
				if (loc == null) continue;
				if (sb.length() > 0) sb.append(", ");
				sb.append(loc.getLabel());
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
