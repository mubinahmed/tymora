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
package org.unitime.timetable.server.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.gwt.shared.SimpleListInterface.SimpleListRequest;
import org.unitime.timetable.gwt.shared.SimpleListInterface.SimpleListResponse;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.DistributionType;
import org.unitime.timetable.model.ItypeDesc;
import org.unitime.timetable.model.TimePattern;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.SolverGroup;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.DistributionTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.TimetableManagerDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only tabular listings for legacy Struts admin pages that have no other
 * GwtRpc command bean. Selected by a {@code page} key and gated per page by its
 * permission. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SimpleListRequest.class)
public class SimpleListBackend implements GwtRpcImplementation<SimpleListRequest, SimpleListResponse> {

	@Override
	public SimpleListResponse execute(SimpleListRequest request, SessionContext context) {
		String page = request.getPage();
		if (page == null)
			throw new GwtRpcException("No list page specified.");

		SimpleListResponse response = new SimpleListResponse();

		if ("sessions".equals(page)) {
			context.checkPermission(Right.AcademicSessions);
			response.setTitle("Academic Sessions");
			for (String c : new String[] { "Academic Session", "Term", "Year", "Initiative", "Status" })
				response.addColumn(c);
			List<Session> sessions = new ArrayList<Session>(SessionDAO.getInstance().findAll());
			Collections.sort(sessions, new Comparator<Session>() {
				@Override
				public int compare(Session s1, Session s2) {
					return s1.compareTo(s2);
				}
			});
			for (Session s : sessions) {
				Row r = response.addRow(s.getUniqueId());
				r.add(s.getLabel());
				r.add(s.getAcademicTerm());
				r.add(s.getAcademicYear());
				r.add(s.getAcademicInitiative());
				r.add(s.getStatusType() == null ? "" : s.getStatusType().getLabel());
			}
		} else if ("managers".equals(page)) {
			context.checkPermission(Right.TimetableManagers);
			response.setTitle("Timetable Managers");
			for (String c : new String[] { "External Id", "Name", "Email" })
				response.addColumn(c);
			List<TimetableManager> managers = new ArrayList<TimetableManager>(TimetableManagerDAO.getInstance().findAll());
			Collections.sort(managers, new Comparator<TimetableManager>() {
				@Override
				public int compare(TimetableManager m1, TimetableManager m2) {
					int cmp = str(m1.getLastName()).compareToIgnoreCase(str(m2.getLastName()));
					if (cmp != 0) return cmp;
					cmp = str(m1.getFirstName()).compareToIgnoreCase(str(m2.getFirstName()));
					if (cmp != 0) return cmp;
					return m1.getUniqueId().compareTo(m2.getUniqueId());
				}
			});
			for (TimetableManager m : managers) {
				Row r = response.addRow(m.getUniqueId());
				r.add(m.getExternalUniqueId());
				StringBuilder name = new StringBuilder();
				if (m.getLastName() != null && !m.getLastName().isEmpty()) name.append(m.getLastName());
				if (m.getFirstName() != null && !m.getFirstName().isEmpty())
					name.append(name.length() > 0 ? ", " : "").append(m.getFirstName());
				if (m.getMiddleName() != null && !m.getMiddleName().isEmpty()) name.append(' ').append(m.getMiddleName());
				r.add(name.toString());
				r.add(m.getEmailAddress());
			}
		} else if ("solverGroups".equals(page)) {
			Long sessionId = context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId();
			if (sessionId == null)
				throw new GwtRpcException("No academic session is selected.");
			context.checkPermission(sessionId, "Session", Right.SolverGroups);
			response.setTitle("Solver Groups");
			for (String c : new String[] { "Abbreviation", "Name", "Departments" })
				response.addColumn(c);
			List<SolverGroup> groups = new ArrayList<SolverGroup>(SolverGroup.findBySessionId(sessionId));
			Collections.sort(groups, new Comparator<SolverGroup>() {
				@Override
				public int compare(SolverGroup g1, SolverGroup g2) {
					int cmp = str(g1.getAbbv()).compareToIgnoreCase(str(g2.getAbbv()));
					if (cmp != 0) return cmp;
					return g1.getUniqueId().compareTo(g2.getUniqueId());
				}
			});
			for (SolverGroup g : groups) {
				Row r = response.addRow(g.getUniqueId());
				r.add(g.getAbbv());
				r.add(g.getName());
				TreeSet<String> depts = new TreeSet<String>();
				if (g.getDepartments() != null)
					for (Department d : g.getDepartments())
						depts.add(d.getDeptCode() + " - " + d.getName());
				StringBuilder sb = new StringBuilder();
				for (String d : depts)
					sb.append(sb.length() > 0 ? ", " : "").append(d);
				r.add(sb.toString());
			}
		} else if ("instructionalTypes".equals(page)) {
			context.checkPermission(Right.InstructionalTypes);
			response.setTitle("Instructional Types");
			for (String c : new String[] { "Abbreviation", "Name", "Reference", "Organized" })
				response.addColumn(c);
			for (ItypeDesc it : ItypeDesc.findAll(false)) {
				Row r = response.addRow(it.getItype() == null ? null : it.getItype().longValue());
				r.add(it.getAbbv());
				r.add(it.getDesc());
				r.add(it.getSis_ref());
				r.add(Boolean.TRUE.equals(it.getOrganized()) ? "Yes" : "No");
			}
		} else if ("distributionTypes".equals(page)) {
			context.checkPermission(Right.DistributionTypes);
			response.setTitle("Distribution Types");
			for (String c : new String[] { "Abbreviation", "Description", "Visible" })
				response.addColumn(c);
			List<DistributionType> types = new ArrayList<DistributionType>(DistributionTypeDAO.getInstance().findAll());
			Collections.sort(types, new Comparator<DistributionType>() {
				@Override
				public int compare(DistributionType a, DistributionType b) {
					int cmp = str(a.getAbbreviation()).compareToIgnoreCase(str(b.getAbbreviation()));
					if (cmp != 0) return cmp;
					return a.getUniqueId().compareTo(b.getUniqueId());
				}
			});
			for (DistributionType t : types) {
				Row r = response.addRow(t.getUniqueId());
				r.add(t.getAbbreviation());
				r.add(t.getDescr());
				r.add(Boolean.TRUE.equals(t.getVisible()) ? "Yes" : "No");
			}
		} else if ("datePatterns".equals(page)) {
			Long sessionId = context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId();
			if (sessionId == null)
				throw new GwtRpcException("No academic session is selected.");
			context.checkPermission(sessionId, "Session", Right.DatePatterns);
			response.setTitle("Date Patterns");
			for (String c : new String[] { "Name", "Type" })
				response.addColumn(c);
			for (DatePattern dp : DatePattern.findAll(sessionId, true, null, null)) {
				Row r = response.addRow(dp.getUniqueId());
				r.add(dp.getName());
				r.add(datePatternType(dp.getType()));
			}
		} else if ("timePatterns".equals(page)) {
			Long sessionId = context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId();
			if (sessionId == null)
				throw new GwtRpcException("No academic session is selected.");
			context.checkPermission(sessionId, "Session", Right.TimePatterns);
			response.setTitle("Time Patterns");
			for (String c : new String[] { "Name", "Meetings", "Minutes / Meeting" })
				response.addColumn(c);
			List<TimePattern> patterns = new ArrayList<TimePattern>(TimePattern.findAll(sessionId, null));
			Collections.sort(patterns, new Comparator<TimePattern>() {
				@Override
				public int compare(TimePattern a, TimePattern b) {
					int cmp = str(a.getName()).compareToIgnoreCase(str(b.getName()));
					if (cmp != 0) return cmp;
					return a.getUniqueId().compareTo(b.getUniqueId());
				}
			});
			for (TimePattern tp : patterns) {
				Row r = response.addRow(tp.getUniqueId());
				r.add(tp.getName());
				r.add(tp.getNrMeetings() == null ? "" : tp.getNrMeetings().toString());
				r.add(tp.getMinPerMtg() == null ? "" : tp.getMinPerMtg().toString());
			}
		} else if ("statusTypes".equals(page)) {
			context.checkPermission(Right.StatusTypes);
			response.setTitle("Status Types");
			for (String c : new String[] { "Reference", "Label" })
				response.addColumn(c);
			for (DepartmentStatusType st : DepartmentStatusType.findAllForSession(false)) {
				Row r = response.addRow(st.getUniqueId());
				r.add(st.getReference());
				r.add(st.getLabel());
			}
		} else if ("instructors".equals(page)) {
			response.setTitle("Instructors");
			for (String c : new String[] { "External Id", "Name", "Email", "Department", "Position" })
				response.addColumn(c);
			List<DepartmentalInstructor> instructors = new ArrayList<DepartmentalInstructor>();
			for (Department d : Department.getUserDepartments(context.getUser())) {
				if (!context.hasPermission(d, Right.Instructors)) continue;
				if (d.getInstructors() != null)
					instructors.addAll(d.getInstructors());
			}
			Collections.sort(instructors, new Comparator<DepartmentalInstructor>() {
				@Override
				public int compare(DepartmentalInstructor a, DepartmentalInstructor b) {
					int cmp = str(a.getLastName()).compareToIgnoreCase(str(b.getLastName()));
					if (cmp != 0) return cmp;
					cmp = str(a.getFirstName()).compareToIgnoreCase(str(b.getFirstName()));
					if (cmp != 0) return cmp;
					return a.getUniqueId().compareTo(b.getUniqueId());
				}
			});
			for (DepartmentalInstructor di : instructors) {
				Row r = response.addRow(di.getUniqueId());
				r.add(di.getExternalUniqueId());
				StringBuilder name = new StringBuilder();
				if (di.getLastName() != null && !di.getLastName().isEmpty()) name.append(di.getLastName());
				if (di.getFirstName() != null && !di.getFirstName().isEmpty())
					name.append(name.length() > 0 ? ", " : "").append(di.getFirstName());
				if (di.getMiddleName() != null && !di.getMiddleName().isEmpty()) name.append(' ').append(di.getMiddleName());
				r.add(name.toString());
				r.add(di.getEmail());
				r.add(di.getDepartment() == null ? "" : di.getDepartment().getDeptCode() + " - " + di.getDepartment().getName());
				r.add(di.getPositionType() == null ? "" : di.getPositionType().getLabel());
			}
		} else {
			throw new GwtRpcException("Unknown list page: " + page);
		}

		return response;
	}

	private static String str(String s) { return s == null ? "" : s; }

	private static String datePatternType(Integer type) {
		if (type == null) return "";
		int t = type.intValue();
		if (t == DatePattern.sTypeStandard) return "Standard";
		if (t == DatePattern.sTypeAlternate) return "Alternate Weeks";
		if (t == DatePattern.sTypeNonStandard) return "Non-standard";
		if (t == DatePattern.sTypeExtended) return "Extended";
		if (t == DatePattern.sTypePatternSet) return "Pattern Set";
		return String.valueOf(t);
	}
}
