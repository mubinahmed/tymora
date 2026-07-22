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
import org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface.IdName;
import org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface.SubpartEditRequest;
import org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface.SubpartEditResponse;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.ItypeDesc;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Scheduling Subpart Edit page (legacy
 * schedulingSubpartEdit.action). Loads the editable subpart-row fields plus the
 * instructional-type and date-pattern option lists. See
 * {@link org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface}; gated by
 * {@link Right#SchedulingSubpartEdit}. The companion {@code SchedulingSubpartEditUpdateBackend} saves.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SubpartEditRequest.class)
public class SchedulingSubpartEditBackend implements GwtRpcImplementation<SubpartEditRequest, SubpartEditResponse> {

	@Override
	public SubpartEditResponse execute(SubpartEditRequest request, SessionContext context) {
		Long subpartId = request.getSubpartId();
		if (subpartId == null)
			throw new GwtRpcException("No scheduling subpart was specified.");

		context.checkPermission(subpartId, "SchedulingSubpart", Right.SchedulingSubpartEdit);

		SchedulingSubpart ss = SchedulingSubpartDAO.getInstance().get(subpartId);
		if (ss == null)
			throw new GwtRpcException("Scheduling subpart " + subpartId + " was not found.");

		SubpartEditResponse response = new SubpartEditResponse();
		fill(response, ss, context);
		return response;
	}

	/** Populate the response's read state from the subpart (shared with the save bean). */
	static void fill(SubpartEditResponse r, SchedulingSubpart ss, SessionContext context) {
		InstrOfferingConfig ioc = ss.getInstrOfferingConfig();
		InstructionalOffering io = ioc.getInstructionalOffering();
		CourseOffering co = io.getControllingCourseOffering();

		r.setSubpartId(ss.getUniqueId());
		r.setOfferingId(io.getUniqueId());

		String label = str(ss.getItype() == null ? "" : ss.getItype().getAbbv());
		if (io.hasMultipleConfigurations())
			label += " [" + ioc.getName() + "]";
		r.setInstructionalTypeLabel(label);
		r.setInstructionalType(ss.getItype() == null ? null : ss.getItype().getItype());

		r.setCourseName(io.getCourseName());
		r.setCourseTitle(str(co.getTitle()));
		r.setSubjectArea(str(co.getSubjectAreaAbbv()));
		r.setCourseNbr(str(co.getCourseNbr()));
		r.setMinutesPerWeek(ss.getMinutesPerWk());
		r.setCreditText(credit(ss));
		r.setManagingDept(ss.getManagingDept() == null ? "" : str(ss.getManagingDept().getManagingDeptLabel()));
		r.setUnlimited(Boolean.TRUE.equals(ioc.isUnlimitedEnrollment()));
		r.setAutoSpreadInTime(ss.isAutoSpreadInTime());
		r.setStudentAllowOverlap(ss.isStudentAllowOverlap());
		r.setDatePatternId(ss.getDatePattern() == null ? Long.valueOf(-1) : ss.getDatePattern().getUniqueId());
		r.setDatePatternEditable(datePatternEditable(ss));

		SchedulingSubpart parent = ss.getParentSubpart();
		if (parent != null) {
			r.setParentSubpartId(parent.getUniqueId());
			r.setParentSubpartLabel(str(parent.getSchedulingSubpartLabel()));
		}

		DatePattern inherited = inheritedDatePattern(ss);
		r.addDatePatternOption(new IdName(Long.valueOf(-1), "Default" + (inherited == null ? "" : " (" + inherited.getName() + ")")));
		try {
			for (DatePattern dp : DatePattern.findAll(context.getUser(), ss.getManagingDept(), ss.effectiveDatePattern()))
				r.addDatePatternOption(new IdName(dp.getUniqueId(), dp.getName()));
		} catch (Exception e) {}

		try {
			for (ItypeDesc it : ItypeDesc.findAll(false))
				r.addItypeOption(new IdName(it.getItype() == null ? null : it.getItype().longValue(), it.getDesc()));
		} catch (Exception e) {}
	}

	private static DatePattern inheritedDatePattern(SchedulingSubpart ss) {
		try {
			return ss.canInheritParentPreferences() ? ss.getParentSubpart().effectiveDatePattern() : ss.getSession().getDefaultDatePatternNotNull();
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean datePatternEditable(SchedulingSubpart ss) {
		try {
			return ApplicationProperty.WaitListCanChangeDatePattern.isTrue()
					|| ss.getInstrOfferingConfig().getEnrollment() == 0
					|| !ss.getInstrOfferingConfig().getInstructionalOffering().effectiveReScheduleNow();
		} catch (Exception e) {
			return true;
		}
	}

	private static String credit(SchedulingSubpart ss) {
		try {
			if (ss.getCredit() != null)
				return str(ss.getCredit().creditText());
		} catch (Exception e) {}
		return "";
	}

	static String str(String s) { return s == null ? "" : s.trim(); }
}
