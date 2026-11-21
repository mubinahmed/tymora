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
package org.unitime.timetable.server.attendance;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.unitime.timetable.gwt.shared.AttendanceInterface.Alert;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Config;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Dashboard;
import org.unitime.timetable.gwt.shared.AttendanceInterface.ReportColumn;
import org.unitime.timetable.gwt.shared.AttendanceInterface.ReportInfo;
import org.unitime.timetable.gwt.shared.AttendanceInterface.ReportRow;
import org.unitime.timetable.gwt.shared.AttendanceInterface.ReportTable;
import org.unitime.timetable.gwt.shared.AttendanceInterface.TrendPoint;
import org.unitime.timetable.model.AttendanceFollowup;
import org.unitime.timetable.model.AttendanceNotification;
import org.unitime.timetable.model.AttendanceRecord;
import org.unitime.timetable.model.AttendanceSession;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.Student;

/**
 * Attendance dashboard and named reports (B1-7.8.3, B1-7.8.4, B1-7.8.6): daily student attendance, period-wise,
 * course and class, subject-wise, staff submission compliance, lateness, consecutive absences, monthly and term
 * percentages, students below the threshold, notification delivery, and the free grouping. Rates use the
 * configured counting rules (AttendanceService.rate) over finalised sessions.
 */
public class AttendanceReports {
	static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
	static final SimpleDateFormat DT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public static List<ReportInfo> catalog() {
		return Arrays.asList(
			new ReportInfo("DAILY", "Daily student attendance", "Every student with sessions on the day: sessions, present, late, absent, excused and the day status.", "DATE"),
			new ReportInfo("PERIOD", "Period-wise attendance", "Attendance by teaching period (start time) over the period chosen.", "RANGE"),
			new ReportInfo("CLASS", "Course and class attendance", "Each class (group) with its course / LU, enrolment, sessions held and attendance rate.", "RANGE"),
			new ReportInfo("SUBJECT", "Subject-wise attendance", "Attendance by subject area.", "RANGE"),
			new ReportInfo("COMPLIANCE", "Staff submission compliance", "For each lecturer (or relief teacher): sessions scheduled, finalised on the day, finalised late, still open, not taken, and the average delay.", "RANGE"),
			new ReportInfo("LATENESS", "Student lateness", "Students who were late, most frequent first, with their share of attended sessions.", "RANGE"),
			new ReportInfo("CONSECUTIVE", "Consecutive absences", "Students with runs of unexcused absences in a class at or above the configured number: current and longest run, last attended.", "RANGE"),
			new ReportInfo("MONTHLY", "Monthly and term attendance", "Attendance percentage per month and for the term to date.", "RANGE"),
			new ReportInfo("BELOW", "Students below the threshold", "Students whose attendance in a class is below the minimum (eligibility risk first), with their follow-up case.", "RANGE"),
			new ReportInfo("NOTIFICATIONS", "Notification delivery", "Every follow-up notification and its delivery status, per recipient.", "NONE"),
			new ReportInfo("CUSTOM", "Attendance by ...", "Free grouping: school, course / LU, class, subject, student, staff, activity type, room, date, week, weekday, month or start time.", "RANGE,DIMENSION"));
	}

	// ------------------------------------------------------------------ helpers

	static String pct(double v) { return String.valueOf(Math.round(10.0 * v) / 10.0); }
	static String n(int v) { return String.valueOf(v); }

	static double rate(Config cfg, int p, int l, int a, int e) { return Math.round(10.0 * AttendanceService.rate(cfg, new int[] {p, l, a, e})) / 10.0; }

	static ReportTable table(String id, String... columns) {
		ReportTable t = new ReportTable();
		t.id = id;
		for (ReportInfo i: catalog()) if (i.id.equals(id)) { t.title = i.title; t.description = i.description; }
		for (String c: columns) { String[] x = c.split(":"); t.columns.add(new ReportColumn(x[0], x.length > 1 ? x[1] : "text")); }
		return t;
	}

	static void summary(ReportTable t, String label, String value) { t.summary.add(Arrays.asList(label, value)); }

	/** Timetabled class meetings in the date range for classes with at least one enrolled student: {meetingId, classId, meetingDate, startPeriod, stopPeriod}. */
	static List<Object[]> scheduled(org.hibernate.Session hib, Long sessionId, Date from, Date to) {
		return hib.createQuery("select m.uniqueId, e.clazz.uniqueId, m.meetingDate, m.startPeriod, m.stopPeriod from Meeting m, ClassEvent e where m.event = e and " +
				"m.meetingDate >= :f and m.meetingDate <= :t and e.clazz.schedulingSubpart.instrOfferingConfig.instructionalOffering.session.uniqueId = :s and " +
				"exists (select 1 from StudentClassEnrollment x where x.clazz = e.clazz) order by m.meetingDate, m.startPeriod", Object[].class)
				.setParameter("f", from).setParameter("t", to).setParameter("s", sessionId).list();
	}

	static Map<Long, AttendanceSession> sessionsByMeeting(org.hibernate.Session hib, Long sessionId, Date from, Date to) {
		Map<Long, AttendanceSession> ret = new HashMap<Long, AttendanceSession>();
		for (AttendanceSession s: hib.createQuery("from AttendanceSession where sessionId = :s and meetingDate >= :f and meetingDate <= :t", AttendanceSession.class)
				.setParameter("s", sessionId).setParameter("f", from).setParameter("t", to).list())
			ret.put(s.getMeetingId(), s);
		return ret;
	}

	static Map<Long, Set<Long>> enrolments(org.hibernate.Session hib, Set<Long> classIds) {
		Map<Long, Set<Long>> ret = new HashMap<Long, Set<Long>>();
		if (classIds.isEmpty()) return ret;
		List<Long> ids = new ArrayList<Long>(classIds);
		for (int i = 0; i < ids.size(); i += 500)
			for (Object[] o: hib.createQuery("select x.clazz.uniqueId, x.student.uniqueId from StudentClassEnrollment x where x.clazz.uniqueId in :c", Object[].class)
					.setParameterList("c", ids.subList(i, Math.min(ids.size(), i + 500))).list())
				ret.computeIfAbsent((Long)o[0], k -> new HashSet<Long>()).add((Long)o[1]);
		return ret;
	}

	static Map<Long, Integer> enrolmentCounts(org.hibernate.Session hib, Long sessionId) {
		Map<Long, Integer> ret = new HashMap<Long, Integer>();
		for (Object[] o: hib.createQuery("select x.clazz.uniqueId, count(x) from StudentClassEnrollment x where x.student.session.uniqueId = :s group by x.clazz.uniqueId", Object[].class)
				.setParameter("s", sessionId).list())
			ret.put((Long)o[0], ((Number)o[1]).intValue());
		return ret;
	}

	static Date addDays(Date d, int days) { Calendar c = Calendar.getInstance(); c.setTime(d); c.add(Calendar.DAY_OF_MONTH, days); return c.getTime(); }

	static Date monday(Date d) {
		Calendar c = Calendar.getInstance(); c.setTime(d);
		int dow = c.get(Calendar.DAY_OF_WEEK);
		c.add(Calendar.DAY_OF_MONTH, -((dow + 5) % 7));
		return c.getTime();
	}

	/** End of the meeting (date + stop period). */
	static Date meetingEnd(Date date, Integer stopPeriod) {
		Calendar c = Calendar.getInstance(); c.setTime(date);
		c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.MINUTE, 5 * (stopPeriod == null ? 0 : stopPeriod));
		return c.getTime();
	}

	static boolean sameDay(Date a, Date b) { return AttendanceService.DATE.format(a).equals(AttendanceService.DATE.format(b)); }

	/** Relief teacher per class and date (Relief Planning) in the range: "classId:yyyy-MM-dd" -> name. */
	static Map<String, String> reliefs(org.hibernate.Session hib, Date from, Date to) {
		Map<String, String> ret = new HashMap<String, String>();
		for (Object[] o: hib.createQuery("select r.clazz.uniqueId, r.meetingDate, r.reliefName, r.reliefUid from ReliefAssignment r where r.meetingDate >= :f and r.meetingDate <= :t and r.reliefUid is not null", Object[].class)
				.setParameter("f", from).setParameter("t", to).list())
			ret.put(o[0] + ":" + AttendanceService.DATE.format((Date)o[1]), o[2] == null ? (String)o[3] : (String)o[2]);
		return ret;
	}

	// ------------------------------------------------------------------ dashboard

	public static Dashboard dashboard(org.hibernate.Session hib, Long sessionId, Config cfg, Date termStart, Date day, String period, String groupBy, int pendingExcuses) {
		Dashboard d = new Dashboard();
		d.date = AttendanceService.DATE.format(day);
		d.period = (period == null ? "TERM" : period);
		d.groupBy = (groupBy == null ? "SCHOOL" : groupBy);

		// student-day statuses
		List<Object[]> today = scheduled(hib, sessionId, day, day);
		Map<Long, AttendanceSession> sessions = sessionsByMeeting(hib, sessionId, day, day);
		Set<Long> classIds = new HashSet<Long>();
		for (Object[] m: today) classIds.add((Long)m[1]);
		Map<Long, Set<Long>> enrl = enrolments(hib, classIds);
		Map<Long, int[]> perStudent = new HashMap<Long, int[]>(); // sessions, recorded, P, L, A, E
		for (Object[] m: today) {
			AttendanceSession s = sessions.get((Long)m[0]);
			d.sessionsScheduled++;
			if (s == null) d.sessionsNotTaken++;
			else if ("FINALISED".equals(s.getStatus())) d.sessionsFinalised++;
			else d.sessionsOpen++;
			Map<Long, String> st = new HashMap<Long, String>();
			if (s != null && "FINALISED".equals(s.getStatus()))
				for (AttendanceRecord r: AttendanceService.records(hib, s.getUniqueId())) st.put(r.getStudentId(), r.getStatus());
			for (Long stu: enrl.getOrDefault((Long)m[1], Collections.emptySet())) {
				int[] c = perStudent.computeIfAbsent(stu, k -> new int[6]);
				c[0]++;
				String x = st.get(stu);
				if (x == null) continue;
				c[1]++;
				switch (x) { case "PRESENT": c[2]++; break; case "LATE": c[3]++; break; case "ABSENT": c[4]++; break; default: c[5]++; }
			}
		}
		int p = 0, l = 0, a = 0, e = 0;
		for (int[] c: perStudent.values()) {
			d.expected++;
			p += c[2]; l += c[3]; a += c[4]; e += c[5];
			switch (dayStatus(c)) {
			case "Present": d.present++; break;
			case "Late": d.late++; break;
			case "Partly absent": d.partial++; break;
			case "Absent": d.absent++; break;
			case "Excused": d.excused++; break;
			default: d.notRecorded++;
			}
		}
		if (p + l + a + e > 0) d.dayRatePct = rate(cfg, p, l, a, e);

		// rates for the week and the term to date
		Date mon = monday(day);
		d.weekRatePct = periodRate(hib, sessionId, cfg, mon, day);
		d.termRatePct = periodRate(hib, sessionId, cfg, termStart, day);

		// by group for the chosen period
		Date from = ("DAY".equals(d.period) ? day : "WEEK".equals(d.period) ? mon : termStart);
		d.periodFrom = AttendanceService.DATE.format(from); d.periodTo = d.date;
		d.byGroup = AttendanceBackend.report(hib, sessionId, cfg, d.groupBy, from, day);
		if (!Arrays.asList("WEEK", "WEEKDAY", "TIME", "DATE", "MONTH").contains(d.groupBy))
			Collections.sort(d.byGroup, new Comparator<ReportRow>() { @Override public int compare(ReportRow x, ReportRow y) { return x.label.compareToIgnoreCase(y.label); } });
		List<ReportRow> classes = AttendanceBackend.report(hib, sessionId, cfg, "CLASS", termStart, day);
		d.lowestClasses = new ArrayList<ReportRow>(classes.subList(0, Math.min(5, classes.size())));

		// daily trend over the term to date
		Map<String, int[]> perDay = new TreeMap<String, int[]>();
		for (Object[] o: hib.createQuery("select s.meetingDate, r.status, count(r) from AttendanceSession s, AttendanceRecord r where r.attendanceSessionId = s.uniqueId and " +
				"s.sessionId = :s and s.status = 'FINALISED' and s.meetingDate >= :f and s.meetingDate <= :t group by s.meetingDate, r.status", Object[].class)
				.setParameter("s", sessionId).setParameter("f", termStart).setParameter("t", day).list()) {
			int[] c = perDay.computeIfAbsent(AttendanceService.DATE.format((Date)o[0]), k -> new int[4]);
			int cnt = ((Number)o[2]).intValue();
			switch ((String)o[1]) { case "PRESENT": c[0] += cnt; break; case "LATE": c[1] += cnt; break; case "ABSENT": c[2] += cnt; break; default: c[3] += cnt; }
		}
		for (Map.Entry<String, int[]> x: perDay.entrySet()) {
			TrendPoint t = new TrendPoint(); t.date = x.getKey(); int[] c = x.getValue();
			t.records = c[0] + c[1] + c[2] + c[3]; t.ratePct = rate(cfg, c[0], c[1], c[2], c[3]);
			d.trend.add(t);
		}

		// students below the thresholds (term to date, any class)
		Set<Long> below = new HashSet<Long>(), risk = new HashSet<Long>();
		for (Map.Entry<String, int[]> x: AttendanceService.counts(hib, sessionId, null).entrySet()) {
			int[] c = x.getValue();
			if (c[0] + c[1] + c[2] + c[3] < cfg.minSessions) continue;
			double r = AttendanceService.rate(cfg, c);
			Long stu = Long.valueOf(x.getKey().split(":")[0]);
			if (cfg.eligibilityPct != null && r < cfg.eligibilityPct) risk.add(stu);
			else if (r < cfg.minPct) below.add(stu);
		}
		below.removeAll(risk);
		d.studentsAtRisk = risk.size(); d.studentsBelowMin = below.size();

		// operational alerts
		List<Object[]> past = scheduled(hib, sessionId, termStart, day);
		Map<Long, AttendanceSession> pastSessions = sessionsByMeeting(hib, sessionId, termStart, day);
		int pending = 0;
		for (Object[] m: past) { AttendanceSession s = pastSessions.get((Long)m[0]); if (s == null || !"FINALISED".equals(s.getStatus())) pending++; }
		alert(d, "PENDING_SUBMISSIONS", "Sessions not finalised", "Timetabled sessions up to " + d.date + " whose attendance has not been finalised", pending, pending > 0 ? "warning" : "ok", "report:COMPLIANCE");
		Number followup = hib.createQuery("select count(distinct f.studentId) from AttendanceFollowup f where f.sessionId = :s and f.status <> 'CLOSED'", Number.class).setParameter("s", sessionId).uniqueResult();
		alert(d, "FOLLOWUP", "Students requiring follow-up", "Students with an open follow-up case", followup.intValue(), followup.intValue() > 0 ? "critical" : "ok", "tab:followup");
		alert(d, "EXCUSES", "Certificates and leave awaiting approval", "Medical certificates, leave and official activities submitted by students", pendingExcuses, pendingExcuses > 0 ? "warning" : "ok", "tab:excuses");
		Number amended = hib.createQuery("select count(r) from AttendanceRecord r, AttendanceSession s where r.attendanceSessionId = s.uniqueId and s.sessionId = :s and r.source = 'AMEND'", Number.class).setParameter("s", sessionId).uniqueResult();
		alert(d, "AMENDED", "Corrections after finalising", "Records changed by an attendance manager after the session was finalised (each with a reason in the History)", amended.intValue(), "info", "tab:history");
		Number undelivered = hib.createQuery("select count(n) from AttendanceNotification n where n.sessionId = :s and n.status <> 'SENT'", Number.class).setParameter("s", sessionId).uniqueResult();
		alert(d, "UNDELIVERED", "Notifications not delivered", "Follow-up notifications that failed, were not sent, or had no e-mail address", undelivered.intValue(), undelivered.intValue() > 0 ? "warning" : "ok", "report:NOTIFICATIONS");
		return d;
	}

	static void alert(Dashboard d, String key, String label, String detail, int count, String severity, String target) {
		Alert a = new Alert(); a.key = key; a.label = label; a.detail = detail; a.count = count; a.severity = severity; a.target = target;
		d.alerts.add(a);
	}

	static Double periodRate(org.hibernate.Session hib, Long sessionId, Config cfg, Date from, Date to) {
		int[] c = new int[4];
		for (Object[] o: hib.createQuery("select r.status, count(r) from AttendanceSession s, AttendanceRecord r where r.attendanceSessionId = s.uniqueId and " +
				"s.sessionId = :s and s.status = 'FINALISED' and s.meetingDate >= :f and s.meetingDate <= :t group by r.status", Object[].class)
				.setParameter("s", sessionId).setParameter("f", from).setParameter("t", to).list()) {
			int cnt = ((Number)o[1]).intValue();
			switch ((String)o[0]) { case "PRESENT": c[0] += cnt; break; case "LATE": c[1] += cnt; break; case "ABSENT": c[2] += cnt; break; default: c[3] += cnt; }
		}
		return (c[0] + c[1] + c[2] + c[3] == 0 ? null : rate(cfg, c[0], c[1], c[2], c[3]));
	}

	/** c = {sessions, recorded, P, L, A, E} */
	static String dayStatus(int[] c) {
		if (c[1] == 0) return "Not recorded";
		if (c[5] == c[1]) return "Excused";
		if (c[4] > 0) return (c[2] + c[3] == 0 ? "Absent" : "Partly absent");
		if (c[3] > 0) return "Late";
		return "Present";
	}

	// ------------------------------------------------------------------ named reports

	public static ReportTable run(org.hibernate.Session hib, Long sessionId, Config cfg, String id, Date from, Date to, String dimension) {
		switch (id == null ? "" : id) {
		case "DAILY": return daily(hib, sessionId, cfg, to);
		case "PERIOD": return grouped(hib, sessionId, cfg, "PERIOD", "TIME", "Start time", from, to);
		case "CLASS": return classes(hib, sessionId, cfg, from, to);
		case "SUBJECT": return grouped(hib, sessionId, cfg, "SUBJECT", "SUBJECT", "Subject", from, to);
		case "COMPLIANCE": return compliance(hib, sessionId, from, to);
		case "LATENESS": return lateness(hib, sessionId, from, to);
		case "CONSECUTIVE": return consecutive(hib, sessionId, cfg, from, to);
		case "MONTHLY": return monthly(hib, sessionId, cfg, from, to);
		case "BELOW": return below(hib, sessionId, cfg, from, to);
		case "NOTIFICATIONS": return notifications(hib, sessionId);
		case "CUSTOM": return custom(hib, sessionId, cfg, dimension, from, to);
		default: throw new org.unitime.timetable.gwt.command.client.GwtRpcException("Unknown report " + id + ".");
		}
	}

	static void rateRow(ReportTable t, String label, ReportRow r) {
		t.rows.add(Arrays.asList(label, n(r.sessions), n(r.present), n(r.late), n(r.absent), n(r.excused), pct(r.ratePct)));
	}

	static ReportTable grouped(org.hibernate.Session hib, Long sessionId, Config cfg, String id, String dim, String label, Date from, Date to) {
		ReportTable t = table(id, label, "Sessions:num", "Present:num", "Late:num", "Absent:num", "Excused:num", "Attendance:pct");
		List<ReportRow> rows = AttendanceBackend.report(hib, sessionId, cfg, dim, from, to);
		if ("SUBJECT".equals(dim))
			Collections.sort(rows, new Comparator<ReportRow>() { @Override public int compare(ReportRow x, ReportRow y) { return x.label.compareTo(y.label); } });
		int p = 0, l = 0, a = 0, e = 0;
		for (ReportRow r: rows) { rateRow(t, r.label, r); p += r.present; l += r.late; a += r.absent; e += r.excused; }
		if (!rows.isEmpty()) summary(t, "Overall attendance", pct(rate(cfg, p, l, a, e)) + "%");
		summary(t, label.equals("Start time") ? "Periods" : "Groups", n(rows.size()));
		return t;
	}

	static ReportTable custom(org.hibernate.Session hib, Long sessionId, Config cfg, String dimension, Date from, Date to) {
		ReportTable t = grouped(hib, sessionId, cfg, "CUSTOM", dimension == null ? "CLASS" : dimension, "Group", from, to);
		return t;
	}

	static ReportTable daily(org.hibernate.Session hib, Long sessionId, Config cfg, Date day) {
		ReportTable t = table("DAILY", "Student", "Id", "Sessions:num", "Present:num", "Late:num", "Absent:num", "Excused:num", "Not recorded:num", "Day status:status");
		t.description = t.description + " Day: " + DAYS[dow(day) - 1] + " " + AttendanceService.DATE.format(day) + ".";
		List<Object[]> today = scheduled(hib, sessionId, day, day);
		Map<Long, AttendanceSession> sessions = sessionsByMeeting(hib, sessionId, day, day);
		Set<Long> classIds = new HashSet<Long>();
		for (Object[] m: today) classIds.add((Long)m[1]);
		Map<Long, Set<Long>> enrl = enrolments(hib, classIds);
		Map<Long, int[]> per = new HashMap<Long, int[]>();
		for (Object[] m: today) {
			AttendanceSession s = sessions.get((Long)m[0]);
			Map<Long, String> st = new HashMap<Long, String>();
			if (s != null && "FINALISED".equals(s.getStatus()))
				for (AttendanceRecord r: AttendanceService.records(hib, s.getUniqueId())) st.put(r.getStudentId(), r.getStatus());
			for (Long stu: enrl.getOrDefault((Long)m[1], Collections.emptySet())) {
				int[] c = per.computeIfAbsent(stu, k -> new int[6]);
				c[0]++;
				String x = st.get(stu);
				if (x == null) continue;
				c[1]++;
				switch (x) { case "PRESENT": c[2]++; break; case "LATE": c[3]++; break; case "ABSENT": c[4]++; break; default: c[5]++; }
			}
		}
		List<Object[]> rows = new ArrayList<Object[]>();
		Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
		for (String s: new String[] {"Present", "Late", "Partly absent", "Absent", "Excused", "Not recorded"}) byStatus.put(s, 0);
		for (Map.Entry<Long, int[]> x: per.entrySet()) {
			Student st = hib.get(Student.class, x.getKey());
			int[] c = x.getValue();
			String status = dayStatus(c);
			byStatus.put(status, byStatus.get(status) + 1);
			rows.add(new Object[] {st == null ? "?" : AttendanceService.name(st), st == null ? "" : st.getExternalUniqueId(), c, status});
		}
		final List<String> order = Arrays.asList("Absent", "Partly absent", "Late", "Not recorded", "Excused", "Present");
		Collections.sort(rows, new Comparator<Object[]>() {
			@Override public int compare(Object[] x, Object[] y) {
				int cmp = Integer.compare(order.indexOf(x[3]), order.indexOf(y[3]));
				return cmp != 0 ? cmp : ((String)x[0]).compareToIgnoreCase((String)y[0]);
			}
		});
		for (Object[] r: rows) {
			int[] c = (int[])r[2];
			t.rows.add(Arrays.asList((String)r[0], (String)r[1], n(c[0]), n(c[2]), n(c[3]), n(c[4]), n(c[5]), n(c[0] - c[1]), (String)r[3]));
		}
		summary(t, "Students expected", n(per.size()));
		for (Map.Entry<String, Integer> x: byStatus.entrySet()) summary(t, x.getKey(), n(x.getValue()));
		t.note = "Day status: absent = absent without excuse from every recorded session; partly absent = from some; late = attended all, late in at least one; not recorded = none of the student's sessions finalised.";
		return t;
	}

	static int dow(Date d) { Calendar c = Calendar.getInstance(); c.setTime(d); return c.get(Calendar.DAY_OF_WEEK); }

	static ReportTable classes(org.hibernate.Session hib, Long sessionId, Config cfg, Date from, Date to) {
		ReportTable t = table("CLASS", "Course / LU", "Class", "Lecturer", "Enrolled:num", "Sessions:num", "Present:num", "Late:num", "Absent:num", "Excused:num", "Attendance:pct");
		Map<Long, Integer> enrolled = enrolmentCounts(hib, sessionId);
		Map<Long, int[]> per = new HashMap<Long, int[]>(); // P L A E
		Map<Long, Set<Long>> sessions = new HashMap<Long, Set<Long>>();
		for (Object[] o: AttendanceBackend.finalisedRecords(hib, sessionId, from, to)) {
			AttendanceSession s = (AttendanceSession)o[0]; AttendanceRecord r = (AttendanceRecord)o[1];
			int[] c = per.computeIfAbsent(s.getClassId(), k -> new int[4]);
			sessions.computeIfAbsent(s.getClassId(), k -> new HashSet<Long>()).add(s.getUniqueId());
			switch (r.getStatus()) { case "PRESENT": c[0]++; break; case "LATE": c[1]++; break; case "ABSENT": c[2]++; break; default: c[3]++; }
		}
		List<Object[]> rows = new ArrayList<Object[]>();
		for (Map.Entry<Long, int[]> x: per.entrySet()) {
			Class_ c = hib.get(Class_.class, x.getKey());
			if (c == null) continue;
			rows.add(new Object[] {AttendanceBackend.course(c), c.getClassLabel(), AttendanceService.instructorNames(c), x.getKey(), x.getValue()});
		}
		Collections.sort(rows, new Comparator<Object[]>() {
			@Override public int compare(Object[] a, Object[] b) { int cmp = ((String)a[0]).compareTo((String)b[0]); return cmp != 0 ? cmp : ((String)a[1]).compareTo((String)b[1]); }
		});
		int lowCount = 0;
		for (Object[] r: rows) {
			int[] c = (int[])r[4];
			double rt = rate(cfg, c[0], c[1], c[2], c[3]);
			if (rt < cfg.minPct) lowCount++;
			t.rows.add(Arrays.asList((String)r[0], (String)r[1], (String)r[2], n(enrolled.getOrDefault((Long)r[3], 0)), n(sessions.get((Long)r[3]).size()),
					n(c[0]), n(c[1]), n(c[2]), n(c[3]), pct(rt)));
		}
		summary(t, "Classes", n(rows.size()));
		summary(t, "Below " + cfg.minPct + "%", n(lowCount));
		return t;
	}

	static ReportTable compliance(org.hibernate.Session hib, Long sessionId, Date from, Date to) {
		ReportTable t = table("COMPLIANCE", "Staff", "Scheduled:num", "Finalised on the day:num", "Finalised late:num", "Open:num", "Not taken:num", "On time:share", "Average delay (h):num");
		Map<Long, AttendanceSession> sessions = sessionsByMeeting(hib, sessionId, from, to);
		Map<String, String> relief = reliefs(hib, from, to);
		Map<Long, String> names = new HashMap<Long, String>();
		Map<String, double[]> per = new TreeMap<String, double[]>(); // scheduled, onDay, late, open, notTaken, delayHoursSum, delayCount
		int[] tot = new int[5];
		for (Object[] m: scheduled(hib, sessionId, from, to)) {
			Long classId = (Long)m[1]; Date date = (Date)m[2];
			String who = relief.get(classId + ":" + AttendanceService.DATE.format(date));
			if (who != null) who = who + " (relief)";
			else who = names.computeIfAbsent(classId, id -> { Class_ c = hib.get(Class_.class, id); String n = (c == null ? "" : AttendanceService.instructorNames(c)); return n.isEmpty() ? "(no lecturer assigned)" : n; });
			double[] c = per.computeIfAbsent(who, k -> new double[7]);
			c[0]++; tot[0]++;
			AttendanceSession s = sessions.get((Long)m[0]);
			if (s == null) { c[4]++; tot[4]++; continue; }
			if (!"FINALISED".equals(s.getStatus()) || s.getFinalisedTs() == null) { c[3]++; tot[3]++; continue; }
			if (sameDay(s.getFinalisedTs(), date)) { c[1]++; tot[1]++; } else { c[2]++; tot[2]++; }
			double h = Math.max(0, (s.getFinalisedTs().getTime() - meetingEnd(date, (Integer)m[4]).getTime()) / 3600000.0);
			c[5] += h; c[6]++;
		}
		List<Map.Entry<String, double[]>> rows = new ArrayList<Map.Entry<String, double[]>>(per.entrySet());
		Collections.sort(rows, new Comparator<Map.Entry<String, double[]>>() {
			@Override public int compare(Map.Entry<String, double[]> a, Map.Entry<String, double[]> b) {
				return Double.compare(a.getValue()[1] / a.getValue()[0], b.getValue()[1] / b.getValue()[0]);
			}
		});
		for (Map.Entry<String, double[]> x: rows) {
			double[] c = x.getValue();
			t.rows.add(Arrays.asList(x.getKey(), n((int)c[0]), n((int)c[1]), n((int)c[2]), n((int)c[3]), n((int)c[4]), pct(100.0 * c[1] / c[0]), c[6] == 0 ? "" : pct(c[5] / c[6])));
		}
		summary(t, "Sessions scheduled", n(tot[0]));
		summary(t, "Finalised on the day", tot[0] == 0 ? "0" : n(tot[1]) + " (" + pct(100.0 * tot[1] / tot[0]) + "%)");
		summary(t, "Finalised late", n(tot[2]));
		summary(t, "Open", n(tot[3]));
		summary(t, "Not taken", n(tot[4]));
		t.note = "Timetabled meetings of classes with enrolled students. A session covered under Relief Planning counts for the relief teacher. Delay: from the end of the class to finalising.";
		return t;
	}

	static ReportTable lateness(org.hibernate.Session hib, Long sessionId, Date from, Date to) {
		ReportTable t = table("LATENESS", "Student", "Id", "Attended:num", "Late:num", "Late share:share", "Most late in", "Last late");
		Map<Long, int[]> per = new HashMap<Long, int[]>(); // attended, late
		Map<Long, Map<Long, Integer>> perClass = new HashMap<Long, Map<Long, Integer>>();
		Map<Long, Date> last = new HashMap<Long, Date>();
		for (Object[] o: AttendanceBackend.finalisedRecords(hib, sessionId, from, to)) {
			AttendanceSession s = (AttendanceSession)o[0]; AttendanceRecord r = (AttendanceRecord)o[1];
			if (!"PRESENT".equals(r.getStatus()) && !"LATE".equals(r.getStatus())) continue;
			int[] c = per.computeIfAbsent(r.getStudentId(), k -> new int[2]);
			c[0]++;
			if ("LATE".equals(r.getStatus())) {
				c[1]++;
				perClass.computeIfAbsent(r.getStudentId(), k -> new HashMap<Long, Integer>()).merge(s.getClassId(), 1, Integer::sum);
				Date d = last.get(r.getStudentId());
				if (d == null || s.getMeetingDate().after(d)) last.put(r.getStudentId(), s.getMeetingDate());
			}
		}
		List<Map.Entry<Long, int[]>> rows = new ArrayList<Map.Entry<Long, int[]>>();
		int totalLate = 0;
		for (Map.Entry<Long, int[]> x: per.entrySet()) if (x.getValue()[1] > 0) { rows.add(x); totalLate += x.getValue()[1]; }
		Collections.sort(rows, new Comparator<Map.Entry<Long, int[]>>() {
			@Override public int compare(Map.Entry<Long, int[]> a, Map.Entry<Long, int[]> b) { return Integer.compare(b.getValue()[1], a.getValue()[1]); }
		});
		Map<Long, String> classLabels = new HashMap<Long, String>();
		for (Map.Entry<Long, int[]> x: rows) {
			Student st = hib.get(Student.class, x.getKey());
			Long cls = Collections.max(perClass.get(x.getKey()).entrySet(), Map.Entry.comparingByValue()).getKey();
			String cl = classLabels.computeIfAbsent(cls, id -> { Class_ c = hib.get(Class_.class, id); return c == null ? "?" : c.getClassLabel(); });
			int[] c = x.getValue();
			t.rows.add(Arrays.asList(st == null ? "?" : AttendanceService.name(st), st == null ? "" : st.getExternalUniqueId(), n(c[0]), n(c[1]), pct(100.0 * c[1] / c[0]),
					cl + " (" + perClass.get(x.getKey()).get(cls) + ")", AttendanceService.DATE.format(last.get(x.getKey()))));
		}
		summary(t, "Students late at least once", n(rows.size()));
		summary(t, "Late arrivals", n(totalLate));
		t.note = "Late share: late arrivals as a share of the sessions the student attended.";
		return t;
	}

	static ReportTable consecutive(org.hibernate.Session hib, Long sessionId, Config cfg, Date from, Date to) {
		int threshold = Math.max(2, cfg.consecutiveAbsences);
		ReportTable t = table("CONSECUTIVE", "Student", "Id", "Class", "Current run:num", "Longest run:num", "Last attended", "Follow-up:status");
		Map<String, List<Object[]>> seq = new HashMap<String, List<Object[]>>(); // student:class -> {date, status}
		for (Object[] o: hib.createQuery("select r.studentId, s.classId, s.meetingDate, r.status from AttendanceRecord r, AttendanceSession s where r.attendanceSessionId = s.uniqueId and " +
				"s.sessionId = :s and s.status = 'FINALISED' and s.meetingDate >= :f and s.meetingDate <= :t order by s.meetingDate, s.startPeriod", Object[].class)
				.setParameter("s", sessionId).setParameter("f", from).setParameter("t", to).list())
			seq.computeIfAbsent(o[0] + ":" + o[1], k -> new ArrayList<Object[]>()).add(new Object[] {o[2], o[3]});
		Map<String, String> cases = new HashMap<String, String>();
		for (AttendanceFollowup f: hib.createQuery("from AttendanceFollowup where sessionId = :s and ruleType = 'CONSECUTIVE' order by createdTs", AttendanceFollowup.class).setParameter("s", sessionId).list())
			cases.put(f.getStudentId() + ":" + f.getClassId(), "OPEN".equals(f.getStatus()) ? "Open" : "IN_PROGRESS".equals(f.getStatus()) ? "In progress" : "Closed");
		List<Object[]> rows = new ArrayList<Object[]>();
		for (Map.Entry<String, List<Object[]>> x: seq.entrySet()) {
			int run = 0, longest = 0; Date lastAttended = null;
			for (Object[] r: x.getValue()) {
				String st = (String)r[1];
				if ("ABSENT".equals(st)) { run++; longest = Math.max(longest, run); }
				else if ("EXCUSED".equals(st)) { /* an excused absence neither breaks nor extends the run */ }
				else { run = 0; lastAttended = (Date)r[0]; }
			}
			if (longest < threshold) continue;
			rows.add(new Object[] {x.getKey(), run, longest, lastAttended});
		}
		Collections.sort(rows, new Comparator<Object[]>() {
			@Override public int compare(Object[] a, Object[] b) { int cmp = Integer.compare((Integer)b[1], (Integer)a[1]); return cmp != 0 ? cmp : Integer.compare((Integer)b[2], (Integer)a[2]); }
		});
		int current = 0;
		for (Object[] r: rows) {
			String[] k = ((String)r[0]).split(":");
			Student st = hib.get(Student.class, Long.valueOf(k[0]));
			Class_ c = hib.get(Class_.class, Long.valueOf(k[1]));
			if ((Integer)r[1] >= threshold) current++;
			t.rows.add(Arrays.asList(st == null ? "?" : AttendanceService.name(st), st == null ? "" : st.getExternalUniqueId(), c == null ? "?" : c.getClassLabel(),
					n((Integer)r[1]), n((Integer)r[2]), r[3] == null ? "never" : AttendanceService.DATE.format((Date)r[3]), cases.getOrDefault(r[0], "None")));
		}
		summary(t, "Runs of " + threshold + " or more", n(rows.size()));
		summary(t, "Still absent (current run " + threshold + "+)", n(current));
		t.note = "Unexcused absences in consecutive finalised sessions of the same class; an excused absence neither breaks nor extends a run. Threshold: the configured number of consecutive absences (" + threshold + ").";
		return t;
	}

	static ReportTable monthly(org.hibernate.Session hib, Long sessionId, Config cfg, Date from, Date to) {
		ReportTable t = table("MONTHLY", "Month", "Sessions:num", "Present:num", "Late:num", "Absent:num", "Excused:num", "Attendance:pct");
		List<ReportRow> rows = AttendanceBackend.report(hib, sessionId, cfg, "MONTH", from, to);
		int p = 0, l = 0, a = 0, e = 0, s = 0;
		for (ReportRow r: rows) { rateRow(t, r.label, r); p += r.present; l += r.late; a += r.absent; e += r.excused; s += r.sessions; }
		if (!rows.isEmpty()) {
			t.rows.add(Arrays.asList("Term to date", n(s), n(p), n(l), n(a), n(e), pct(rate(cfg, p, l, a, e))));
			summary(t, "Term attendance", pct(rate(cfg, p, l, a, e)) + "%");
		}
		t.note = "The last line is the whole period chosen (by default the term to date).";
		return t;
	}

	static ReportTable below(org.hibernate.Session hib, Long sessionId, Config cfg, Date from, Date to) {
		ReportTable t = table("BELOW", "Student", "Id", "Class", "Sessions:num", "Attended:num", "Attendance:pct", "Standing:status", "Follow-up:status");
		Map<String, int[]> per = new HashMap<String, int[]>(); // P L A E
		for (Object[] o: AttendanceBackend.finalisedRecords(hib, sessionId, from, to)) {
			AttendanceSession s = (AttendanceSession)o[0]; AttendanceRecord r = (AttendanceRecord)o[1];
			int[] c = per.computeIfAbsent(r.getStudentId() + ":" + s.getClassId(), k -> new int[4]);
			switch (r.getStatus()) { case "PRESENT": c[0]++; break; case "LATE": c[1]++; break; case "ABSENT": c[2]++; break; default: c[3]++; }
		}
		Map<String, String> cases = new HashMap<String, String>();
		for (AttendanceFollowup f: hib.createQuery("from AttendanceFollowup where sessionId = :s and ruleType in ('BELOW_MIN', 'ELIGIBILITY_RISK') and status <> 'CLOSED'", AttendanceFollowup.class).setParameter("s", sessionId).list())
			cases.put(f.getStudentId() + ":" + f.getClassId(), "IN_PROGRESS".equals(f.getStatus()) ? "In progress" : "Open");
		List<Object[]> rows = new ArrayList<Object[]>();
		Set<Long> students = new HashSet<Long>();
		int risk = 0;
		for (Map.Entry<String, int[]> x: per.entrySet()) {
			int[] c = x.getValue();
			int total = c[0] + c[1] + c[2] + c[3];
			if (total < cfg.minSessions) continue;
			double r = rate(cfg, c[0], c[1], c[2], c[3]);
			if (r >= cfg.minPct) continue;
			rows.add(new Object[] {x.getKey(), c, r});
			students.add(Long.valueOf(x.getKey().split(":")[0]));
			if (cfg.eligibilityPct != null && r < cfg.eligibilityPct) risk++;
		}
		Collections.sort(rows, new Comparator<Object[]>() { @Override public int compare(Object[] a, Object[] b) { return Double.compare((Double)a[2], (Double)b[2]); } });
		for (Object[] r: rows) {
			String[] k = ((String)r[0]).split(":");
			Student st = hib.get(Student.class, Long.valueOf(k[0]));
			Class_ c = hib.get(Class_.class, Long.valueOf(k[1]));
			int[] x = (int[])r[1];
			double rt = (Double)r[2];
			int attended = x[0] + (cfg.lateCountsPresent ? x[1] : 0) + (cfg.excusedCountsPresent ? x[3] : 0);
			t.rows.add(Arrays.asList(st == null ? "?" : AttendanceService.name(st), st == null ? "" : st.getExternalUniqueId(), c == null ? "?" : c.getClassLabel(),
					n(x[0] + x[1] + x[2] + x[3]), n(attended), pct(rt),
					cfg.eligibilityPct != null && rt < cfg.eligibilityPct ? "Eligibility at risk" : "Below minimum", cases.getOrDefault(r[0], "None")));
		}
		summary(t, "Students", n(students.size()));
		summary(t, "Student-class pairs", n(rows.size()));
		summary(t, "Eligibility at risk", n(risk));
		t.note = "Minimum " + cfg.minPct + "%" + (cfg.eligibilityPct == null ? "" : ", examination eligibility " + cfg.eligibilityPct + "%") + "; only classes with at least " + cfg.minSessions + " finalised sessions for the student.";
		return t;
	}

	static ReportTable notifications(org.hibernate.Session hib, Long sessionId) {
		ReportTable t = table("NOTIFICATIONS", "Sent", "Recipient", "Type", "Address", "Student", "Class", "Status:status", "Error:long");
		Map<String, Integer> byStatus = new LinkedHashMap<String, Integer>();
		for (String s: new String[] {"SENT", "FAILED", "NOT_SENT", "NO_ADDRESS"}) byStatus.put(s, 0);
		Map<String, Integer> byType = new TreeMap<String, Integer>();
		Map<Long, String> classLabels = new HashMap<Long, String>();
		Map<Long, String> studentLabels = new HashMap<Long, String>();
		for (AttendanceNotification n: hib.createQuery("from AttendanceNotification where sessionId = :s order by sentTs desc, uniqueId desc", AttendanceNotification.class).setParameter("s", sessionId).list()) {
			byStatus.merge(n.getStatus(), 1, Integer::sum);
			byType.merge(n.getRecipientType(), 1, Integer::sum);
			String stu = (n.getStudentId() == null ? "" : studentLabels.computeIfAbsent(n.getStudentId(), id -> { Student s = hib.get(Student.class, id); return s == null ? "?" : AttendanceService.name(s) + " (" + s.getExternalUniqueId() + ")"; }));
			String cls = (n.getClassId() == null ? "" : classLabels.computeIfAbsent(n.getClassId(), id -> { Class_ c = hib.get(Class_.class, id); return c == null ? "?" : c.getClassLabel(); }));
			t.rows.add(Arrays.asList(DT.format(n.getSentTs()), n.getRecipientName() == null ? "" : n.getRecipientName(), label(n.getRecipientType()), n.getAddress() == null ? "" : n.getAddress(),
					stu, cls, statusLabel(n.getStatus()), n.getError() == null ? "" : n.getError()));
		}
		for (Map.Entry<String, Integer> x: byStatus.entrySet()) summary(t, statusLabel(x.getKey()), n(x.getValue()));
		t.note = "One line per notification and recipient. Recipients follow the settings (lecturers, department timetable coordinators, the student). " +
				"Parents and guardians cannot be notified: the product holds no guardian contact data (this would come from SIMS).";
		return t;
	}

	static String label(String type) {
		if (type == null) return "";
		switch (type) { case "LECTURER": return "Lecturer"; case "COORDINATOR": return "Coordinator"; case "STUDENT": return "Student"; default: return type; }
	}

	static String statusLabel(String s) {
		if (s == null) return "";
		switch (s) { case "SENT": return "Sent"; case "FAILED": return "Failed"; case "NOT_SENT": return "Not sent"; case "NO_ADDRESS": return "No e-mail address"; default: return s; }
	}

	static String month(Date d) { return new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(d); }
}
