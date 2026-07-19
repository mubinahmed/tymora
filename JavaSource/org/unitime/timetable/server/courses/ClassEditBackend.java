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

import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ClassEditInterface.ClassEditRequest;
import org.unitime.timetable.gwt.shared.ClassEditInterface.ClassEditResponse;
import org.unitime.timetable.gwt.shared.ClassEditInterface.IdName;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Class Edit page (legacy classEdit.action). Loads
 * the editable class-data fields plus the applicable date-pattern options. See
 * {@link org.unitime.timetable.gwt.shared.ClassEditInterface}; gated by
 * {@link Right#ClassEdit}. The companion {@code ClassEditUpdateBackend} saves.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ClassEditRequest.class)
public class ClassEditBackend implements GwtRpcImplementation<ClassEditRequest, ClassEditResponse> {

	@Override
	public ClassEditResponse execute(ClassEditRequest request, SessionContext context) {
		Long classId = request.getClassId();
		if (classId == null)
			throw new GwtRpcException("No class was specified.");

		context.checkPermission(classId, "Class_", Right.ClassEdit);

		Class_ c = Class_DAO.getInstance().get(classId);
		if (c == null)
			throw new GwtRpcException("Class " + classId + " was not found.");

		ClassEditResponse response = new ClassEditResponse();
		fill(response, c, context);
		return response;
	}

	/** Populate the response's read state from the class (shared with the save bean). */
	static void fill(ClassEditResponse r, Class_ c, SessionContext context) {
		SchedulingSubpart ss = c.getSchedulingSubpart();
		InstrOfferingConfig ioc = ss.getInstrOfferingConfig();
		InstructionalOffering io = ioc.getInstructionalOffering();
		CourseOffering cco = ss.getControllingCourseOffering();

		r.setClassId(c.getUniqueId());
		r.setOfferingId(io.getUniqueId());
		r.setSubpartId(ss.getUniqueId());
		r.setSection(str(c.getSectionNumberString()));
		r.setClassName(str(c.getClassLabel()));
		String itype = str(c.getItypeDesc());
		if (io.hasMultipleConfigurations())
			itype += " [" + ioc.getName() + "]";
		r.setItypeDesc(itype);
		r.setCourseName(io.getCourseName());
		r.setCourseTitle(str(cco.getTitle()));
		r.setManagingDept(c.getManagingDept() == null ? "" : str(c.getManagingDept().getManagingDeptLabel()));
		r.setLms(c.getLms() == null ? "" : str(c.getLms().getLabel()));
		r.setUnlimited(Boolean.TRUE.equals(ioc.isUnlimitedEnrollment()));

		r.setExpectedCapacity(c.getExpectedCapacity());
		r.setMaxExpectedCapacity(c.getMaxExpectedCapacity());
		r.setRoomRatio(c.getRoomRatio());
		r.setNbrRooms(c.getNbrRooms());
		r.setSplitAttendance(c.isRoomsSplitAttendance());
		r.setDatePatternId(c.getDatePattern() == null ? Long.valueOf(-1) : c.getDatePattern().getUniqueId());
		r.setNotes(c.getNotes() == null ? "" : c.getNotes());
		r.setSchedulePrintNote(c.getSchedulePrintNote() == null ? "" : c.getSchedulePrintNote());
		r.setEnabledForStudentScheduling(c.isEnabledForStudentScheduling());
		r.setDisplayInstructor(c.isDisplayInstructor());
		r.setDatePatternEditable(datePatternEditable(c));

		DatePattern effective = ss.effectiveDatePattern();
		r.addDatePatternOption(new IdName(Long.valueOf(-1), "Default" + (effective == null ? "" : " (" + effective.getName() + ")")));
		try {
			for (DatePattern dp : DatePattern.findAll(context.getUser(), c.getManagingDept(), c.effectiveDatePattern()))
				r.addDatePatternOption(new IdName(dp.getUniqueId(), dp.getName()));
		} catch (Exception e) {}
	}

	private static boolean datePatternEditable(Class_ c) {
		try {
			return ApplicationProperty.WaitListCanChangeDatePattern.isTrue()
					|| c.getEnrollment() == null || c.getEnrollment() == 0
					|| !c.getSchedulingSubpart().getInstrOfferingConfig().getInstructionalOffering().effectiveReScheduleNow();
		} catch (Exception e) {
			return true;
		}
	}

	static String str(String s) { return s == null ? "" : s.trim(); }
}
