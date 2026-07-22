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

import java.util.Iterator;

import org.hibernate.query.Query;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ClassAssignmentsReportInterface.ClassAssignmentsReportRequest;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.gwt.shared.SimpleListInterface.SimpleListResponse;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backend for the migrated Class Assignments report (legacy
 * classAssignmentsReportSearch.action). See
 * {@link org.unitime.timetable.gwt.shared.ClassAssignmentsReportInterface} for
 * the single-request protocol (subject-area picker vs. committed-assignment
 * listing).
 *
 * Gated by {@link Right#ClassAssignments}; scoped to the current academic
 * session. Assigned time/room/date-pattern come from the class' committed
 * assignment only (this page does not attach to a live solver), matching the
 * conservative read-only intent. Every per-cell projection is wrapped in
 * try/catch for lazy-init safety. Additive: introduces no changes to existing
 * behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ClassAssignmentsReportRequest.class)
public class ClassAssignmentsReportBackend implements GwtRpcImplementation<ClassAssignmentsReportRequest, SimpleListResponse> {

	@Override
	public SimpleListResponse execute(ClassAssignmentsReportRequest request, SessionContext context) {
		context.checkPermission(Right.ClassAssignments);

		Long sessionId = context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId();
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		SimpleListResponse response = new SimpleListResponse();

		if (request.getSubjectAreaId() == null) {
			// Subject-area picker payload.
			response.setTitle("Subject Areas");
			response.addColumn("Subject Area");
			for (SubjectArea sa : SubjectArea.getUserSubjectAreas(context.getUser())) {
				if (!sessionId.equals(sa.getSessionId())) continue;
				Row r = response.addRow(sa.getUniqueId());
				r.add(sa.getLabel());
			}
			return response;
		}

		response.setTitle("Class Assignments");
		for (String c : new String[] { "Class", "Type", "Time", "Room", "Instructor", "Date Pattern" })
			response.addColumn(c);

		org.hibernate.Session hibSession = Class_DAO.getInstance().getSession();
		StringBuilder hql = new StringBuilder();
		hql.append("select c, co from Class_ c ");
		hql.append("left join fetch c.schedulingSubpart as ss ");
		hql.append("left join fetch ss.instrOfferingConfig as ioc ");
		hql.append("left join fetch ioc.instructionalOffering as io ");
		hql.append("inner join c.schedulingSubpart.instrOfferingConfig.instructionalOffering.courseOfferings as co ");
		hql.append("where co.subjectArea.uniqueId = :subjectAreaId and co.isControl = true ");

		String courseNbr = request.getCourseNbr() == null ? null : request.getCourseNbr().trim();
		boolean hasCourseNbr = courseNbr != null && !courseNbr.isEmpty();
		if (hasCourseNbr) {
			if (courseNbr.indexOf('*') >= 0)
				hql.append("and co.courseNbr like :courseNbr ");
			else
				hql.append("and co.courseNbr = :courseNbr ");
		}

		Query<Object[]> q = hibSession.createQuery(hql.toString(), Object[].class);
		q.setParameter("subjectAreaId", request.getSubjectAreaId());
		if (hasCourseNbr)
			q.setParameter("courseNbr", courseNbr.replace('*', '%'));
		q.setCacheable(true);

		for (Object[] o : q.list()) {
			Class_ c = (Class_) o[0];
			CourseOffering co = (CourseOffering) o[1];
			Assignment a = committedAssignment(c);
			Row r = response.addRow(c.getUniqueId());
			r.add(safeLabel(c, co));
			r.add(itype(c));
			r.add(assignedTime(a));
			r.add(assignedRoom(a));
			r.add(instructors(c));
			r.add(datePattern(c));
		}

		return response;
	}

	private static String str(String s) { return s == null ? "" : s.trim(); }

	private static String safeLabel(Class_ c, CourseOffering co) {
		try {
			return str(c.getClassLabel(co));
		} catch (Exception e) {
			try { return str(c.getClassLabel()); } catch (Exception ex) { return ""; }
		}
	}

	private static String itype(Class_ c) {
		try {
			return str(c.getSchedulingSubpart() == null ? null : c.getSchedulingSubpart().getItypeDesc());
		} catch (Exception e) {
			return "";
		}
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

	private static String datePattern(Class_ c) {
		try {
			return c.effectiveDatePattern() == null ? "" : str(c.effectiveDatePattern().getName());
		} catch (Exception e) {
			return "";
		}
	}
}
