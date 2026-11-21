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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.unitime.commons.Email;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Config;
import org.unitime.timetable.model.AttendanceConfig;
import org.unitime.timetable.model.AttendanceExcuse;
import org.unitime.timetable.model.AttendanceFollowup;
import org.unitime.timetable.model.AttendanceNotification;
import org.unitime.timetable.model.AttendanceRecord;
import org.unitime.timetable.model.AttendanceSession;
import org.unitime.timetable.model.ClassEvent;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * Attendance management. Attendance sessions are the dated meetings of each class's timetable event
 * (created when the class timetable is committed); an AttendanceSession row is created the first time a
 * meeting is marked or opened for QR check-in.
 *
 * Who may take attendance: the class's instructors, the relief teacher covering that meeting (Relief
 * Planning), and attendance managers. Rates count PRESENT, LATE (configurable) and EXCUSED (configurable)
 * as attended, over the finalised sessions. Follow-up rules (below the minimum %, at risk for examination
 * eligibility, consecutive unexcused absences) raise follow-up cases for staff to review.
 */
public class AttendanceService {
	private static Logger sLog = LogManager.getLogger(AttendanceService.class);
	public static final String PRESENT = "PRESENT", LATE = "LATE", ABSENT = "ABSENT", EXCUSED = "EXCUSED";
	public static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd");

	// ------------------------------------------------------------------ configuration

	public static AttendanceConfig configEntity(org.hibernate.Session hib, Long sessionId) {
		return hib.createQuery("from AttendanceConfig where sessionId = :s", AttendanceConfig.class).setParameter("s", sessionId).setMaxResults(1).uniqueResult();
	}

	public static Config config(org.hibernate.Session hib, Long sessionId) {
		Config c = new Config();
		AttendanceConfig x = configEntity(hib, sessionId);
		if (x != null) {
			c.minPct = x.getMinPct(); c.eligibilityPct = x.getEligibilityPct(); c.lateMinutes = x.getLateMinutes(); c.checkinMinutes = x.getCheckinMinutes();
			c.rollcallEnabled = Boolean.TRUE.equals(x.getRollcallEnabled()); c.qrEnabled = Boolean.TRUE.equals(x.getQrEnabled()); c.qrRotationSec = x.getQrRotationSec();
			c.oneDevice = Boolean.TRUE.equals(x.getOneDevice()); c.lateCountsPresent = Boolean.TRUE.equals(x.getLateCountsPresent());
			c.excusedCountsPresent = Boolean.TRUE.equals(x.getExcusedCountsPresent()); c.consecutiveAbsences = x.getConsecutiveAbsences();
			c.minSessions = x.getMinSessions(); c.alertRecipients = x.getAlertRecipients(); c.excuseApprover = x.getExcuseApprover();
			c.notifyStudent = Boolean.TRUE.equals(x.getNotifyStudent()); c.exportColumns = x.getExportColumns();
		}
		return c;
	}

	public static void saveConfig(org.hibernate.Session hib, Long sessionId, Config c) {
		AttendanceConfig x = configEntity(hib, sessionId);
		if (x == null) { x = new AttendanceConfig(); x.setSessionId(sessionId); }
		x.setMinPct(c.minPct); x.setEligibilityPct(c.eligibilityPct); x.setLateMinutes(c.lateMinutes); x.setCheckinMinutes(c.checkinMinutes);
		x.setRollcallEnabled(c.rollcallEnabled); x.setQrEnabled(c.qrEnabled); x.setQrRotationSec(c.qrRotationSec); x.setOneDevice(c.oneDevice);
		x.setLateCountsPresent(c.lateCountsPresent); x.setExcusedCountsPresent(c.excusedCountsPresent); x.setConsecutiveAbsences(c.consecutiveAbsences);
		x.setMinSessions(c.minSessions); x.setAlertRecipients(c.alertRecipients); x.setExcuseApprover(c.excuseApprover);
		x.setNotifyStudent(c.notifyStudent); x.setExportColumns(c.exportColumns);
		if (x.getUniqueId() == null) hib.persist(x); else hib.merge(x);
	}

	// ------------------------------------------------------------------ meetings and access

	public static List<Meeting> meetings(org.hibernate.Session hib, Long sessionId, Date day) {
		return hib.createQuery("select m from Meeting m, ClassEvent e where m.event = e and m.meetingDate = :d and " +
				"e.clazz.schedulingSubpart.instrOfferingConfig.instructionalOffering.session.uniqueId = :s order by m.startPeriod", Meeting.class)
				.setParameter("d", day).setParameter("s", sessionId).list();
	}

	public static Meeting meeting(org.hibernate.Session hib, Long meetingId) {
		return hib.get(Meeting.class, meetingId);
	}

	public static Class_ classOf(Meeting m) {
		if (m == null) return null;
		Object e = org.hibernate.Hibernate.unproxy(m.getEvent());
		return (e instanceof ClassEvent ? ((ClassEvent)e).getClazz() : null);
	}

	public static Set<String> instructorIds(Class_ c) {
		Set<String> ret = new HashSet<String>();
		if (c.getClassInstructors() != null)
			for (ClassInstructor ci: c.getClassInstructors())
				if (ci.getInstructor() != null && ci.getInstructor().getExternalUniqueId() != null) ret.add(ci.getInstructor().getExternalUniqueId());
		return ret;
	}

	public static String instructorNames(Class_ c) {
		Set<String> ret = new TreeSet<String>();
		if (c.getClassInstructors() != null)
			for (ClassInstructor ci: c.getClassInstructors()) if (ci.getInstructor() != null) ret.add(ci.getInstructor().getNameLastFirst());
		return String.join("; ", ret);
	}

	public static ReliefAssignment relief(org.hibernate.Session hib, Class_ c, Date day) {
		List<ReliefAssignment> list = hib.createQuery("from ReliefAssignment where clazz.uniqueId = :c and meetingDate = :d", ReliefAssignment.class)
				.setParameter("c", c.getUniqueId()).setParameter("d", day).list();
		for (ReliefAssignment r: list) if (r.isAssigned()) return r;
		return null;
	}

	public static boolean canTake(org.hibernate.Session hib, SessionContext context, Class_ c, Date day) {
		if (context.hasPermission(Right.AttendanceManage)) return true;
		if (!context.hasPermission(Right.AttendanceTake)) return false;
		String me = context.getUser().getExternalUserId();
		if (me == null) return false;
		if (instructorIds(c).contains(me)) return true;
		ReliefAssignment r = relief(hib, c, day);
		return r != null && me.equals(r.getReliefUid());
	}

	/** classes the user teaches in the session */
	public static Set<Long> myClasses(org.hibernate.Session hib, SessionContext context, Long sessionId) {
		Set<Long> ret = new HashSet<Long>();
		String me = context.getUser().getExternalUserId();
		if (me == null) return ret;
		ret.addAll(hib.createQuery("select ci.classInstructing.uniqueId from ClassInstructor ci where ci.instructor.externalUniqueId = :e and " +
				"ci.instructor.department.session.uniqueId = :s", Long.class).setParameter("e", me).setParameter("s", sessionId).list());
		return ret;
	}

	public static List<Student> roster(org.hibernate.Session hib, Long classId) {
		return hib.createQuery("select distinct e.student from StudentClassEnrollment e where e.clazz.uniqueId = :c order by e.student.lastName, e.student.firstName", Student.class)
				.setParameter("c", classId).list();
	}

	public static String location(org.hibernate.Session hib, Long sessionId, Long permId) {
		if (permId == null) return "";
		List<Location> l = hib.createQuery("from Location where permanentId = :p and session.uniqueId = :s", Location.class).setParameter("p", permId).setParameter("s", sessionId).setMaxResults(1).list();
		return l.isEmpty() ? "" : l.get(0).getLabel();
	}

	public static String time(Integer startPeriod, Integer stopPeriod) {
		return Constants.toTime(5 * startPeriod) + " - " + Constants.toTime(5 * stopPeriod);
	}

	public static AttendanceSession session(org.hibernate.Session hib, Long meetingId) {
		return hib.createQuery("from AttendanceSession where meetingId = :m", AttendanceSession.class).setParameter("m", meetingId).setMaxResults(1).uniqueResult();
	}

	/** The attendance session of a meeting, created (not yet persisted) if missing. */
	public static AttendanceSession sessionFor(org.hibernate.Session hib, Long sessionId, Meeting m, SessionContext context) {
		AttendanceSession s = session(hib, m.getUniqueId());
		if (s == null) {
			Class_ c = classOf(m);
			s = new AttendanceSession();
			s.setSessionId(sessionId); s.setMeetingId(m.getUniqueId()); s.setClassId(c.getUniqueId()); s.setMeetingDate(m.getMeetingDate());
			s.setStartPeriod(m.getStartPeriod()); s.setStopPeriod(m.getStopPeriod()); s.setLocationPermId(m.getLocationPermanentId());
			s.setStatus("OPEN");
			hib.persist(s);
		}
		if (context != null) { s.setTakerUid(context.getUser().getExternalUserId()); s.setTakerName(context.getUser().getName()); }
		return s;
	}

	public static List<AttendanceRecord> records(org.hibernate.Session hib, Long attendanceSessionId) {
		return hib.createQuery("from AttendanceRecord where attendanceSessionId = :a", AttendanceRecord.class).setParameter("a", attendanceSessionId).list();
	}

	// ------------------------------------------------------------------ excuses

	public static List<AttendanceExcuse> approvedExcuses(org.hibernate.Session hib, Long sessionId, Collection<Long> studentIds) {
		if (studentIds.isEmpty()) return new ArrayList<AttendanceExcuse>();
		return hib.createQuery("from AttendanceExcuse where sessionId = :s and status = 'APPROVED' and studentId in :ids", AttendanceExcuse.class)
				.setParameter("s", sessionId).setParameterList("ids", studentIds).list();
	}

	public static boolean covers(AttendanceExcuse e, Date day) {
		String d = DATE.format(day);
		return DATE.format(e.getFromDate()).compareTo(d) <= 0 && DATE.format(e.getToDate()).compareTo(d) >= 0;
	}

	// ------------------------------------------------------------------ rates and rules

	/** Per student, per class: counts over finalised sessions. key = studentId:classId -> [present, late, absent, excused] */
	public static Map<String, int[]> counts(org.hibernate.Session hib, Long sessionId, Collection<Long> studentIds) {
		Map<String, int[]> ret = new HashMap<String, int[]>();
		String q = "select r.studentId, s.classId, r.status from AttendanceRecord r, AttendanceSession s where r.attendanceSessionId = s.uniqueId and s.sessionId = :s and s.status = 'FINALISED'" +
				(studentIds == null ? "" : " and r.studentId in :ids");
		org.hibernate.query.Query<Object[]> query = hib.createQuery(q, Object[].class).setParameter("s", sessionId);
		if (studentIds != null) { if (studentIds.isEmpty()) return ret; query.setParameterList("ids", studentIds); }
		for (Object[] row: query.list()) {
			String key = row[0] + ":" + row[1];
			int[] c = ret.get(key);
			if (c == null) { c = new int[4]; ret.put(key, c); }
			c[index((String)row[2])]++;
		}
		return ret;
	}

	public static int index(String status) {
		return PRESENT.equals(status) ? 0 : LATE.equals(status) ? 1 : ABSENT.equals(status) ? 2 : 3;
	}

	public static double rate(Config cfg, int[] c) {
		int total = c[0] + c[1] + c[2] + c[3];
		if (total == 0) return 100.0;
		int attended = c[0] + (cfg.lateCountsPresent ? c[1] : 0) + (cfg.excusedCountsPresent ? c[3] : 0);
		return 100.0 * attended / total;
	}

	public static String standing(Config cfg, int[] c) {
		double r = rate(cfg, c);
		if (cfg.eligibilityPct != null && r < cfg.eligibilityPct) return "ELIGIBILITY_RISK";
		if (r < cfg.minPct) return "BELOW_MIN";
		return "OK";
	}

	/**
	 * Evaluate the follow-up rules for the given students (null = everyone) and raise cases (one open case per
	 * student, class and rule). Returns the new cases (persisted; call inside a transaction).
	 */
	public static List<AttendanceFollowup> evaluate(org.hibernate.Session hib, Long sessionId, Config cfg, Collection<Long> studentIds) {
		List<AttendanceFollowup> created = new ArrayList<AttendanceFollowup>();
		Map<String, int[]> counts = counts(hib, sessionId, studentIds);
		Set<String> open = new HashSet<String>();
		Map<String, List<AttendanceFollowup>> openCases = new HashMap<String, List<AttendanceFollowup>>();
		for (AttendanceFollowup f: hib.createQuery("from AttendanceFollowup where sessionId = :s and status <> 'CLOSED'", AttendanceFollowup.class).setParameter("s", sessionId).list()) {
			open.add(f.getStudentId() + ":" + f.getClassId() + ":" + f.getRuleType());
			openCases.computeIfAbsent(f.getStudentId() + ":" + f.getClassId(), x -> new ArrayList<AttendanceFollowup>()).add(f);
		}
		// consecutive absences: last N finalised sessions of the class for the student
		Map<String, List<String>> sequence = new HashMap<String, List<String>>();
		String q = "select r.studentId, s.classId, r.status from AttendanceRecord r, AttendanceSession s where r.attendanceSessionId = s.uniqueId and s.sessionId = :s and s.status = 'FINALISED'" +
				(studentIds == null ? "" : " and r.studentId in :ids") + " order by s.meetingDate, s.startPeriod";
		org.hibernate.query.Query<Object[]> query = hib.createQuery(q, Object[].class).setParameter("s", sessionId);
		if (studentIds != null) { if (studentIds.isEmpty()) return created; query.setParameterList("ids", studentIds); }
		for (Object[] row: query.list()) {
			String key = row[0] + ":" + row[1];
			List<String> l = sequence.get(key);
			if (l == null) { l = new ArrayList<String>(); sequence.put(key, l); }
			l.add((String)row[2]);
		}
		for (Map.Entry<String, int[]> e: counts.entrySet()) {
			int[] c = e.getValue();
			int total = c[0] + c[1] + c[2] + c[3];
			if (total < cfg.minSessions) continue;
			String[] k = e.getKey().split(":");
			Long studentId = Long.valueOf(k[0]), classId = Long.valueOf(k[1]);
			double r = rate(cfg, c);
			String pct = String.format("%.0f%%", r);
			String applies = null;
			if (cfg.eligibilityPct != null && r < cfg.eligibilityPct) {
				applies = "ELIGIBILITY_RISK";
				raise(hib, sessionId, studentId, classId, applies, "Attendance " + pct + " is below the examination eligibility threshold of " + cfg.eligibilityPct + "% (" + total + " sessions)", open, created);
			} else if (r < cfg.minPct) {
				applies = "BELOW_MIN";
				raise(hib, sessionId, studentId, classId, applies, "Attendance " + pct + " is below the minimum of " + cfg.minPct + "% (" + total + " sessions)", open, created);
			}
			// A rate case that no staff member has picked up yet is closed automatically when it no longer applies
			// (attendance recovered, or moved between below-minimum and eligibility risk); cases staff work on stay.
			List<AttendanceFollowup> mine = openCases.get(e.getKey());
			if (mine != null)
				for (AttendanceFollowup f: mine) {
					boolean rateRule = "ELIGIBILITY_RISK".equals(f.getRuleType()) || "BELOW_MIN".equals(f.getRuleType());
					if (!rateRule || f.getRuleType().equals(applies) || !"OPEN".equals(f.getStatus()) || f.getUpdatedBy() != null) continue;
					f.setStatus("CLOSED");
					f.setOutcome(applies == null ? "Resolved automatically: attendance recovered to " + pct : "Superseded: attendance now " + pct);
					f.setUpdatedBy("Attendance rules"); f.setUpdatedTs(new Date());
					hib.merge(f);
					open.remove(f.getStudentId() + ":" + f.getClassId() + ":" + f.getRuleType());
				}
			List<String> seq = sequence.get(e.getKey());
			if (seq != null && cfg.consecutiveAbsences > 0 && seq.size() >= cfg.consecutiveAbsences) {
				boolean all = true;
				for (int i = seq.size() - cfg.consecutiveAbsences; i < seq.size(); i++) if (!ABSENT.equals(seq.get(i))) all = false;
				if (all) raise(hib, sessionId, studentId, classId, "CONSECUTIVE", cfg.consecutiveAbsences + " consecutive unexcused absences", open, created);
			}
		}
		return created;
	}

	private static void raise(org.hibernate.Session hib, Long sessionId, Long studentId, Long classId, String rule, String detail, Set<String> open, List<AttendanceFollowup> created) {
		String key = studentId + ":" + classId + ":" + rule;
		if (open.contains(key)) return;
		open.add(key);
		AttendanceFollowup f = new AttendanceFollowup();
		f.setSessionId(sessionId); f.setStudentId(studentId); f.setClassId(classId); f.setRuleType(rule); f.setDetail(detail);
		f.setStatus("OPEN"); f.setCreatedTs(new Date());
		hib.persist(f);
		created.add(f);
	}

	// ------------------------------------------------------------------ notifications (best effort)

	/** Recipients of a follow-up case per the configuration: the class's lecturers and/or department timetable managers. */
	public static Map<String, String> recipients(org.hibernate.Session hib, Config cfg, Class_ c) {
		Map<String, String> ret = new HashMap<String, String>(); // email -> name
		if (c == null) return ret;
		String who = (cfg.alertRecipients == null ? "" : cfg.alertRecipients.toUpperCase());
		if (who.contains("LECTURER") && c.getClassInstructors() != null)
			for (ClassInstructor ci: c.getClassInstructors())
				if (ci.getInstructor() != null && ci.getInstructor().getEmail() != null) ret.put(ci.getInstructor().getEmail(), ci.getInstructor().getNameLastFirst());
		if (who.contains("COORDINATOR")) {
			Department d = c.getManagingDept();
			if (d != null && d.getTimetableManagers() != null)
				for (TimetableManager m: d.getTimetableManagers()) if (m.getEmailAddress() != null) ret.put(m.getEmailAddress(), m.getName());
		}
		return ret;
	}

	public static String recipientNames(org.hibernate.Session hib, Config cfg, Class_ c) {
		Set<String> names = new LinkedHashSet<String>();
		String who = (cfg.alertRecipients == null ? "" : cfg.alertRecipients.toUpperCase());
		if (c != null && who.contains("LECTURER")) { String n = instructorNames(c); if (!n.isEmpty()) names.add("Lecturer: " + n); }
		if (c != null && who.contains("COORDINATOR") && c.getManagingDept() != null && c.getManagingDept().getTimetableManagers() != null)
			for (TimetableManager m: c.getManagingDept().getTimetableManagers()) names.add("Coordinator: " + m.getName());
		return String.join("; ", names);
	}

	/** Who a follow-up notification goes to, per the configuration: {type, name, e-mail or null}. */
	public static List<String[]> recipientList(Config cfg, Class_ c, Student s) {
		List<String[]> ret = new ArrayList<String[]>();
		Set<String> seen = new HashSet<String>();
		String who = (cfg.alertRecipients == null ? "" : cfg.alertRecipients.toUpperCase());
		if (c != null && who.contains("LECTURER") && c.getClassInstructors() != null)
			for (ClassInstructor ci: c.getClassInstructors())
				if (ci.getInstructor() != null && seen.add("I" + ci.getInstructor().getUniqueId()))
					ret.add(new String[] {"LECTURER", ci.getInstructor().getNameLastFirst(), blank(ci.getInstructor().getEmail())});
		if (c != null && who.contains("COORDINATOR") && c.getManagingDept() != null && c.getManagingDept().getTimetableManagers() != null)
			for (TimetableManager m: c.getManagingDept().getTimetableManagers())
				if (seen.add("M" + m.getUniqueId())) ret.add(new String[] {"COORDINATOR", m.getName(), blank(m.getEmailAddress())});
		if (cfg.notifyStudent && s != null) ret.add(new String[] {"STUDENT", name(s), blank(s.getEmail())});
		return ret;
	}

	private static String blank(String email) { return (email == null || email.trim().isEmpty() ? null : email.trim()); }

	/**
	 * Sends the follow-up notifications, one e-mail per recipient, and records each in the delivery log
	 * (attendance_notification): SENT, FAILED (with the error), NO_ADDRESS (no e-mail on record), or NOT_SENT
	 * (not attempted because the mail server failed earlier in the same run). Returns the number sent.
	 */
	public static int notify(org.hibernate.Session hib, Config cfg, List<AttendanceFollowup> cases) {
		int sent = 0;
		String mailDown = null;
		for (AttendanceFollowup f: cases) {
			Class_ c = (f.getClassId() == null ? null : hib.get(Class_.class, f.getClassId()));
			Student s = hib.get(Student.class, f.getStudentId());
			String sn = (s == null ? "Student " + f.getStudentId() : name(s));
			for (String[] r: recipientList(cfg, c, s)) {
				boolean student = "STUDENT".equals(r[0]);
				String subject = (student ? "Attendance notice" + (c == null ? "" : ": " + c.getClassLabel())
						: "Attendance follow-up: " + sn + (c == null ? "" : " (" + c.getClassLabel() + ")"));
				AttendanceNotification n = new AttendanceNotification();
				n.setSessionId(f.getSessionId()); n.setFollowupId(f.getUniqueId()); n.setStudentId(f.getStudentId()); n.setClassId(f.getClassId());
				n.setRecipientType(r[0]); n.setRecipientName(r[1] == null ? null : (r[1].length() > 200 ? r[1].substring(0, 200) : r[1]));
				n.setAddress(r[2]); n.setSubject(subject.length() > 300 ? subject.substring(0, 300) : subject); n.setSentTs(new Date());
				if (r[2] == null) {
					n.setStatus("NO_ADDRESS");
				} else if (mailDown != null) {
					n.setStatus("NOT_SENT"); n.setError("Not attempted: the mail server failed earlier (" + mailDown + ")");
				} else {
					try {
						Email mail = Email.createEmail();
						mail.addRecipient(r[2], r[1]);
						mail.setSubject(subject);
						mail.setText(student
								? "Dear " + sn + ",\n\n" + f.getDetail() + ".\nPlease contact your lecturer. If you were ill or on approved leave, submit your medical certificate or leave under My Attendance."
								: f.getDetail() + "\n\nPlease review the case on the Attendance page (Follow-up) and record the action taken.");
						mail.send();
						n.setStatus("SENT"); sent++;
					} catch (Throwable t) {
						String msg = (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
						mailDown = (msg.length() > 200 ? msg.substring(0, 200) : msg);
						n.setStatus("FAILED"); n.setError(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
						sLog.info("Attendance notification not sent: " + msg);
					}
				}
				hib.persist(n);
			}
		}
		return sent;
	}

	public static String name(Student s) {
		return (s.getLastName() == null ? "" : s.getLastName()) + (s.getFirstName() == null ? "" : ", " + s.getFirstName());
	}

	// ------------------------------------------------------------------ QR tokens

	public static String newSecret() {
		byte[] b = new byte[24];
		new SecureRandom().nextBytes(b);
		StringBuffer s = new StringBuffer();
		for (byte x: b) s.append(String.format("%02x", x));
		return s.toString();
	}

	public static long interval(Config cfg, long now) { return now / (1000L * Math.max(5, cfg.qrRotationSec)); }

	public static String token(String secret, Long meetingId, long interval) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] h = mac.doFinal((meetingId + ":" + interval).getBytes(StandardCharsets.UTF_8));
			StringBuffer s = new StringBuffer();
			for (int i = 0; i < 6; i++) s.append(String.format("%02x", h[i]));
			return s.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/** The current token or the previous one (a short grace period for slow phones). */
	public static boolean validToken(Config cfg, AttendanceSession s, String token) {
		if (s.getQrSecret() == null || token == null) return false;
		long i = interval(cfg, System.currentTimeMillis());
		return token.equals(token(s.getQrSecret(), s.getMeetingId(), i)) || token.equals(token(s.getQrSecret(), s.getMeetingId(), i - 1));
	}

	public static Date plusMinutes(Date d, int minutes) {
		Calendar c = Calendar.getInstance(); c.setTime(d); c.add(Calendar.MINUTE, minutes); return c.getTime();
	}
}
