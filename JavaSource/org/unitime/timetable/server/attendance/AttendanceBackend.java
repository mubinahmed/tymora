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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.AttendanceInterface.AttendanceRequest;
import org.unitime.timetable.gwt.shared.AttendanceInterface.AttendanceResponse;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Config;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Excuse;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Followup;
import org.unitime.timetable.gwt.shared.AttendanceInterface.Mark;
import org.unitime.timetable.gwt.shared.AttendanceInterface.ReportRow;
import org.unitime.timetable.gwt.shared.AttendanceInterface.RosterEntry;
import org.unitime.timetable.gwt.shared.AttendanceInterface.SessionInfo;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.LogEntry;
import org.unitime.timetable.model.AttendanceExcuse;
import org.unitime.timetable.model.AttendanceFollowup;
import org.unitime.timetable.model.AttendanceRecord;
import org.unitime.timetable.model.AttendanceSession;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.ExamAllocationLog;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.dao.AttendanceSessionDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.seating.ExamAllocationSupport;

/**
 * Backend of the Attendance page (AttendanceRequest): sessions of a day, roll call, QR check-in (open /
 * close / rotating token), finalising, excuse approval, follow-up cases, rule evaluation, reports, settings,
 * the SIMS export and the history. Needs Right.AttendanceTake (own and covered classes) or
 * Right.AttendanceManage (everything, settings, excuses when approved by the office, follow-up cases).
 */
@GwtRpcImplements(AttendanceRequest.class)
public class AttendanceBackend implements GwtRpcImplementation<AttendanceRequest, AttendanceResponse> {
	static final String KIND = "ATTENDANCE";

	@Override
	public AttendanceResponse execute(AttendanceRequest request, SessionContext context) {
		boolean manage = context.hasPermission(Right.AttendanceManage), take = context.hasPermission(Right.AttendanceTake);
		if (!manage && !take) throw new GwtRpcException("You are not allowed to take or manage attendance.");
		String op = (request.operation == null ? "INIT" : request.operation);
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		org.hibernate.Session hib = AttendanceSessionDAO.getInstance().getSession();
		Config cfg = AttendanceService.config(hib, sessionId);
		AttendanceResponse response = new AttendanceResponse();
		response.canManage = manage; response.canTake = take || manage; response.config = cfg;
		Session session = SessionDAO.getInstance().get(sessionId);
		String who = context.getUser().getName();

		switch (op) {
		case "INIT": {
			response.termStart = AttendanceService.DATE.format(session.getSessionBeginDateTime());
			response.termEnd = AttendanceService.DATE.format(session.getClassesEndDateTime());
			String today = AttendanceService.DATE.format(new Date());
			response.today = (today.compareTo(response.termStart) >= 0 && today.compareTo(response.termEnd) <= 0 ? today : firstTeachingDay(hib, sessionId, response.termStart));
			response.lastRecorded = AttendanceService.DATE.format(lastRecorded(hib, sessionId, session.getSessionBeginDateTime()));
			if (manage) response.reports = AttendanceReports.catalog();
			break;
		}
		case "SESSIONS": {
			response.sessions = sessions(hib, context, sessionId, day(request.date), "ALL".equals(request.scope) && manage);
			break;
		}
		case "ROLL": case "QR": break;
		case "MARK": {
			Meeting m = meeting(hib, request.meetingId);
			Class_ c = AttendanceService.classOf(m);
			check(hib, context, c, m);
			if (!cfg.rollcallEnabled && !manage) throw new GwtRpcException("Roll call is disabled in the attendance settings.");
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceSession s = AttendanceService.sessionFor(hib, sessionId, m, context);
				boolean amending = "FINALISED".equals(s.getStatus());
				if (amending) {
					if (!manage) throw new GwtRpcException("The session is finalised; an attendance manager can amend it with a reason.");
					if (request.reason == null || request.reason.trim().length() < 5) throw new GwtRpcException("Give a reason for amending a finalised session.");
				}
				Map<Long, AttendanceRecord> existing = new HashMap<Long, AttendanceRecord>();
				for (AttendanceRecord r: AttendanceService.records(hib, s.getUniqueId())) existing.put(r.getStudentId(), r);
				int changed = 0;
				List<String> changes = new ArrayList<String>();
				for (Mark mk: request.marks == null ? new ArrayList<Mark>() : request.marks) {
					if (mk.status == null) continue;
					if (!Arrays.asList(AttendanceService.PRESENT, AttendanceService.LATE, AttendanceService.ABSENT, AttendanceService.EXCUSED).contains(mk.status)) throw new GwtRpcException("Unknown status " + mk.status);
					AttendanceRecord r = existing.get(mk.studentId);
					if (r != null && mk.status.equals(r.getStatus()) && eq(mk.note, r.getNote())) continue;
					if (r == null) { r = new AttendanceRecord(); r.setAttendanceSessionId(s.getUniqueId()); r.setStudentId(mk.studentId); }
					if (amending) changes.add(mk.studentId + ": " + r.getStatus() + " -> " + mk.status);
					r.setStatus(mk.status); r.setSource("ROLLCALL"); r.setMarkedTs(new Date()); r.setMarkedBy(who); r.setNote(trim(mk.note, 500));
					if (r.getUniqueId() == null) hib.persist(r); else hib.merge(r);
					changed++;
				}
				hib.merge(s);
				log(hib, context, sessionId, amending ? "AMEND" : "ROLL_CALL", c.getClassLabel() + " " + AttendanceService.DATE.format(m.getMeetingDate()) + ": " + changed + " mark(s)" + (amending ? "; reason: " + request.reason + "; " + changes : ""));
				if (amending) evaluate(hib, sessionId, cfg, studentsOf(request.marks), response);
				hib.flush(); tx.commit();
				response.message = changed + " mark(s) saved.";
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			break;
		}
		case "OPEN_CHECKIN": case "CLOSE_CHECKIN": {
			Meeting m = meeting(hib, request.meetingId);
			Class_ c = AttendanceService.classOf(m);
			check(hib, context, c, m);
			if ("OPEN_CHECKIN".equals(op) && !cfg.qrEnabled) throw new GwtRpcException("QR check-in is disabled in the attendance settings.");
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceSession s = AttendanceService.sessionFor(hib, sessionId, m, context);
				if ("FINALISED".equals(s.getStatus())) throw new GwtRpcException("The session is finalised.");
				if ("OPEN_CHECKIN".equals(op)) {
					Date now = new Date();
					if (s.getQrSecret() == null) s.setQrSecret(AttendanceService.newSecret());
					if (s.getCheckinOpened() == null) s.setCheckinOpened(now);
					s.setCheckinUntil(AttendanceService.plusMinutes(now, cfg.checkinMinutes));
				} else {
					s.setCheckinUntil(new Date(System.currentTimeMillis() - 1000)); // datetime columns round to whole seconds
				}
				hib.merge(s);
				log(hib, context, sessionId, op, c.getClassLabel() + " " + AttendanceService.DATE.format(m.getMeetingDate()));
				hib.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			break;
		}
		case "FINALISE": case "REOPEN": {
			Meeting m = meeting(hib, request.meetingId);
			Class_ c = AttendanceService.classOf(m);
			check(hib, context, c, m);
			if ("REOPEN".equals(op) && !manage) throw new GwtRpcException("Only an attendance manager can reopen a finalised session.");
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceSession s = AttendanceService.sessionFor(hib, sessionId, m, context);
				if ("FINALISE".equals(op)) {
					Map<Long, AttendanceRecord> existing = new HashMap<Long, AttendanceRecord>();
					for (AttendanceRecord r: AttendanceService.records(hib, s.getUniqueId())) existing.put(r.getStudentId(), r);
					List<Student> roster = AttendanceService.roster(hib, c.getUniqueId());
					Set<Long> ids = new HashSet<Long>();
					for (Student st: roster) ids.add(st.getUniqueId());
					List<AttendanceExcuse> excuses = AttendanceService.approvedExcuses(hib, sessionId, ids);
					int absent = 0, excused = 0;
					for (Student st: roster) {
						AttendanceRecord r = existing.get(st.getUniqueId());
						boolean covered = false;
						for (AttendanceExcuse e: excuses) if (e.getStudentId().equals(st.getUniqueId()) && AttendanceService.covers(e, m.getMeetingDate())) covered = true;
						if (r == null) {
							r = new AttendanceRecord(); r.setAttendanceSessionId(s.getUniqueId()); r.setStudentId(st.getUniqueId());
							r.setStatus(covered ? AttendanceService.EXCUSED : AttendanceService.ABSENT); r.setSource(covered ? "EXCUSE" : "FINALISE");
							r.setMarkedTs(new Date()); r.setMarkedBy(who);
							hib.persist(r);
							if (covered) excused++; else absent++;
						} else if (covered && AttendanceService.ABSENT.equals(r.getStatus())) {
							r.setStatus(AttendanceService.EXCUSED); r.setSource("EXCUSE"); hib.merge(r); excused++;
						}
					}
					s.setStatus("FINALISED"); s.setFinalisedBy(who); s.setFinalisedTs(new Date()); s.setCheckinUntil(s.getCheckinUntil() == null ? null : new Date());
					hib.merge(s);
					hib.flush();
					log(hib, context, sessionId, "FINALISE", c.getClassLabel() + " " + AttendanceService.DATE.format(m.getMeetingDate()) + ": " + absent + " unmarked recorded absent, " + excused + " excused");
					evaluate(hib, sessionId, cfg, ids, response);
					response.message = "Finalised: " + absent + " unmarked student(s) recorded absent, " + excused + " excused." + (response.message == null ? "" : " " + response.message);
				} else {
					if (request.reason == null || request.reason.trim().length() < 5) throw new GwtRpcException("Give a reason for reopening the session.");
					s.setStatus("OPEN"); hib.merge(s);
					log(hib, context, sessionId, "REOPEN", c.getClassLabel() + " " + AttendanceService.DATE.format(m.getMeetingDate()) + ": " + request.reason);
				}
				hib.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			break;
		}
		case "DECIDE_EXCUSE": {
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceExcuse e = hib.get(AttendanceExcuse.class, request.id);
				if (e == null || !e.getSessionId().equals(sessionId)) throw new GwtRpcException("Unknown excuse.");
				if (!canDecide(hib, context, cfg, e, sessionId)) throw new GwtRpcException("You are not allowed to decide this excuse (" + cfg.excuseApprover.toLowerCase() + " approval).");
				if (!"PENDING".equals(e.getStatus())) throw new GwtRpcException("The excuse is already " + e.getStatus().toLowerCase() + ".");
				boolean approve = "APPROVE".equals(request.decision);
				if (!approve && (request.note == null || request.note.trim().isEmpty())) throw new GwtRpcException("Say why the excuse is rejected.");
				e.setStatus(approve ? "APPROVED" : "REJECTED"); e.setDecidedBy(who); e.setDecidedTs(new Date()); e.setDecisionNote(trim(request.note, 1000));
				hib.merge(e);
				int updated = 0;
				if (approve) {
					for (AttendanceRecord r: hib.createQuery("select r from AttendanceRecord r, AttendanceSession s where r.attendanceSessionId = s.uniqueId and s.sessionId = :s and r.studentId = :st " +
							"and r.status = 'ABSENT' and s.meetingDate >= :f and s.meetingDate <= :t", AttendanceRecord.class)
							.setParameter("s", sessionId).setParameter("st", e.getStudentId()).setParameter("f", e.getFromDate()).setParameter("t", e.getToDate()).list()) {
						r.setStatus(AttendanceService.EXCUSED); r.setSource("EXCUSE"); r.setNote(trim("Excuse " + e.getExcuseType() + " approved by " + who, 500)); hib.merge(r); updated++;
					}
				}
				Student st = hib.get(Student.class, e.getStudentId());
				log(hib, context, sessionId, approve ? "APPROVE_EXCUSE" : "REJECT_EXCUSE", (st == null ? "" : AttendanceService.name(st)) + " " + e.getExcuseType() + " " + AttendanceService.DATE.format(e.getFromDate()) + " - " + AttendanceService.DATE.format(e.getToDate()) + (approve ? ": " + updated + " absence(s) excused" : ": " + request.note));
				hib.flush(); tx.commit();
				response.message = approve ? "Approved; " + updated + " absence(s) changed to excused." : "Rejected.";
			} catch (RuntimeException ex) { tx.rollback(); throw ex; }
			op = "EXCUSES";
			break;
		}
		case "UPDATE_FOLLOWUP": {
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceFollowup f = hib.get(AttendanceFollowup.class, request.id);
				if (f == null || !f.getSessionId().equals(sessionId)) throw new GwtRpcException("Unknown follow-up case.");
				if (!manage && !AttendanceService.myClasses(hib, context, sessionId).contains(f.getClassId())) throw new GwtRpcException("You can update only the cases of your own classes.");
				if (request.status != null) f.setStatus(request.status);
				if (request.note != null) f.setNotes(trim(request.note, 2000));
				if (request.outcome != null) f.setOutcome(trim(request.outcome, 1000));
				f.setNextReview(day(request.nextReview)); f.setUpdatedBy(who); f.setUpdatedTs(new Date());
				hib.merge(f);
				log(hib, context, sessionId, "FOLLOWUP", "Case " + f.getUniqueId() + " " + f.getRuleType() + ": " + f.getStatus() + (request.outcome == null ? "" : "; outcome: " + request.outcome));
				hib.flush(); tx.commit();
			} catch (RuntimeException ex) { tx.rollback(); throw ex; }
			op = "FOLLOWUPS";
			break;
		}
		case "EVALUATE": {
			if (!manage) throw new GwtRpcException("Only attendance managers can run the follow-up rules for everyone.");
			Transaction tx = hib.beginTransaction();
			try {
				evaluate(hib, sessionId, cfg, null, response);
				log(hib, context, sessionId, "EVALUATE", response.message);
				hib.flush(); tx.commit();
			} catch (RuntimeException ex) { tx.rollback(); throw ex; }
			op = "FOLLOWUPS";
			break;
		}
		case "SAVE_CONFIG": {
			if (!manage) throw new GwtRpcException("Only attendance managers can change the settings.");
			Config c = request.config;
			if (c == null || c.minPct < 0 || c.minPct > 100 || (c.eligibilityPct != null && (c.eligibilityPct < 0 || c.eligibilityPct > 100)) || c.checkinMinutes < 1 || c.qrRotationSec < 5 || c.minSessions < 1)
				throw new GwtRpcException("Percentages must be 0-100, check-in at least 1 minute, QR rotation at least 5 seconds, minimum sessions at least 1.");
			Transaction tx = hib.beginTransaction();
			try {
				AttendanceService.saveConfig(hib, sessionId, c);
				log(hib, context, sessionId, "SETTINGS", "minimum " + c.minPct + "%, eligibility " + c.eligibilityPct + "%, late after " + c.lateMinutes + " min, check-in " + c.checkinMinutes + " min, QR " + (c.qrEnabled ? "on (" + c.qrRotationSec + " s)" : "off") + ", roll call " + (c.rollcallEnabled ? "on" : "off") + ", " + c.consecutiveAbsences + " consecutive absences, alerts to " + c.alertRecipients + ", excuses approved by " + c.excuseApprover);
				hib.flush(); tx.commit();
			} catch (RuntimeException ex) { tx.rollback(); throw ex; }
			response.config = AttendanceService.config(hib, sessionId);
			break;
		}
		default: break;
		}

		// ---- content
		if ("ROLL".equals(op) || "MARK".equals(op) || "OPEN_CHECKIN".equals(op) || "CLOSE_CHECKIN".equals(op) || "FINALISE".equals(op) || "REOPEN".equals(op) || "QR".equals(op)) {
			Meeting m = meeting(hib, request.meetingId);
			Class_ c = AttendanceService.classOf(m);
			check(hib, context, c, m);
			response.session = info(hib, context, sessionId, m, c);
			if (!"QR".equals(op)) response.roster = roster(hib, sessionId, cfg, m, c);
			AttendanceSession s = AttendanceService.session(hib, m.getUniqueId());
			if (s != null && response.session.checkinOpen) {
				long now = System.currentTimeMillis();
				response.qrToken = AttendanceService.token(s.getQrSecret(), m.getUniqueId(), AttendanceService.interval(cfg, now));
				long period = 1000L * Math.max(5, cfg.qrRotationSec);
				response.qrSecondsLeft = (int)((period - now % period) / 1000L);
			}
		}
		if ("EXCUSES".equals(op)) response.excuses = excuses(hib, context, cfg, sessionId);
		if ("FOLLOWUPS".equals(op)) response.followups = followups(hib, context, cfg, sessionId, manage);
		if ("REPORT".equals(op)) {
			if (!manage) throw new GwtRpcException("Reports need the attendance manager right.");
			response.report = report(hib, sessionId, cfg, request.dimension, day(request.fromDate), day(request.toDate));
		}
		if ("EXPORT".equals(op)) {
			if (!manage) throw new GwtRpcException("The export needs the attendance manager right.");
			export(hib, sessionId, cfg, day(request.fromDate), day(request.toDate), response);
			Transaction tx = hib.beginTransaction();
			try { log(hib, context, sessionId, "EXPORT", response.exportRows.size() + " rows " + request.fromDate + " - " + request.toDate); hib.flush(); tx.commit(); }
			catch (RuntimeException ex) { tx.rollback(); throw ex; }
		}
		if ("LOG".equals(op)) response.log = log(hib, sessionId);
		if ("EXCUSE_FILE".equals(op)) {
			AttendanceExcuse e = hib.get(AttendanceExcuse.class, request.id);
			if (e == null || !e.getSessionId().equals(sessionId)) throw new GwtRpcException("Unknown excuse.");
			if (!manage && !teachesStudent(hib, context, e, sessionId, AttendanceService.myClasses(hib, context, sessionId))) throw new GwtRpcException("You cannot see this document."); // same rule as the list: lecturers of the student
			Excuse x = new Excuse();
			x.id = e.getUniqueId(); x.fileName = e.getFileName(); x.fileType = e.getFileType();
			x.fileBase64 = (e.getFileContent() == null ? null : Base64.getEncoder().encodeToString(e.getFileContent()));
			response.excuse = x;
		}
		response.pendingExcuses = excuses(hib, context, cfg, sessionId).stream().filter(e -> e.canDecide).mapToInt(e -> 1).sum();
		if ("DASHBOARD".equals(op) || "RUN_REPORT".equals(op)) {
			if (!manage) throw new GwtRpcException("The dashboard and reports need the attendance manager right.");
			Date termStart = session.getSessionBeginDateTime(), termEnd = session.getClassesEndDateTime();
			if ("DASHBOARD".equals(op)) {
				Date d = (request.date == null || request.date.isEmpty() ? lastRecorded(hib, sessionId, termStart) : day(request.date));
				response.dashboard = AttendanceReports.dashboard(hib, sessionId, cfg, termStart, d, request.period, request.dimension, response.pendingExcuses);
			} else {
				Date from = (request.fromDate == null || request.fromDate.isEmpty() ? termStart : day(request.fromDate));
				Date to = (request.toDate == null || request.toDate.isEmpty() ? termEnd : day(request.toDate));
				if ("DAILY".equals(request.report) && request.date != null && !request.date.isEmpty()) to = day(request.date);
				response.table = AttendanceReports.run(hib, sessionId, cfg, request.report, from, to, request.dimension);
			}
		}
		response.openFollowups = (int)followups(hib, context, cfg, sessionId, manage).stream().filter(f -> !"CLOSED".equals(f.status)).count();
		return response;
	}

	// ------------------------------------------------------------------ helpers

	private static boolean eq(String a, String b) { return (a == null ? "" : a).equals(b == null ? "" : b); }
	static String trim(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }

	static Date day(String s) {
		if (s == null || s.isEmpty()) return null;
		try { return AttendanceService.DATE.parse(s); } catch (ParseException e) { throw new GwtRpcException("Invalid date " + s); }
	}

	private static Meeting meeting(org.hibernate.Session hib, Long id) {
		Meeting m = (id == null ? null : AttendanceService.meeting(hib, id));
		if (m == null || AttendanceService.classOf(m) == null) throw new GwtRpcException("Unknown class meeting.");
		return m;
	}

	private static void check(org.hibernate.Session hib, SessionContext context, Class_ c, Meeting m) {
		if (m == null || c == null) throw new GwtRpcException("Unknown class meeting.");
		if (!AttendanceService.canTake(hib, context, c, m.getMeetingDate()))
			throw new GwtRpcException("You do not teach or cover " + c.getClassLabel() + " on " + AttendanceService.DATE.format(m.getMeetingDate()) + ".");
	}

	private static Set<Long> studentsOf(List<Mark> marks) {
		Set<Long> ret = new HashSet<Long>();
		if (marks != null) for (Mark m: marks) ret.add(m.studentId);
		return ret;
	}

	static void evaluate(org.hibernate.Session hib, Long sessionId, Config cfg, Set<Long> students, AttendanceResponse response) {
		List<AttendanceFollowup> created = AttendanceService.evaluate(hib, sessionId, cfg, students);
		for (AttendanceFollowup f: created) {
			Class_ c = (f.getClassId() == null ? null : hib.get(Class_.class, f.getClassId()));
			f.setRecipients(AttendanceService.recipientNames(hib, cfg, c));
			hib.merge(f);
		}
		int sent = AttendanceService.notify(hib, cfg, created);
		if (!created.isEmpty()) response.message = created.size() + " follow-up case(s) raised" + (sent > 0 ? ", " + sent + " notification(s) emailed" : "") + ".";
		else if (response.message == null) response.message = "No new follow-up cases.";
	}

	static void log(org.hibernate.Session hib, SessionContext context, Long sessionId, String action, String detail) {
		ExamAllocationSupport.log(hib, context, sessionId, null, KIND, action, null, detail, false);
	}

	private static List<LogEntry> log(org.hibernate.Session hib, Long sessionId) {
		List<LogEntry> ret = new ArrayList<LogEntry>();
		for (ExamAllocationLog l: hib.createQuery("from ExamAllocationLog where sessionId = :s and kind = :k order by timeStamp desc, uniqueId desc", ExamAllocationLog.class)
				.setParameter("s", sessionId).setParameter("k", KIND).setMaxResults(300).list()) {
			LogEntry e = new LogEntry();
			e.timeStamp = l.getTimeStamp(); e.action = l.getAction(); e.detail = l.getDetail(); e.userName = l.getUserName();
			ret.add(e);
		}
		return ret;
	}

	/** The last day with finalised attendance, or the start of term. */
	static Date lastRecorded(org.hibernate.Session hib, Long sessionId, Date fallback) {
		Date d = hib.createQuery("select max(meetingDate) from AttendanceSession where sessionId = :s and status = 'FINALISED'", Date.class).setParameter("s", sessionId).uniqueResult();
		return (d == null ? fallback : d);
	}

	private static String firstTeachingDay(org.hibernate.Session hib, Long sessionId, String fallback) {
		Date d = hib.createQuery("select min(m.meetingDate) from Meeting m, ClassEvent e where m.event = e and " +
				"e.clazz.schedulingSubpart.instrOfferingConfig.instructionalOffering.session.uniqueId = :s", Date.class).setParameter("s", sessionId).uniqueResult();
		return d == null ? fallback : AttendanceService.DATE.format(d);
	}

	private static List<SessionInfo> sessions(org.hibernate.Session hib, SessionContext context, Long sessionId, Date day, boolean all) {
		List<SessionInfo> ret = new ArrayList<SessionInfo>();
		if (day == null) return ret;
		Set<Long> mine = AttendanceService.myClasses(hib, context, sessionId);
		String me = context.getUser().getExternalUserId();
		for (Meeting m: AttendanceService.meetings(hib, sessionId, day)) {
			Class_ c = AttendanceService.classOf(m);
			boolean relief = false;
			if (!all && !mine.contains(c.getUniqueId())) {
				ReliefAssignment r = AttendanceService.relief(hib, c, day);
				if (r == null || me == null || !me.equals(r.getReliefUid())) continue;
				relief = true;
			}
			SessionInfo s = info(hib, context, sessionId, m, c);
			s.relief = relief || s.relief;
			ret.add(s);
		}
		return ret;
	}

	static SessionInfo info(org.hibernate.Session hib, SessionContext context, Long sessionId, Meeting m, Class_ c) {
		SessionInfo s = new SessionInfo();
		s.meetingId = m.getUniqueId(); s.classId = c.getUniqueId(); s.className = c.getClassLabel();
		try { s.course = c.getSchedulingSubpart().getControllingCourseOffering().getCourseName(); } catch (Exception e) {}
		try { s.itype = c.getSchedulingSubpart().getItypeDesc().trim(); } catch (Exception e) {}
		s.date = AttendanceService.DATE.format(m.getMeetingDate()); s.time = AttendanceService.time(m.getStartPeriod(), m.getStopPeriod());
		s.room = AttendanceService.location(hib, sessionId, m.getLocationPermanentId());
		s.instructors = AttendanceService.instructorNames(c);
		ReliefAssignment r = AttendanceService.relief(hib, c, m.getMeetingDate());
		if (r != null) { s.relief = true; s.takerName = r.getReliefName() + " (relief)"; }
		s.enrolled = AttendanceService.roster(hib, c.getUniqueId()).size();
		s.canTake = (context == null || AttendanceService.canTake(hib, context, c, m.getMeetingDate()));
		AttendanceSession a = AttendanceService.session(hib, m.getUniqueId());
		if (a != null) {
			s.attendanceSessionId = a.getUniqueId(); s.status = a.getStatus();
			if (r == null) s.takerName = a.getTakerName();
			for (AttendanceRecord rec: AttendanceService.records(hib, a.getUniqueId())) {
				switch (rec.getStatus()) {
				case "PRESENT": s.present++; break;
				case "LATE": s.late++; break;
				case "ABSENT": s.absent++; break;
				default: s.excused++;
				}
			}
			s.checkinOpen = !"FINALISED".equals(a.getStatus()) && a.getCheckinUntil() != null && a.getCheckinUntil().after(new Date());
			s.checkinUntil = a.getCheckinUntil();
		}
		return s;
	}

	private static List<RosterEntry> roster(org.hibernate.Session hib, Long sessionId, Config cfg, Meeting m, Class_ c) {
		List<RosterEntry> ret = new ArrayList<RosterEntry>();
		List<Student> students = AttendanceService.roster(hib, c.getUniqueId());
		Set<Long> ids = new HashSet<Long>();
		for (Student s: students) ids.add(s.getUniqueId());
		Map<Long, AttendanceRecord> recs = new HashMap<Long, AttendanceRecord>();
		AttendanceSession a = AttendanceService.session(hib, m.getUniqueId());
		if (a != null) for (AttendanceRecord r: AttendanceService.records(hib, a.getUniqueId())) recs.put(r.getStudentId(), r);
		List<AttendanceExcuse> excuses = AttendanceService.approvedExcuses(hib, sessionId, ids);
		Map<String, int[]> counts = AttendanceService.counts(hib, sessionId, ids);
		for (Student s: students) {
			RosterEntry e = new RosterEntry();
			e.studentId = s.getUniqueId(); e.externalId = s.getExternalUniqueId(); e.name = AttendanceService.name(s);
			AttendanceRecord r = recs.get(s.getUniqueId());
			if (r != null) { e.status = r.getStatus(); e.source = r.getSource(); e.note = r.getNote(); e.markedTs = r.getMarkedTs(); e.markedBy = r.getMarkedBy(); }
			for (AttendanceExcuse x: excuses) if (x.getStudentId().equals(s.getUniqueId()) && AttendanceService.covers(x, m.getMeetingDate())) e.excused = true;
			int[] cnt = counts.get(s.getUniqueId() + ":" + c.getUniqueId());
			if (cnt != null) e.ratePct = (int)Math.round(AttendanceService.rate(cfg, cnt));
			ret.add(e);
		}
		return ret;
	}

	static boolean canDecide(org.hibernate.Session hib, SessionContext context, Config cfg, AttendanceExcuse e, Long sessionId) {
		if (context.hasPermission(Right.AttendanceManage)) return true;
		if (!"LECTURER".equalsIgnoreCase(cfg.excuseApprover)) return false;
		return teachesStudent(hib, context, e, sessionId, AttendanceService.myClasses(hib, context, sessionId));
	}

	/** True when the user takes attendance and the excuse's student is enrolled in one of the user's classes. */
	private static boolean teachesStudent(org.hibernate.Session hib, SessionContext context, AttendanceExcuse e, Long sessionId, Set<Long> mine) {
		if (!context.hasPermission(Right.AttendanceTake) || mine == null || mine.isEmpty()) return false;
		Long n = hib.createQuery("select count(x) from StudentClassEnrollment x where x.student.uniqueId = :s and x.clazz.uniqueId in :c", Long.class)
				.setParameter("s", e.getStudentId()).setParameterList("c", mine).uniqueResult();
		return n != null && n > 0;
	}

	/**
	 * Certificates and leave listed on the Take Attendance page. Attendance managers see all of them. A lecturer sees
	 * those of students enrolled in their classes, whoever approves them (Settings): the lecturer can then take the
	 * excuse into account, but only gets Approve / Reject when lecturers are the approvers.
	 */
	private static List<Excuse> excuses(org.hibernate.Session hib, SessionContext context, Config cfg, Long sessionId) {
		List<Excuse> ret = new ArrayList<Excuse>();
		boolean manage = context.hasPermission(Right.AttendanceManage);
		Set<Long> mine = (manage ? null : AttendanceService.myClasses(hib, context, sessionId));
		for (AttendanceExcuse e: hib.createQuery("from AttendanceExcuse where sessionId = :s order by submittedTs desc", AttendanceExcuse.class).setParameter("s", sessionId).list()) {
			boolean visible = manage || teachesStudent(hib, context, e, sessionId, mine);
			if (!visible) continue;
			boolean decide = manage || ("LECTURER".equalsIgnoreCase(cfg.excuseApprover) && visible);
			ret.add(toExcuse(hib, e, decide && "PENDING".equals(e.getStatus())));
		}
		return ret;
	}

	static Excuse toExcuse(org.hibernate.Session hib, AttendanceExcuse e, boolean canDecide) {
		Excuse x = new Excuse();
		Student s = hib.get(Student.class, e.getStudentId());
		x.id = e.getUniqueId(); x.studentId = e.getStudentId(); x.studentName = (s == null ? null : AttendanceService.name(s)); x.studentExternalId = (s == null ? null : s.getExternalUniqueId());
		x.type = e.getExcuseType(); x.note = e.getNote(); x.fileName = e.getFileName(); x.fileType = e.getFileType(); x.hasFile = e.getFileName() != null;
		x.status = e.getStatus(); x.decidedBy = e.getDecidedBy(); x.decisionNote = e.getDecisionNote();
		x.fromDate = e.getFromDate(); x.toDate = e.getToDate(); x.submittedTs = e.getSubmittedTs(); x.decidedTs = e.getDecidedTs();
		x.canDecide = canDecide;
		return x;
	}

	private static List<Followup> followups(org.hibernate.Session hib, SessionContext context, Config cfg, Long sessionId, boolean manage) {
		Set<Long> mine = (manage ? null : AttendanceService.myClasses(hib, context, sessionId));
		List<AttendanceFollowup> list = hib.createQuery("from AttendanceFollowup where sessionId = :s order by createdTs desc", AttendanceFollowup.class).setParameter("s", sessionId).list();
		Set<Long> students = new HashSet<Long>();
		for (AttendanceFollowup f: list) students.add(f.getStudentId());
		Map<String, int[]> counts = AttendanceService.counts(hib, sessionId, students);
		List<Followup> ret = new ArrayList<Followup>();
		for (AttendanceFollowup f: list) {
			if (mine != null && !mine.contains(f.getClassId())) continue;
			Followup x = new Followup();
			Student s = hib.get(Student.class, f.getStudentId());
			Class_ c = (f.getClassId() == null ? null : hib.get(Class_.class, f.getClassId()));
			x.id = f.getUniqueId(); x.studentId = f.getStudentId(); x.classId = f.getClassId();
			x.studentName = (s == null ? null : AttendanceService.name(s)); x.studentExternalId = (s == null ? null : s.getExternalUniqueId());
			x.className = (c == null ? null : c.getClassLabel()); x.rule = f.getRuleType(); x.detail = f.getDetail(); x.status = f.getStatus();
			x.recipients = f.getRecipients(); x.notes = f.getNotes(); x.outcome = f.getOutcome(); x.updatedBy = f.getUpdatedBy();
			x.createdTs = f.getCreatedTs(); x.nextReview = f.getNextReview(); x.updatedTs = f.getUpdatedTs();
			int[] cnt = counts.get(f.getStudentId() + ":" + f.getClassId());
			if (cnt != null) x.ratePct = (int)Math.round(AttendanceService.rate(cfg, cnt));
			ret.add(x);
		}
		return ret;
	}

	// ------------------------------------------------------------------ reports and export

	static List<Object[]> finalisedRecords(org.hibernate.Session hib, Long sessionId, Date from, Date to) {
		String q = "select s, r from AttendanceSession s, AttendanceRecord r where r.attendanceSessionId = s.uniqueId and s.sessionId = :s and s.status = 'FINALISED'" +
				(from == null ? "" : " and s.meetingDate >= :f") + (to == null ? "" : " and s.meetingDate <= :t");
		org.hibernate.query.Query<Object[]> query = hib.createQuery(q, Object[].class).setParameter("s", sessionId);
		if (from != null) query.setParameter("f", from);
		if (to != null) query.setParameter("t", to);
		return query.list();
	}

	static List<ReportRow> report(org.hibernate.Session hib, Long sessionId, Config cfg, String dimension, Date from, Date to) {
		String dim = (dimension == null ? "CLASS" : dimension);
		Map<String, ReportRow> rows = new TreeMap<String, ReportRow>();
		Map<String, Set<Long>> sessionsPerKey = new HashMap<String, Set<Long>>();
		Map<Long, Class_> classes = new HashMap<Long, Class_>();
		Map<Long, Student> students = new HashMap<Long, Student>();
		String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
		Map<Long, String> rooms = new HashMap<Long, String>();
		for (Object[] o: finalisedRecords(hib, sessionId, from, to)) {
			AttendanceSession s = (AttendanceSession)o[0]; AttendanceRecord r = (AttendanceRecord)o[1];
			Class_ c = classes.computeIfAbsent(s.getClassId(), id -> hib.get(Class_.class, id));
			List<String[]> keys = new ArrayList<String[]>(); // key, label
			switch (dim) {
			case "SCHOOL": keys.add(new String[] {dept(c), dept(c)}); break;
			case "COURSE": String course = course(c); keys.add(new String[] {course, course}); break;
			case "ITYPE": String it = c.getSchedulingSubpart().getItypeDesc().trim(); keys.add(new String[] {it, it}); break;
			case "STUDENT":
				Student st = students.computeIfAbsent(r.getStudentId(), id -> hib.get(Student.class, id));
				keys.add(new String[] {String.valueOf(r.getStudentId()), st == null ? "?" : AttendanceService.name(st) + " (" + st.getExternalUniqueId() + ")"}); break;
			case "INSTRUCTOR":
				String names = AttendanceService.instructorNames(c);
				for (String n: names.isEmpty() ? new String[] {"(no instructor)"} : names.split("; ")) keys.add(new String[] {n, n});
				break;
			case "WEEK":
				Calendar cal = Calendar.getInstance(); cal.setTime(s.getMeetingDate()); cal.setFirstDayOfWeek(Calendar.MONDAY);
				cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				String wk = AttendanceService.DATE.format(cal.getTime()); keys.add(new String[] {wk, "Week of " + wk}); break;
			case "WEEKDAY":
				Calendar cd = Calendar.getInstance(); cd.setTime(s.getMeetingDate());
				int dw = cd.get(Calendar.DAY_OF_WEEK); keys.add(new String[] {String.valueOf((dw + 5) % 7), days[dw - 1]}); break;
			case "TIME":
				String t = AttendanceService.time(s.getStartPeriod(), s.getStopPeriod()).split(" - ")[0];
				keys.add(new String[] {String.format("%04d", s.getStartPeriod()), t}); break;
			case "SUBJECT": String subj = subject(c); keys.add(new String[] {subj, subj}); break;
			case "MONTH":
				String mo = AttendanceService.DATE.format(s.getMeetingDate()).substring(0, 7);
				keys.add(new String[] {mo, new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH).format(s.getMeetingDate())}); break;
			case "ROOM":
				String room = (s.getLocationPermId() == null ? "(no room)" : rooms.computeIfAbsent(s.getLocationPermId(), p -> {
					String n = AttendanceService.location(hib, sessionId, p); return n == null || n.isEmpty() ? "(no room)" : n; }));
				keys.add(new String[] {room, room}); break;
			case "DATE":
				Calendar cdt = Calendar.getInstance(); cdt.setTime(s.getMeetingDate());
				String dt = AttendanceService.DATE.format(s.getMeetingDate()); keys.add(new String[] {dt, days[cdt.get(Calendar.DAY_OF_WEEK) - 1] + " " + dt}); break;
			default: keys.add(new String[] {c.getClassLabel(), c.getClassLabel()});
			}
			for (String[] k: keys) {
				ReportRow row = rows.get(k[0]);
				if (row == null) { row = new ReportRow(); row.key = k[0]; row.label = k[1]; rows.put(k[0], row); sessionsPerKey.put(k[0], new HashSet<Long>()); }
				row.records++;
				sessionsPerKey.get(k[0]).add(s.getUniqueId());
				switch (r.getStatus()) { case "PRESENT": row.present++; break; case "LATE": row.late++; break; case "ABSENT": row.absent++; break; default: row.excused++; }
			}
		}
		List<ReportRow> ret = new ArrayList<ReportRow>(rows.values());
		for (ReportRow row: ret) {
			row.sessions = sessionsPerKey.get(row.key).size();
			row.ratePct = Math.round(10.0 * AttendanceService.rate(cfg, new int[] {row.present, row.late, row.absent, row.excused})) / 10.0;
		}
		if ("STUDENT".equals(dim) || "CLASS".equals(dim) || "COURSE".equals(dim) || "INSTRUCTOR".equals(dim))
			Collections.sort(ret, new Comparator<ReportRow>() { @Override public int compare(ReportRow a, ReportRow b) { return Double.compare(a.ratePct, b.ratePct); } });
		return ret;
	}

	static String dept(Class_ c) {
		try { return c.getSchedulingSubpart().getControllingDept().getDeptCode() + " " + c.getSchedulingSubpart().getControllingDept().getName(); } catch (Exception e) { return "?"; }
	}

	static String subject(Class_ c) {
		try { return c.getSchedulingSubpart().getControllingCourseOffering().getSubjectArea().getSubjectAreaAbbreviation(); } catch (Exception e) { return "?"; }
	}

	static String course(Class_ c) {
		try { return c.getSchedulingSubpart().getControllingCourseOffering().getCourseName(); } catch (Exception e) { return "?"; }
	}

	private static void export(org.hibernate.Session hib, Long sessionId, Config cfg, Date from, Date to, AttendanceResponse response) {
		List<String> cols = new ArrayList<String>();
		for (String c: (cfg.exportColumns == null || cfg.exportColumns.trim().isEmpty() ? new Config().exportColumns : cfg.exportColumns).split(","))
			if (!c.trim().isEmpty()) cols.add(c.trim().toUpperCase());
		response.exportColumns = cols;
		response.exportRows = new ArrayList<List<String>>();
		Map<Long, Class_> classes = new HashMap<Long, Class_>();
		Map<Long, Student> students = new HashMap<Long, Student>();
		Map<Long, String> rooms = new HashMap<Long, String>();
		for (Object[] o: finalisedRecords(hib, sessionId, from, to)) {
			AttendanceSession s = (AttendanceSession)o[0]; AttendanceRecord r = (AttendanceRecord)o[1];
			Class_ c = classes.computeIfAbsent(s.getClassId(), id -> hib.get(Class_.class, id));
			Student st = students.computeIfAbsent(r.getStudentId(), id -> hib.get(Student.class, id));
			String room = rooms.computeIfAbsent(s.getLocationPermId() == null ? -1l : s.getLocationPermId(), id -> AttendanceService.location(hib, sessionId, id < 0 ? null : id));
			List<String> row = new ArrayList<String>();
			for (String col: cols) {
				switch (col) {
				case "STUDENT_ID": row.add(st == null ? "" : st.getExternalUniqueId()); break;
				case "STUDENT_NAME": row.add(st == null ? "" : AttendanceService.name(st)); break;
				case "COURSE": row.add(course(c)); break;
				case "CLASS": row.add(c.getClassLabel()); break;
				case "SCHOOL": row.add(dept(c)); break;
				case "DATE": row.add(AttendanceService.DATE.format(s.getMeetingDate())); break;
				case "START": row.add(AttendanceService.time(s.getStartPeriod(), s.getStopPeriod()).split(" - ")[0]); break;
				case "END": row.add(AttendanceService.time(s.getStartPeriod(), s.getStopPeriod()).split(" - ")[1]); break;
				case "ROOM": row.add(room); break;
				case "STATUS": row.add(r.getStatus()); break;
				case "SOURCE": row.add(r.getSource()); break;
				case "EXCUSE": row.add(AttendanceService.EXCUSED.equals(r.getStatus()) ? (r.getNote() == null ? "Y" : r.getNote()) : ""); break;
				case "MARKED_BY": row.add(r.getMarkedBy() == null ? "" : r.getMarkedBy()); break;
				default: row.add("");
				}
			}
			response.exportRows.add(row);
		}
	}
}
