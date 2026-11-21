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
package org.unitime.timetable.server.assistant;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.shared.AttendanceInterface.AttendanceRequest;
import org.unitime.timetable.gwt.shared.AttendanceInterface.AttendanceResponse;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Excuse;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Followup;
import org.unitime.timetable.gwt.shared.AttendanceInterface.MyAttendanceRequest;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.MyExamsRequest;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.attendance.AttendanceBackend;
import org.unitime.timetable.server.attendance.AttendanceService;
import org.unitime.timetable.server.attendance.MyAttendanceBackend;
import org.unitime.timetable.server.seating.MyExamsBackend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * What the assistant can do. Every tool runs as the signed-in user: it is offered only when the user has
 * the rights behind it, and the backends it calls check those rights again. Tools marked write change data
 * and run only after the user confirms them in the chat. Results go back to Claude as JSON (capped in size)
 * and are marked as data in the system prompt.
 */
public class AssistantTools {
	static final int MAX_RESULT = 24000, MAX_ROWS = 40;
	static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd HH:mm").create();
	static final Pattern PAGE = Pattern.compile("^/[a-z0-9][a-z0-9-]*(/[A-Za-z0-9_-]+)*(\\?[A-Za-z0-9_=&%.-]*)?$");

	public interface Exec { Outcome run(Map<String, Object> in, SessionContext context) throws Exception; }

	public static class Outcome {
		public Object data;
		public String detail, linkPath, linkLabel;
		public Outcome(Object data, String detail) { this.data = data; this.detail = detail; }
	}

	public static class Tool {
		public final String name, description;
		public final Map<String, Object> properties = new LinkedHashMap<String, Object>();
		public final List<String> required = new ArrayList<String>();
		/** changes data: runs only after the user confirms */
		public boolean write;
		Predicate<SessionContext> allowed = c -> true;
		Exec exec;
		/** a one-line description of a call (shown as the tool line, or as the action to confirm) */
		Function<Map<String, Object>, String> describe;

		Tool(String name, String description) { this.name = name; this.description = description; }

		Tool param(String name, String type, String description, boolean required, String... enumValues) {
			Map<String, Object> p = new LinkedHashMap<String, Object>();
			p.put("type", type);
			p.put("description", description);
			if (enumValues.length > 0) p.put("enum", Arrays.asList(enumValues));
			properties.put(name, p);
			if (required) this.required.add(name);
			return this;
		}

		/** checks the input against the schema (required, types, enums) - the CLI backend does not validate it */
		String validate(Map<String, Object> in) {
			if (in == null) return "Missing input.";
			for (String r: required) if (in.get(r) == null || (in.get(r) instanceof String && ((String)in.get(r)).trim().isEmpty())) return "Missing required parameter: " + r;
			for (Map.Entry<String, Object> e: in.entrySet()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> p = (Map<String, Object>)properties.get(e.getKey());
				if (p == null) return "Unknown parameter: " + e.getKey();
				Object v = e.getValue();
				if (v == null) continue;
				String type = (String)p.get("type");
				if ("string".equals(type) && !(v instanceof String)) return "Parameter " + e.getKey() + " must be a string.";
				if ("integer".equals(type) && !(v instanceof Number)) return "Parameter " + e.getKey() + " must be a number.";
				if (p.containsKey("enum") && !((List<?>)p.get("enum")).contains(v)) return "Parameter " + e.getKey() + " must be one of " + p.get("enum") + ".";
			}
			return null;
		}

		String describe(Map<String, Object> in) {
			try { return describe == null ? name : describe.apply(in); } catch (Exception e) { return name; }
		}
	}

	// ------------------------------------------------------------------ input helpers

	static String str(Map<String, Object> in, String key) {
		Object v = in.get(key);
		return v == null ? null : v.toString().trim().isEmpty() ? null : v.toString().trim();
	}

	static String str(Map<String, Object> in, String key, String def) { String v = str(in, key); return v == null ? def : v; }

	static Long lng(Map<String, Object> in, String key) {
		Object v = in.get(key);
		if (v instanceof Number) return ((Number)v).longValue();
		try { return v == null ? null : Long.valueOf(v.toString().trim()); } catch (NumberFormatException e) { return null; }
	}

	static int limit(Map<String, Object> in, int def) {
		Long l = lng(in, "limit");
		return l == null ? def : (int)Math.max(1, Math.min(MAX_ROWS, l));
	}

	static <T> List<T> head(List<T> list, int max) { return list == null ? new ArrayList<T>() : list.size() <= max ? list : new ArrayList<T>(list.subList(0, max)); }

	/** wraps a list with its total size when it was cut */
	static Map<String, Object> rows(String key, List<?> all, int max) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("total", all == null ? 0 : all.size());
		m.put(key, head(all, max));
		if (all != null && all.size() > max) m.put("note", "Only the first " + max + " of " + all.size() + " are listed.");
		return m;
	}

	static Long sessionId(SessionContext c) { return c.getUser().getCurrentAcademicSessionId(); }

	static AttendanceResponse attendance(SessionContext c, AttendanceRequest r) { return new AttendanceBackend().execute(r, c); }

	static String time(Assignment a) {
		try { return a.getPlacement().getLongName(true); } catch (Exception e) { return ""; }
	}

	static String rooms(Assignment a) {
		TreeSet<String> ret = new TreeSet<String>();
		if (a.getRooms() != null) for (Location l: a.getRooms()) ret.add(l.getLabel());
		return String.join(", ", ret);
	}

	static Map<String, Object> classRow(Class_ c) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("class", c.getClassLabel());
		try { m.put("title", c.getSchedulingSubpart().getControllingCourseOffering().getTitle()); } catch (Exception e) {}
		Assignment a = c.getCommittedAssignment();
		m.put("time", a == null ? "not assigned" : time(a));
		m.put("rooms", a == null ? "" : rooms(a));
		m.put("instructors", AttendanceService.instructorNames(c));
		m.put("enrolled", c.getEnrollment() == null ? 0 : c.getEnrollment());
		m.put("limit", c.getExpectedCapacity());
		return m;
	}

	// ------------------------------------------------------------------ the tools

	public static List<Tool> all() {
		List<Tool> tools = new ArrayList<Tool>();
		Tool t;

		// ---- timetable look-ups
		t = new Tool("search_classes", "Find classes of the current academic session by course (e.g. \"BIOL 101\", \"CALC\") or course title, with the committed timetable: time, rooms, instructors and enrolment.")
				.param("query", "string", "Course code, subject or words of the title.", true)
				.param("limit", "integer", "Maximum number of classes (default 20, at most 40).", false);
		t.allowed = c -> c.hasPermission(Right.ClassAssignments) || c.hasPermission(Right.Classes);
		t.describe = in -> "Searched classes for \"" + str(in, "query") + "\"";
		t.exec = (in, c) -> {
			String q = "%" + str(in, "query").toLowerCase(Locale.ROOT) + "%";
			List<Class_> classes = Class_DAO.getInstance().getSession().createQuery(
					"select distinct c from Class_ c, CourseOffering co where co.instructionalOffering = c.schedulingSubpart.instrOfferingConfig.instructionalOffering " +
					"and co.instructionalOffering.session.uniqueId = :s and (lower(concat(co.subjectArea.subjectAreaAbbreviation, ' ', co.courseNbr)) like :q " +
					"or lower(co.title) like :q or lower(co.subjectArea.subjectAreaAbbreviation) like :q)", Class_.class)
					.setParameter("s", sessionId(c)).setParameter("q", q).setMaxResults(200).list();
			List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
			for (Class_ x: classes) ret.add(classRow(x));
			ret.sort((a, b) -> ((String)a.get("class")).compareTo((String)b.get("class")));
			return new Outcome(rows("classes", ret, limit(in, 20)), "Searched classes for \"" + str(in, "query") + "\" (" + ret.size() + " found)");
		};
		tools.add(t);

		t = new Tool("instructor_timetable", "The weekly timetable (committed) of an instructor, by name or part of the name.")
				.param("name", "string", "Instructor name, e.g. \"Doe\" or \"Joe Doe\".", true);
		t.allowed = c -> c.hasPermission(Right.ClassAssignments) || c.hasPermission(Right.Classes);
		t.describe = in -> "Looked up the timetable of instructor \"" + str(in, "name") + "\"";
		t.exec = (in, c) -> {
			String name = str(in, "name").toLowerCase(Locale.ROOT);
			List<ClassInstructor> cis = Class_DAO.getInstance().getSession().createQuery(
					"from ClassInstructor ci where ci.instructor.department.session.uniqueId = :s and " +
					"(lower(concat(coalesce(ci.instructor.firstName, ''), ' ', ci.instructor.lastName)) like :n or lower(ci.instructor.lastName) like :n)", ClassInstructor.class)
					.setParameter("s", sessionId(c)).setParameter("n", "%" + name + "%").list();
			Map<String, List<Map<String, Object>>> byInstructor = new LinkedHashMap<String, List<Map<String, Object>>>();
			for (ClassInstructor ci: cis) {
				DepartmentalInstructor di = ci.getInstructor();
				byInstructor.computeIfAbsent(di.getNameLastFirst(), k -> new ArrayList<Map<String, Object>>()).add(classRow(ci.getClassInstructing()));
			}
			return new Outcome(byInstructor.isEmpty() ? Map.of("message", "No instructor matches \"" + str(in, "name") + "\".") : byInstructor,
					"Looked up the timetable of instructor \"" + str(in, "name") + "\"");
		};
		tools.add(t);

		t = new Tool("room_timetable", "The weekly timetable (committed classes) of a room, by room name, e.g. \"EDUC 101\" or \"HALL A\".")
				.param("room", "string", "Room name or part of it.", true);
		t.allowed = c -> c.hasPermission(Right.ClassAssignments) || c.hasPermission(Right.Rooms);
		t.describe = in -> "Looked up the timetable of room \"" + str(in, "room") + "\"";
		t.exec = (in, c) -> {
			String q = str(in, "room").toLowerCase(Locale.ROOT);
			org.hibernate.Session hib = Class_DAO.getInstance().getSession();
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			for (Location l: hib.createQuery("from Location where session.uniqueId = :s", Location.class).setParameter("s", sessionId(c)).list()) {
				if (!l.getLabel().toLowerCase(Locale.ROOT).contains(q)) continue;
				List<Assignment> as = hib.createQuery("select a from Assignment a join a.rooms r where r.uniqueId = :r and a.solution.commited = true", Assignment.class)
						.setParameter("r", l.getUniqueId()).list();
				List<Map<String, Object>> classes = new ArrayList<Map<String, Object>>();
				for (Assignment a: as) {
					Map<String, Object> m = new LinkedHashMap<String, Object>();
					m.put("class", a.getClazz().getClassLabel()); m.put("time", time(a)); m.put("instructors", AttendanceService.instructorNames(a.getClazz()));
					classes.add(m);
				}
				Map<String, Object> room = new LinkedHashMap<String, Object>();
				room.put("capacity", l.getCapacity()); room.put("classes", classes);
				ret.put(l.getLabel(), room);
				if (ret.size() >= 5) break;
			}
			return new Outcome(ret.isEmpty() ? Map.of("message", "No room matches \"" + str(in, "room") + "\".") : ret, "Looked up the timetable of room \"" + str(in, "room") + "\"");
		};
		tools.add(t);

		t = new Tool("exam_schedule", "Examinations of the current session whose name matches (course code or exam name): type, assigned period, rooms and number of students.")
				.param("query", "string", "Course code or part of the examination name, e.g. \"CALC 101\".", true);
		t.allowed = c -> c.hasPermission(Right.ExaminationSchedule) || c.hasPermission(Right.AssignedExaminations) || c.hasPermission(Right.Examinations);
		t.describe = in -> "Looked up examinations for \"" + str(in, "query") + "\"";
		t.exec = (in, c) -> {
			String q = str(in, "query").toLowerCase(Locale.ROOT);
			List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
			for (Exam e: Class_DAO.getInstance().getSession().createQuery("from Exam where session.uniqueId = :s", Exam.class).setParameter("s", sessionId(c)).list()) {
				String label = e.getLabel();
				if (label == null || !label.toLowerCase(Locale.ROOT).contains(q)) continue;
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("exam", label); m.put("type", e.getExamType().getLabel());
				m.put("period", e.getAssignedPeriod() == null ? "not assigned" : e.getAssignedPeriod().getName());
				TreeSet<String> rooms = new TreeSet<String>();
				if (e.getAssignedRooms() != null) for (Object l: e.getAssignedRooms()) rooms.add(((Location)l).getLabel());
				m.put("rooms", String.join(", ", rooms));
				try { m.put("students", e.countStudents()); } catch (Exception x) {}
				ret.add(m);
			}
			return new Outcome(rows("exams", ret, MAX_ROWS), "Looked up examinations for \"" + str(in, "query") + "\" (" + ret.size() + " found)");
		};
		tools.add(t);

		t = new Tool("staff_absences", "Staff absences on a date and the relief cover for the classes they miss (covered by whom, or not covered yet).")
				.param("date", "string", "Date as YYYY-MM-DD.", true);
		t.allowed = c -> c.hasPermission(Right.StaffAbsences) || c.hasPermission(Right.ReliefPlanning);
		t.describe = in -> "Looked up staff absences and relief cover on " + str(in, "date");
		t.exec = (in, c) -> {
			Date day = new SimpleDateFormat("yyyy-MM-dd").parse(str(in, "date"));
			org.hibernate.Session hib = Class_DAO.getInstance().getSession();
			List<Map<String, Object>> absences = new ArrayList<Map<String, Object>>();
			for (StaffAbsence a: hib.createQuery("from StaffAbsence where session.uniqueId = :s and startDate <= :d and endDate >= :d", StaffAbsence.class)
					.setParameter("s", sessionId(c)).setParameter("d", day).list()) {
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("staff", a.getName()); m.put("from", a.getStartDate()); m.put("to", a.getEndDate());
				m.put("reason", a.getReason() == null ? "" : a.getReason().getLabel());
				absences.add(m);
			}
			List<Map<String, Object>> cover = new ArrayList<Map<String, Object>>();
			for (ReliefAssignment r: hib.createQuery("from ReliefAssignment where session.uniqueId = :s and meetingDate = :d order by startPeriod", ReliefAssignment.class)
					.setParameter("s", sessionId(c)).setParameter("d", day).list()) {
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("class", r.getClazz() == null ? "" : r.getClazz().getClassLabel());
				m.put("time", AttendanceService.time(r.getStartPeriod(), r.getStopPeriod()));
				m.put("absent", r.getAbsence() == null ? "" : r.getAbsence().getName());
				m.put("coveredBy", r.getReliefName() == null ? (r.getReliefUid() == null ? "NOT COVERED" : r.getReliefUid()) : r.getReliefName());
				cover.add(m);
			}
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			ret.put("absences", absences); ret.put("reliefCover", head(cover, 60));
			return new Outcome(ret, "Looked up staff absences and relief cover on " + str(in, "date") + " (" + absences.size() + " absent)");
		};
		tools.add(t);

		// ---- attendance
		t = new Tool("attendance_sessions", "The class sessions of a day with their attendance status (not taken / in progress / finalised) and counts. Lecturers see their own and covered classes; managers can ask for all.")
				.param("date", "string", "Date as YYYY-MM-DD.", true)
				.param("scope", "string", "MINE (default) or ALL (attendance managers only).", false, "MINE", "ALL");
		t.allowed = c -> c.hasPermission(Right.AttendanceTake) || c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Looked up attendance sessions on " + str(in, "date");
		t.exec = (in, c) -> {
			AttendanceRequest r = new AttendanceRequest(); r.operation = "SESSIONS"; r.date = str(in, "date"); r.scope = str(in, "scope", "MINE");
			AttendanceResponse x = attendance(c, r);
			return new Outcome(rows("sessions", x.sessions, MAX_ROWS), "Looked up attendance sessions on " + r.date + " (" + (x.sessions == null ? 0 : x.sessions.size()) + ")");
		};
		tools.add(t);

		t = new Tool("attendance_dashboard", "The attendance dashboard of a day: attendance rate on the day, week and term to date, student-day statuses, sessions taken, attendance by a grouping, operational alerts and the lowest classes.")
				.param("date", "string", "Date as YYYY-MM-DD (default: the last day with finalised attendance).", false)
				.param("group_by", "string", "Grouping for the by-group figures (default SUBJECT).", false, "SCHOOL", "SUBJECT", "COURSE", "CLASS", "INSTRUCTOR", "ITYPE", "WEEKDAY", "TIME", "ROOM")
				.param("period", "string", "Period of the by-group figures: DAY, WEEK or TERM (default TERM).", false, "DAY", "WEEK", "TERM");
		t.allowed = c -> c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Opened the attendance dashboard" + (str(in, "date") == null ? "" : " for " + str(in, "date"));
		t.exec = (in, c) -> {
			AttendanceRequest r = new AttendanceRequest(); r.operation = "DASHBOARD"; r.date = str(in, "date"); r.dimension = str(in, "group_by", "SUBJECT"); r.period = str(in, "period", "TERM");
			AttendanceResponse x = attendance(c, r);
			if (x.dashboard != null) { x.dashboard.byGroup = head(x.dashboard.byGroup, 25); x.dashboard.trend = head(x.dashboard.trend, 0); }
			Outcome o = new Outcome(x.dashboard, "Opened the attendance dashboard for " + (x.dashboard == null ? "" : x.dashboard.date));
			return o;
		};
		tools.add(t);

		t = new Tool("attendance_report", "Run a named attendance report. DAILY takes date; NOTIFICATIONS takes nothing; the others take from_date / to_date (default: the whole term); CUSTOM also takes dimension. Reports: DAILY (daily student attendance), PERIOD (by start time), CLASS (course and class), SUBJECT, COMPLIANCE (staff submission compliance), LATENESS, CONSECUTIVE (consecutive absences), MONTHLY (monthly and term), BELOW (students below the threshold), NOTIFICATIONS (delivery), CUSTOM.")
				.param("report", "string", "Report id.", true, "DAILY", "PERIOD", "CLASS", "SUBJECT", "COMPLIANCE", "LATENESS", "CONSECUTIVE", "MONTHLY", "BELOW", "NOTIFICATIONS", "CUSTOM")
				.param("date", "string", "DAILY: the day, YYYY-MM-DD.", false)
				.param("from_date", "string", "Start date YYYY-MM-DD.", false)
				.param("to_date", "string", "End date YYYY-MM-DD.", false)
				.param("dimension", "string", "CUSTOM: CLASS, COURSE, SUBJECT, SCHOOL, STUDENT, INSTRUCTOR, ITYPE, ROOM, DATE, WEEK, WEEKDAY, MONTH or TIME.", false);
		t.allowed = c -> c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Ran the attendance report " + str(in, "report");
		t.exec = (in, c) -> {
			AttendanceRequest r = new AttendanceRequest(); r.operation = "RUN_REPORT"; r.report = str(in, "report");
			r.date = str(in, "date"); r.fromDate = str(in, "from_date"); r.toDate = str(in, "to_date"); r.dimension = str(in, "dimension");
			AttendanceResponse x = attendance(c, r);
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			if (x.table != null) {
				m.put("title", x.table.title); m.put("summary", x.table.summary);
				List<String> cols = new ArrayList<String>();
				for (org.unitime.timetable.gwt.shared.AttendanceInterface.ReportColumn col: x.table.columns) cols.add(col.name);
				m.put("columns", cols); m.put("totalRows", x.table.rows.size()); m.put("rows", head(x.table.rows, MAX_ROWS));
				if (x.table.rows.size() > MAX_ROWS) m.put("note", "Only the first " + MAX_ROWS + " rows are listed; the full report is on the Attendance page (Reports).");
				if (x.table.note != null) m.put("reportNote", x.table.note);
			}
			return new Outcome(m, "Ran the report \"" + (x.table == null ? r.report : x.table.title) + "\"");
		};
		tools.add(t);

		t = new Tool("attendance_followups", "Follow-up cases raised by the attendance rules (below minimum, eligibility at risk, consecutive absences): student, class, rule, rate, status, notes. Lecturers see their own classes' cases.")
				.param("status", "string", "OPEN (default: open and in progress), IN_PROGRESS, CLOSED or ALL.", false, "OPEN", "IN_PROGRESS", "CLOSED", "ALL");
		t.allowed = c -> c.hasPermission(Right.AttendanceTake) || c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Listed follow-up cases (" + str(in, "status", "OPEN").toLowerCase() + ")";
		t.exec = (in, c) -> {
			AttendanceRequest r = new AttendanceRequest(); r.operation = "FOLLOWUPS";
			String status = str(in, "status", "OPEN");
			List<Followup> list = new ArrayList<Followup>();
			for (Followup f: attendance(c, r).followups)
				if ("ALL".equals(status) || ("OPEN".equals(status) ? !"CLOSED".equals(f.status) : status.equals(f.status))) list.add(f);
			return new Outcome(rows("cases", list, MAX_ROWS), "Listed follow-up cases (" + list.size() + ")");
		};
		tools.add(t);

		t = new Tool("attendance_excuses", "Medical certificates, leave and official-activity excuses submitted by students: id, student, type, period, note, status, whether the user can decide it.")
				.param("status", "string", "PENDING (default), APPROVED, REJECTED or ALL.", false, "PENDING", "APPROVED", "REJECTED", "ALL");
		t.allowed = c -> c.hasPermission(Right.AttendanceTake) || c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Listed certificates and leave (" + str(in, "status", "PENDING").toLowerCase() + ")";
		t.exec = (in, c) -> {
			AttendanceRequest r = new AttendanceRequest(); r.operation = "EXCUSES";
			String status = str(in, "status", "PENDING");
			List<Excuse> list = new ArrayList<Excuse>();
			for (Excuse e: attendance(c, r).excuses) if ("ALL".equals(status) || status.equals(e.status)) { e.fileBase64 = null; list.add(e); }
			return new Outcome(rows("excuses", list, MAX_ROWS), "Listed certificates and leave (" + list.size() + ")");
		};
		tools.add(t);

		t = new Tool("student_attendance", "Attendance of one student (by student id such as S900011, or name) in each class: sessions, present, late, absent, excused and rate.")
				.param("student", "string", "Student id or name.", true);
		t.allowed = c -> c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Looked up the attendance of " + str(in, "student");
		t.exec = (in, c) -> {
			String q = str(in, "student");
			org.hibernate.Session hib = Class_DAO.getInstance().getSession();
			List<Student> students = hib.createQuery("from Student where session.uniqueId = :s and (lower(externalUniqueId) = :id or lower(concat(coalesce(firstName, ''), ' ', lastName)) like :n or lower(lastName) like :n)", Student.class)
					.setParameter("s", sessionId(c)).setParameter("id", q.toLowerCase(Locale.ROOT)).setParameter("n", "%" + q.toLowerCase(Locale.ROOT) + "%").setMaxResults(6).list();
			if (students.isEmpty()) return new Outcome(Map.of("message", "No student matches \"" + q + "\"."), "Looked up the attendance of " + q);
			if (students.size() > 1) {
				List<String> names = new ArrayList<String>();
				for (Student s: students) names.add(AttendanceService.name(s) + " (" + s.getExternalUniqueId() + ")");
				return new Outcome(Map.of("message", "Several students match; ask which one.", "matches", names), "Looked up the attendance of " + q);
			}
			Student st = students.get(0);
			org.unitime.timetable.gwt.shared.AttendanceInterface.Config cfg = AttendanceService.config(hib, sessionId(c));
			Map<String, int[]> counts = AttendanceService.counts(hib, sessionId(c), Arrays.asList(st.getUniqueId()));
			List<Map<String, Object>> classes = new ArrayList<Map<String, Object>>();
			for (Map.Entry<String, int[]> e: counts.entrySet()) {
				Class_ cl = Class_DAO.getInstance().get(Long.valueOf(e.getKey().split(":")[1]));
				int[] x = e.getValue();
				Map<String, Object> m = new LinkedHashMap<String, Object>();
				m.put("class", cl == null ? "?" : cl.getClassLabel());
				m.put("sessions", x[0] + x[1] + x[2] + x[3]); m.put("present", x[0]); m.put("late", x[1]); m.put("absent", x[2]); m.put("excused", x[3]);
				m.put("ratePct", Math.round(10.0 * AttendanceService.rate(cfg, x)) / 10.0);
				classes.add(m);
			}
			Map<String, Object> ret = new LinkedHashMap<String, Object>();
			ret.put("student", AttendanceService.name(st) + " (" + st.getExternalUniqueId() + ")");
			ret.put("minimumPct", cfg.minPct); ret.put("eligibilityPct", cfg.eligibilityPct); ret.put("classes", classes);
			return new Outcome(ret, "Looked up the attendance of " + AttendanceService.name(st));
		};
		tools.add(t);

		t = new Tool("my_attendance", "The signed-in student's own attendance: rate per class, warnings, certificates and leave.");
		t.allowed = c -> c.hasPermission(Right.MyAttendance) && !c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Looked up your attendance";
		t.exec = (in, c) -> {
			org.unitime.timetable.gwt.shared.AttendanceInterface.MyAttendanceResponse x = new MyAttendanceBackend().execute(new MyAttendanceRequest(), c);
			x.records = head(x.records, 30);
			return new Outcome(x, "Looked up your attendance");
		};
		tools.add(t);

		t = new Tool("my_exams", "The signed-in user's published examination seats (students) or invigilation duties (invigilators).");
		t.allowed = c -> c.hasPermission(Right.MyExamSeats);
		t.describe = in -> "Looked up your examinations";
		t.exec = (in, c) -> new Outcome(new MyExamsBackend().execute(new MyExamsRequest(), c), "Looked up your examinations");
		tools.add(t);

		// ---- actions (confirmed by the user before they run)
		t = new Tool("decide_excuse", "Approve or reject a student's medical certificate / leave (needs the excuse id from attendance_excuses). Approving turns the student's absences in the period into excused. Rejecting needs a reason. The user is asked to confirm before it runs.")
				.param("id", "integer", "Excuse id.", true)
				.param("decision", "string", "APPROVE or REJECT.", true, "APPROVE", "REJECT")
				.param("note", "string", "Reason (required to reject) or note.", false);
		t.write = true;
		t.allowed = c -> c.hasPermission(Right.AttendanceTake) || c.hasPermission(Right.AttendanceManage);
		t.describe = in -> ("APPROVE".equals(str(in, "decision")) ? "Approve" : "Reject") + " excuse " + lng(in, "id") + (str(in, "note") == null ? "" : " - note: \"" + str(in, "note") + "\"");
		t.exec = (in, c) -> {
			AttendanceRequest r = new AttendanceRequest(); r.operation = "DECIDE_EXCUSE"; r.id = lng(in, "id"); r.decision = str(in, "decision"); r.note = str(in, "note", "");
			AttendanceResponse x = attendance(c, r);
			Excuse e = null;
			for (Excuse y: x.excuses) if (y.id.equals(r.id)) { e = y; e.fileBase64 = null; }
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("result", x.message == null ? "Done." : x.message); m.put("excuse", e);
			return new Outcome(m, ("APPROVE".equals(r.decision) ? "Approved" : "Rejected") + " excuse " + r.id + (e == null ? "" : " (" + e.studentName + ")"));
		};
		tools.add(t);

		t = new Tool("update_followup", "Update a follow-up case (needs the case id from attendance_followups): status, notes on the contact made, outcome / action decided, next review date. Fields left out keep their value. The user is asked to confirm before it runs.")
				.param("id", "integer", "Case id.", true)
				.param("status", "string", "OPEN, IN_PROGRESS or CLOSED.", false, "OPEN", "IN_PROGRESS", "CLOSED")
				.param("notes", "string", "Notes (replaces the current notes).", false)
				.param("outcome", "string", "Outcome or action decided.", false)
				.param("next_review", "string", "Next review date YYYY-MM-DD.", false);
		t.write = true;
		t.allowed = c -> c.hasPermission(Right.AttendanceTake) || c.hasPermission(Right.AttendanceManage);
		t.describe = in -> "Update follow-up case " + lng(in, "id") + ":" + (str(in, "status") == null ? "" : " status " + str(in, "status")) + (str(in, "outcome") == null ? "" : ", outcome \"" + str(in, "outcome") + "\"")
				+ (str(in, "notes") == null ? "" : ", notes \"" + str(in, "notes") + "\"") + (str(in, "next_review") == null ? "" : ", next review " + str(in, "next_review"));
		t.exec = (in, c) -> {
			Long id = lng(in, "id");
			AttendanceRequest list = new AttendanceRequest(); list.operation = "FOLLOWUPS";
			Followup current = null;
			for (Followup f: attendance(c, list).followups) if (f.id.equals(id)) current = f;
			if (current == null) throw new GwtRpcException("Unknown follow-up case " + id + " (or not one of your classes).");
			AttendanceRequest r = new AttendanceRequest(); r.operation = "UPDATE_FOLLOWUP"; r.id = id;
			r.status = str(in, "status"); r.note = str(in, "notes"); r.outcome = str(in, "outcome");
			r.nextReview = str(in, "next_review") != null ? str(in, "next_review") : current.nextReview == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(current.nextReview);
			AttendanceResponse x = attendance(c, r);
			Followup updated = null;
			for (Followup f: x.followups) if (f.id.equals(id)) updated = f;
			return new Outcome(Map.of("result", "Updated.", "case", updated == null ? "?" : updated), "Updated follow-up case " + id + " (" + current.studentName + ")");
		};
		tools.add(t);

		// ---- navigation
		t = new Tool("open_page", "Offer the user a button that opens a page of this application, e.g. /attendance, /my-attendance, /exam-seating, /invigilation, /my-exams, /change-requests, /solver, /relief-planning. Use it when the user wants to see or do something on a page.")
				.param("path", "string", "Application route starting with /.", true)
				.param("label", "string", "Button text, e.g. \"Open the attendance dashboard\".", true);
		t.describe = in -> "Suggested opening " + str(in, "path");
		t.exec = (in, c) -> {
			String path = str(in, "path");
			if (!PAGE.matcher(path).matches()) throw new GwtRpcException("Not an application route: " + path);
			Outcome o = new Outcome(Map.of("result", "A button to open " + path + " is shown to the user."), "Suggested a page");
			o.linkPath = path; o.linkLabel = str(in, "label", path);
			return o;
		};
		tools.add(t);
		return tools;
	}

	/** the tools this user may use */
	public static List<Tool> available(SessionContext context) {
		List<Tool> ret = new ArrayList<Tool>();
		for (Tool t: all()) {
			try { if (t.allowed.test(context)) ret.add(t); } catch (Exception e) {}
		}
		return ret;
	}

	static String toJson(Object data) {
		String json = GSON.toJson(data);
		if (json.length() > MAX_RESULT) json = json.substring(0, MAX_RESULT) + "... [cut: result too long]";
		return json;
	}
}
