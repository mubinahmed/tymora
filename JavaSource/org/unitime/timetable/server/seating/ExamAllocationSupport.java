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
package org.unitime.timetable.server.seating;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.AllocationStatus;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.IdName;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.LogEntry;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.ExamAllocationLog;
import org.unitime.timetable.model.ExamAllocationStatus;
import org.unitime.timetable.model.ExamSeat;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.InvigilationAssignment;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Review / approval / publication state and audit trail shared by seat allocation (kind SEATS) and
 * invigilation (kind INVIGILATION). Status: DRAFT -> APPROVED (approver; no errors unless overridden)
 * -> PUBLISHED. Any change after approval returns the plan to DRAFT; the publication time is kept so
 * changes made after publication can be reported.
 */
public class ExamAllocationSupport {
	public static final String SEATS = "SEATS", INVIGILATION = "INVIGILATION";

	public static List<IdName> examTypes(SessionContext context) {
		List<IdName> ret = new ArrayList<IdName>();
		for (ExamType t: ExamType.findAllApplicable(context.getUser(), DepartmentStatusType.Status.ExamView, DepartmentStatusType.Status.ExamTimetable))
			ret.add(new IdName(t.getUniqueId(), t.getLabel(), null));
		return ret;
	}

	public static Long examType(SessionContext context, Long requested, List<IdName> types) {
		if (requested != null) for (IdName t: types) if (t.id.equals(requested)) return requested;
		Long last = (Long)context.getAttribute("Exam.Type");
		if (last != null) for (IdName t: types) if (t.id.equals(last)) return last;
		return types.isEmpty() ? null : types.get(0).id;
	}

	public static ExamAllocationStatus status(org.hibernate.Session hibSession, Long sessionId, Long examTypeId, String kind, boolean create) {
		ExamAllocationStatus s = hibSession.createQuery("from ExamAllocationStatus where sessionId = :s and examTypeId = :t and kind = :k", ExamAllocationStatus.class)
				.setParameter("s", sessionId).setParameter("t", examTypeId).setParameter("k", kind).setMaxResults(1).uniqueResult();
		if (s == null && create) {
			s = new ExamAllocationStatus();
			s.setSessionId(sessionId); s.setExamTypeId(examTypeId); s.setKind(kind); s.setStatus("DRAFT"); s.setVersion(0);
			hibSession.persist(s);
		}
		return s;
	}

	public static AllocationStatus toStatus(org.hibernate.Session hibSession, SessionContext context, Long sessionId, Long examTypeId, String kind, Right edit) {
		AllocationStatus ret = new AllocationStatus();
		ExamAllocationStatus s = status(hibSession, sessionId, examTypeId, kind, false);
		if (s != null) {
			ret.status = s.getStatus(); ret.approvedBy = s.getApprovedBy(); ret.approvedTs = s.getApprovedTs();
			ret.publishedBy = s.getPublishedBy(); ret.publishedTs = s.getPublishedTs(); ret.note = s.getNote();
			ret.version = (s.getVersion() == null ? 0 : s.getVersion());
			if (s.getPublishedTs() != null) {
				Long n = hibSession.createQuery("select count(l) from ExamAllocationLog l where l.sessionId = :s and l.examTypeId = :t and l.kind = :k and l.timeStamp > :ts and l.action not in ('APPROVE', 'PUBLISH', 'REOPEN')", Long.class)
						.setParameter("s", sessionId).setParameter("t", examTypeId).setParameter("k", kind).setParameter("ts", s.getPublishedTs()).uniqueResult();
				ret.changesSincePublication = (n == null ? 0 : n.intValue());
			}
		}
		ret.canApprove = context.hasPermission(Right.AssignmentChangeApprove);
		ret.canEdit = context.hasPermission(edit);
		return ret;
	}

	/** Record an action (call inside a transaction). Changes return an approved / published plan to DRAFT. */
	public static void log(org.hibernate.Session hibSession, SessionContext context, Long sessionId, Long examTypeId, String kind, String action, Long periodId, String detail, boolean change) {
		ExamAllocationLog l = new ExamAllocationLog();
		l.setSessionId(sessionId); l.setExamTypeId(examTypeId); l.setKind(kind); l.setAction(action); l.setExamPeriodId(periodId);
		l.setDetail(detail == null ? null : detail.length() > 2000 ? detail.substring(0, 1997) + "..." : detail);
		l.setUserId(context.getUser().getExternalUserId()); l.setUserName(context.getUser().getName()); l.setTimeStamp(new Date());
		hibSession.persist(l);
		if (change) {
			ExamAllocationStatus s = status(hibSession, sessionId, examTypeId, kind, true);
			if (!"DRAFT".equals(s.getStatus())) s.setStatus("DRAFT");
			hibSession.merge(s);
		}
	}

	/** APPROVE, PUBLISH or REOPEN. errors = number of mandatory problems. Returns a message. */
	public static String transition(org.hibernate.Session hibSession, SessionContext context, Long sessionId, Long examTypeId, String kind, String op, int errors, boolean override, String note) {
		if (!context.hasPermission(Right.AssignmentChangeApprove))
			throw new GwtRpcException("Only approvers can approve, publish or reopen the " + (SEATS.equals(kind) ? "seat allocation" : "invigilation plan") + ".");
		Transaction tx = hibSession.beginTransaction();
		try {
			ExamAllocationStatus s = status(hibSession, sessionId, examTypeId, kind, true);
			String who = context.getUser().getName();
			String msg;
			if ("APPROVE".equals(op)) {
				if (errors > 0 && !override) throw new GwtRpcException(errors + " mandatory problem(s) must be fixed first (or approve with override).");
				s.setStatus("APPROVED"); s.setApprovedBy(who); s.setApprovedTs(new Date());
				msg = "Approved" + (errors > 0 ? " with override of " + errors + " problem(s)" : "") + ".";
			} else if ("PUBLISH".equals(op)) {
				if (!"APPROVED".equals(s.getStatus()) && !override) throw new GwtRpcException("Approve the plan before publishing it.");
				if (errors > 0 && !override) throw new GwtRpcException(errors + " mandatory problem(s) must be fixed first (or publish with override).");
				s.setStatus("PUBLISHED"); s.setPublishedBy(who); s.setPublishedTs(new Date());
				s.setVersion((s.getVersion() == null ? 0 : s.getVersion()) + 1);
				msg = "Published (version " + s.getVersion() + ").";
			} else if ("REOPEN".equals(op)) {
				s.setStatus("DRAFT");
				msg = "Reopened for changes. Students and invigilators keep seeing the current plan; publish again to notify them of the changes.";
			} else throw new GwtRpcException("Unknown operation " + op);
			if (note != null && !note.trim().isEmpty()) s.setNote(note.trim());
			hibSession.merge(s);
			log(hibSession, context, sessionId, examTypeId, kind, op, null, msg + (note == null || note.trim().isEmpty() ? "" : " Note: " + note.trim()), false);
			hibSession.flush();
			tx.commit();
			return msg;
		} catch (RuntimeException e) {
			tx.rollback();
			throw e;
		}
	}

	public static List<LogEntry> logEntries(org.hibernate.Session hibSession, ExamPlanData data, String kind, int limit) {
		List<LogEntry> ret = new ArrayList<LogEntry>();
		for (ExamAllocationLog l: hibSession.createQuery("from ExamAllocationLog where sessionId = :s and examTypeId = :t and kind = :k order by timeStamp desc, uniqueId desc", ExamAllocationLog.class)
				.setParameter("s", data.sessionId).setParameter("t", data.examTypeId).setParameter("k", kind).setMaxResults(limit).list()) {
			LogEntry e = new LogEntry();
			e.timeStamp = l.getTimeStamp(); e.action = l.getAction(); e.detail = l.getDetail(); e.userName = l.getUserName();
			e.period = (l.getExamPeriodId() == null ? null : data.periodName(l.getExamPeriodId()));
			ret.add(e);
		}
		return ret;
	}

	/**
	 * A seat is stale when its examination is no longer assigned to the seat's period, the student no longer takes
	 * the examination, or the room is no longer used in that period (an exam room or a separate room of a special
	 * arrangement). This happens when an examination is moved after seats were allocated (assignment dialog,
	 * approved change request, examination solver).
	 */
	public static boolean isStale(ExamPlanData data, ExamSeat s, Map<Long, Set<Long>> roomsByPeriod) {
		ExamPlanData.ExamData e = data.examById.get(s.getExamId());
		if (e == null || !e.periodId.equals(s.getExamPeriodId())) return true;
		if (!e.studentIds.contains(s.getStudentId())) return true;
		return !rooms(data, s.getExamPeriodId(), roomsByPeriod).contains(s.getLocationId());
	}

	/** A duty is stale when its period has no examinations any more, or its room is no longer used in the period. Reserves have no room. */
	public static boolean isStale(ExamPlanData data, InvigilationAssignment a, Map<Long, Set<Long>> roomsByPeriod) {
		if (data.exams(a.getExamPeriodId()).isEmpty()) return true;
		if (a.getLocationId() == null) return false;
		return !rooms(data, a.getExamPeriodId(), roomsByPeriod).contains(a.getLocationId());
	}

	private static Set<Long> rooms(ExamPlanData data, Long periodId, Map<Long, Set<Long>> roomsByPeriod) {
		Set<Long> ret = roomsByPeriod.get(periodId);
		if (ret == null) {
			ret = new HashSet<Long>();
			for (Location l: data.rooms(periodId)) ret.add(l.getUniqueId());
			roomsByPeriod.put(periodId, ret);
		}
		return ret;
	}

	/**
	 * Remove the stale seats (kind SEATS) or duties (kind INVIGILATION) of the examination type, including locked ones,
	 * and record it as a change, so that an approved or published plan returns to DRAFT and the removal shows in the
	 * history and in the changes since publication. Returns the number removed.
	 */
	public static int removeStale(org.hibernate.Session hibSession, SessionContext context, ExamPlanData data, String kind) {
		Map<Long, Set<Long>> roomsByPeriod = new HashMap<Long, Set<Long>>();
		Map<Long, Integer> removedByPeriod = new TreeMap<Long, Integer>();
		List<Object> stale = new ArrayList<Object>();
		if (SEATS.equals(kind)) {
			for (ExamSeat s: hibSession.createQuery("from ExamSeat where sessionId = :s and examTypeId = :t", ExamSeat.class)
					.setParameter("s", data.sessionId).setParameter("t", data.examTypeId).list())
				if (isStale(data, s, roomsByPeriod)) { stale.add(s); removedByPeriod.merge(s.getExamPeriodId(), 1, Integer::sum); }
		} else {
			for (InvigilationAssignment a: hibSession.createQuery("from InvigilationAssignment where sessionId = :s and examTypeId = :t", InvigilationAssignment.class)
					.setParameter("s", data.sessionId).setParameter("t", data.examTypeId).list())
				if (isStale(data, a, roomsByPeriod)) { stale.add(a); removedByPeriod.merge(a.getExamPeriodId(), 1, Integer::sum); }
		}
		if (stale.isEmpty()) return 0;
		Transaction tx = hibSession.beginTransaction();
		try {
			for (Object o: stale) hibSession.remove(o);
			for (Map.Entry<Long, Integer> e: removedByPeriod.entrySet())
				log(hibSession, context, data.sessionId, data.examTypeId, kind, "REMOVE_STALE", e.getKey(),
						e.getValue() + (SEATS.equals(kind) ? " seat(s)" : " duty(ies)") + " removed: the examinations of this period or their rooms changed after allocation.", true);
			hibSession.flush();
			tx.commit();
		} catch (RuntimeException e) {
			tx.rollback();
			throw e;
		}
		return stale.size();
	}

	/** Whether the plan of this kind is published (seats / duties are visible to students / invigilators). */
	public static boolean isPublished(org.hibernate.Session hibSession, Long sessionId, Long examTypeId, String kind) {
		ExamAllocationStatus s = status(hibSession, sessionId, examTypeId, kind, false);
		return s != null && s.getPublishedTs() != null;
	}
}
