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
import java.util.Iterator;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SchedulingSubpartDetailInterface.SchedulingSubpartDetailRequest;
import org.unitime.timetable.gwt.shared.SchedulingSubpartDetailInterface.SchedulingSubpartDetailResponse;
import org.unitime.timetable.gwt.shared.SchedulingSubpartDetailInterface.SubpartClassInfo;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.comparators.ClassComparator;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backend for the migrated Scheduling Subpart Detail page (legacy
 * schedulingSubpartDetail.action). See
 * {@link org.unitime.timetable.gwt.shared.SchedulingSubpartDetailInterface}.
 * Mirrors the read path of {@code SchedulingSubpartDetailAction.doLoad()} plus the
 * subpart's class list; gated by {@link Right#SchedulingSubpartDetail}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SchedulingSubpartDetailRequest.class)
public class SchedulingSubpartDetailBackend implements GwtRpcImplementation<SchedulingSubpartDetailRequest, SchedulingSubpartDetailResponse> {

	@Override
	public SchedulingSubpartDetailResponse execute(SchedulingSubpartDetailRequest request, SessionContext context) {
		Long subpartId = request.getSubpartId();
		if (subpartId == null)
			throw new GwtRpcException("No scheduling subpart was specified.");

		context.checkPermission(subpartId, "SchedulingSubpart", Right.SchedulingSubpartDetail);

		SchedulingSubpart ss = SchedulingSubpartDAO.getInstance().get(subpartId);
		if (ss == null)
			throw new GwtRpcException("Scheduling subpart " + subpartId + " was not found.");

		InstrOfferingConfig ioc = ss.getInstrOfferingConfig();
		InstructionalOffering io = ioc.getInstructionalOffering();
		CourseOffering co = io.getControllingCourseOffering();

		SchedulingSubpartDetailResponse r = new SchedulingSubpartDetailResponse();
		r.setSubpartId(ss.getUniqueId());
		r.setOfferingId(io.getUniqueId());

		String label = str(ss.getItype() == null ? "" : ss.getItype().getAbbv());
		if (io.hasMultipleConfigurations())
			label += " [" + ioc.getName() + "]";
		r.setInstructionalTypeLabel(label);

		r.setCourseName(io.getCourseName());
		r.setCourseTitle(str(co.getTitle()));
		r.setSubjectArea(str(co.getSubjectAreaAbbv()));
		r.setCourseNbr(str(co.getCourseNbr()));
		r.setMinutesPerWeek(ss.getMinutesPerWk());
		r.setDatePattern(datePattern(ss));
		r.setDatePatternId(datePatternId(ss));
		r.setCredit(credit(ss));
		r.setUnlimitedEnroll(Boolean.TRUE.equals(ioc.isUnlimitedEnrollment()));
		r.setAutoSpreadInTime(ss.isAutoSpreadInTime());
		r.setStudentAllowOverlap(ss.isStudentAllowOverlap());
		r.setManagingDept(ss.getManagingDept() == null ? "" : str(ss.getManagingDept().getManagingDeptLabel()));

		SchedulingSubpart parent = ss.getParentSubpart();
		if (parent != null) {
			r.setParentSubpartId(parent.getUniqueId());
			r.setParentSubpartLabel(str(parent.getSchedulingSubpartLabel()));
		}

		List<Class_> classes = new ArrayList<Class_>(ss.getClasses());
		Collections.sort(classes, new ClassComparator(ClassComparator.COMPARE_BY_ITYPE));
		for (Class_ c : classes) {
			SubpartClassInfo ck = new SubpartClassInfo();
			ck.setId(c.getUniqueId());
			ck.setSection(section(c, co));
			ck.setLimit(limit(c));
			ck.setEnrollment(c.getEnrollment() == null ? "" : c.getEnrollment().toString());
			Assignment a = committedAssignment(c);
			ck.setTime(assignedTime(a));
			ck.setRoom(assignedRoom(a));
			ck.setInstructors(instructors(c));
			r.addClass(ck);
		}

		return r;
	}

	private static String str(String s) { return s == null ? "" : s.trim(); }

	private static String datePattern(SchedulingSubpart ss) {
		try {
			DatePattern dp = ss.getDatePattern();
			if (dp == null) dp = ss.effectiveDatePattern();
			return dp == null ? "" : dp.getName();
		} catch (Exception e) {
			return "";
		}
	}

	private static Long datePatternId(SchedulingSubpart ss) {
		try {
			DatePattern dp = ss.getDatePattern();
			if (dp == null) dp = ss.effectiveDatePattern();
			return dp == null ? null : dp.getUniqueId();
		} catch (Exception e) {
			return null;
		}
	}

	private static String credit(SchedulingSubpart ss) {
		try {
			if (ss.getCredit() != null)
				return str(ss.getCredit().creditText());
		} catch (Exception e) {}
		return "";
	}

	private static String section(Class_ c, CourseOffering co) {
		try {
			String s = c.getClassSuffix(co);
			if (s != null && !s.isEmpty()) return s;
		} catch (Exception e) {}
		try {
			String s = c.getSectionNumberString();
			if (s != null && !s.isEmpty()) return s;
		} catch (Exception e) {}
		try {
			return str(c.getClassLabel(co));
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

	private static String instructors(Class_ c) {
		try {
			if (c.getClassInstructors() == null) return "";
			StringBuilder sb = new StringBuilder();
			for (Iterator<ClassInstructor> i = c.getClassInstructors().iterator(); i.hasNext();) {
				ClassInstructor ci = i.next();
				if (ci == null || ci.getInstructor() == null) continue;
				if (sb.length() > 0) sb.append(", ");
				sb.append(ci.getInstructor().getNameLastFirst());
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}
}
