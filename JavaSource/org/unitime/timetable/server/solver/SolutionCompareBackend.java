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
package org.unitime.timetable.server.solver;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.unitime.timetable.gwt.client.sectioning.PublishedSectioningSolutionsTable.InfoComparator;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolutionCompareInterface.Change;
import org.unitime.timetable.gwt.shared.SolutionCompareInterface.ClassRow;
import org.unitime.timetable.gwt.shared.SolutionCompareInterface.Metric;
import org.unitime.timetable.gwt.shared.SolutionCompareInterface.SolutionCompareRequest;
import org.unitime.timetable.gwt.shared.SolutionCompareInterface.SolutionCompareResponse;
import org.unitime.timetable.gwt.shared.SolutionCompareInterface.SolutionOption;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Solution;
import org.unitime.timetable.model.dao.SolutionDAO;
import org.unitime.timetable.security.Qualifiable;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.ui.AssignmentPreferenceInfo;
import org.unitime.timetable.solver.ui.PropertiesInfo;

/**
 * Side-by-side comparison of two saved course timetables (Solution rows): the solution statistics
 * (global info) of each, and class by class how the two place a class (time, room, date pattern,
 * instructors, student conflicts), flagged as same / time / room / time and room / only in one.
 *
 * Which solutions may be compared follows the Saved Timetables page (ListSolutionsBackend):
 * Right.Timetables; all solutions of the session for department-independent users, otherwise the
 * solutions of the user's solver groups; committed solutions only for users without Right.Solver.
 */
@GwtRpcImplements(SolutionCompareRequest.class)
public class SolutionCompareBackend implements GwtRpcImplementation<SolutionCompareRequest, SolutionCompareResponse> {
	private static Logger sLog = LogManager.getLogger(SolutionCompareBackend.class);

	@Override
	public SolutionCompareResponse execute(SolutionCompareRequest request, SessionContext context) {
		context.checkPermission(Right.Timetables);
		SolutionCompareResponse response = new SolutionCompareResponse();

		Map<Long, Solution> visible = new HashMap<Long, Solution>();
		for (Solution s: visibleSolutions(context)) visible.put(s.getUniqueId(), s);
		List<Solution> sorted = new ArrayList<Solution>(visible.values());
		Collections.sort(sorted, new Comparator<Solution>() {
			@Override
			public int compare(Solution a, Solution b) {
				int cmp = a.getOwner().getName().compareTo(b.getOwner().getName());
				if (cmp != 0) return cmp;
				return -a.getCreated().compareTo(b.getCreated());
			}
		});
		for (Solution s: sorted) response.getSolutions().add(option(s));

		if (request.getSolutionA() == null || request.getSolutionB() == null) {
			response.setMessage(sorted.size() < 2 ? "At least two saved timetables are needed for a comparison." : "Pick two saved timetables to compare.");
			return response;
		}
		Solution a = visible.get(request.getSolutionA()), b = visible.get(request.getSolutionB());
		if (a == null || b == null)
			throw new GwtRpcException("The selected timetable does not exist or you are not allowed to see it.");
		response.setA(option(a));
		response.setB(option(b));

		// solution statistics side by side (union of the global info keys, legacy order)
		Map<String, String> ia = globalInfo(a), ib = globalInfo(b);
		// InfoComparator orders the legacy statistics but never returns 0, so de-duplicate first, then sort
		Set<String> keySet = new HashSet<String>(ia.keySet());
		keySet.addAll(ib.keySet());
		List<String> keys = new ArrayList<String>(keySet);
		Collections.sort(keys, new InfoComparator());
		for (String key: keys) {
			Metric m = new Metric(key, ia.get(key), ib.get(key));
			m.setNumberA(number(ia.get(key)));
			m.setNumberB(number(ib.get(key)));
			response.getMetrics().add(m);
		}

		// class by class
		Map<Long, Assignment> aa = assignments(a), ab = assignments(b);
		Set<Long> classIds = new HashSet<Long>(aa.keySet());
		classIds.addAll(ab.keySet());
		List<ClassRow> rows = new ArrayList<ClassRow>();
		for (Long classId: classIds) {
			Assignment x = aa.get(classId), y = ab.get(classId);
			Class_ clazz = (x != null ? x.getClazz() : y.getClazz());
			ClassRow row = new ClassRow();
			row.setClassId(classId);
			try { row.setClassName(clazz.getClassLabel()); } catch (Exception e) { row.setClassName(x != null ? x.getClassName() : y.getClassName()); }
			try { row.setDepartment(clazz.getManagingDept().getShortLabel()); } catch (Exception e) {}
			String timeA = null, roomA = null, timeB = null, roomB = null;
			if (x != null) {
				timeA = time(x); roomA = rooms(x);
				row.setTimeA(timeA); row.setRoomA(roomA); row.setDateA(date(x)); row.setInstructorA(instructors(x));
				row.setStudentConflictsA(studentConflicts(a, clazz));
			}
			if (y != null) {
				timeB = time(y); roomB = rooms(y);
				row.setTimeB(timeB); row.setRoomB(roomB); row.setDateB(date(y)); row.setInstructorB(instructors(y));
				row.setStudentConflictsB(studentConflicts(b, clazz));
			}
			Change change;
			if (x == null) change = Change.ONLY_B;
			else if (y == null) change = Change.ONLY_A;
			else {
				boolean t = !eq(timeA, timeB) || !eq(row.getDateA(), row.getDateB());
				boolean r = !eq(roomA, roomB);
				change = (t && r ? Change.TIME_AND_ROOM : t ? Change.TIME : r ? Change.ROOM : Change.SAME);
			}
			row.setChange(change);
			response.count(change);
			rows.add(row);
		}
		Collections.sort(rows, new Comparator<ClassRow>() {
			@Override
			public int compare(ClassRow r1, ClassRow r2) {
				int cmp = Boolean.compare(r1.getChange() == Change.SAME, r2.getChange() == Change.SAME);
				if (cmp != 0) return cmp;
				return String.valueOf(r1.getClassName()).compareTo(String.valueOf(r2.getClassName()));
			}
		});
		response.getRows().addAll(rows);
		return response;
	}

	/** Same rules as the Saved Timetables page. */
	protected Collection<Solution> visibleSolutions(SessionContext context) {
		boolean committedOnly = !context.hasPermission(Right.Solver);
		boolean listAll = context.getUser().getCurrentAuthority().hasRight(Right.DepartmentIndependent);
		Collection<Solution> solutions;
		if (listAll) {
			solutions = Solution.findBySessionId(context.getUser().getCurrentAcademicSessionId());
		} else {
			List<Serializable> ids = new ArrayList<Serializable>();
			for (Qualifiable owner: context.getUser().getCurrentAuthority().getQualifiers("SolverGroup")) ids.add(owner.getQualifierId());
			if (ids.isEmpty()) return new ArrayList<Solution>();
			solutions = SolutionDAO.getInstance().getSession().createQuery("from Solution where owner.uniqueId in :ids", Solution.class).setParameterList("ids", ids).list();
		}
		List<Solution> ret = new ArrayList<Solution>();
		for (Solution s: solutions)
			if (!committedOnly || Boolean.TRUE.equals(s.isCommited())) ret.add(s);
		return ret;
	}

	private static SolutionOption option(Solution s) {
		SolutionOption o = new SolutionOption();
		o.setId(s.getUniqueId());
		o.setOwner(s.getOwner().getAbbv());
		o.setOwnerName(s.getOwner().getName());
		o.setNote(s.getNote());
		o.setCreator(s.getCreator());
		o.setCreated(s.getCreated());
		o.setCommitDate(s.getCommitDate());
		o.setCommitted(Boolean.TRUE.equals(s.isCommited()));
		try { o.setNrAssignments(s.getAssignments().size()); } catch (Exception e) {}
		return o;
	}

	private static Map<String, String> globalInfo(Solution s) {
		Map<String, String> ret = new HashMap<String, String>();
		try {
			PropertiesInfo info = (PropertiesInfo)s.getInfo("GlobalInfo");
			if (info != null)
				for (Object key: info.keySet()) ret.put((String)key, info.getProperty((String)key));
		} catch (Exception e) {
			sLog.warn("Unable to read the global info of solution " + s.getUniqueId() + ": " + e.getMessage());
		}
		return ret;
	}

	/** Leading number of a statistic such as "12.5% (3/24)" or "1,234". */
	private static Double number(String value) {
		if (value == null) return null;
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(-?[0-9][0-9,]*(\\.[0-9]+)?)").matcher(value);
		if (!m.find()) return null;
		try { return Double.valueOf(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return null; }
	}

	private static Map<Long, Assignment> assignments(Solution s) {
		Map<Long, Assignment> ret = new HashMap<Long, Assignment>();
		for (Assignment a: s.getAssignments()) ret.put(a.getClassId(), a);
		return ret;
	}

	private static String time(Assignment a) {
		// days and times only; the date pattern is reported separately
		try {
			org.cpsolver.coursett.model.TimeLocation t = a.getTimeLocation();
			return t.getDayHeader() + " " + t.getStartTimeHeader(true) + " - " + t.getEndTimeHeader(true);
		} catch (Exception e) { return ""; }
	}

	private static String date(Assignment a) {
		try { return a.getDatePattern() == null ? null : a.getDatePattern().getName(); } catch (Exception e) { return null; }
	}

	private static String rooms(Assignment a) {
		StringBuffer ret = new StringBuffer();
		if (a.getRooms() != null)
			for (Location l: new TreeSet<Location>(a.getRooms())) ret.append(ret.length() == 0 ? "" : ", ").append(l.getLabel());
		return ret.toString();
	}

	private static String instructors(Assignment a) {
		StringBuffer ret = new StringBuffer();
		if (a.getInstructors() != null)
			for (DepartmentalInstructor i: new TreeSet<DepartmentalInstructor>(a.getInstructors())) ret.append(ret.length() == 0 ? "" : ", ").append(i.getNameLastFirst());
		return ret.toString();
	}

	private static Integer studentConflicts(Solution s, Class_ clazz) {
		try {
			AssignmentPreferenceInfo info = s.getAssignmentInfo(clazz);
			return info == null ? null : info.getNrStudentConflicts();
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean eq(String a, String b) {
		return (a == null ? "" : a).equals(b == null ? "" : b);
	}
}
