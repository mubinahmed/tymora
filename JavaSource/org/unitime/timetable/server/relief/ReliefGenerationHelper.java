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
package org.unitime.timetable.server.relief;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.ReliefConfiguration;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.dao.ReliefAssignmentDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.StaffAbsenceDAO;

/**
 * The relief-generation engine. Given an academic session and a calendar date, it
 * finds every lesson made vacant by an approved {@link StaffAbsence} on that date,
 * computes the free teachers who could cover each one, ranks them by the session's
 * {@link ReliefConfiguration} policy (academic continuity, weekly workload fairness,
 * exemptions, availability) and writes a {@link ReliefAssignment} per lesson.
 *
 * The allocation is a deterministic greedy pass — fast (well under the ITQ's five
 * minute bar) and explainable — not a global optimiser. Regeneration is idempotent:
 * it clears previous automatic assignments for the date but preserves any an
 * administrator set manually on the oversight board.
 */
public class ReliefGenerationHelper {

	/** A ranked relief candidate for a single vacated lesson. */
	public static class Candidate {
		public String uid;
		public String name;
		public boolean sameDept;
		public int weekLoad;

		public Candidate(String uid, String name, boolean sameDept, int weekLoad) {
			this.uid = uid; this.name = name; this.sameDept = sameDept; this.weekLoad = weekLoad;
		}
	}

	/**
	 * Reusable, precomputed view of a session/date used both to generate assignments
	 * and to list candidate teachers for the oversight board. Built once per request.
	 */
	public static class Context {
		public final Long sessionId;
		public final Date date;
		public final ReliefConfiguration cfg;
		public final Set<String> absentUids = new HashSet<String>();
		/** uid -> display name of every instructor in the session. */
		public final Map<String, String> names = new HashMap<String, String>();
		/** uid -> departments the instructor belongs to (for continuity ranking). */
		public final Map<String, Set<Long>> depts = new HashMap<String, Set<Long>>();
		/** uid -> the instructor rows for that person in the session (for availability). */
		public final Map<String, List<DepartmentalInstructor>> instructors = new HashMap<String, List<DepartmentalInstructor>>();
		/** uids that teach at least one class in the session (i.e. not "non-teaching"). */
		public final Set<String> teachingUids = new HashSet<String>();
		/** uid -> the instructor's busy teaching intervals [start,stop) on the date. */
		public final Map<String, List<int[]>> busy = new HashMap<String, List<int[]>>();
		/** uid -> relief periods already assigned to them in the containing week. */
		public final Map<String, Integer> weekLoad = new HashMap<String, Integer>();
		/** uid -> intervals reserved during this run (avoids double-booking a reliever). */
		public final Map<String, List<int[]>> reservedThisRun = new HashMap<String, List<int[]>>();

		Context(Long sessionId, Date date, ReliefConfiguration cfg) {
			this.sessionId = sessionId; this.date = date; this.cfg = cfg;
		}
	}

	/** Build the reusable context (instructor pool, busy map, weekly loads) for a session/date. */
	@SuppressWarnings("unchecked")
	public static Context buildContext(Long sessionId, Date date, Session hibSession) {
		ReliefConfiguration cfg = ReliefConfiguration.getOrDefault(hibSession, sessionId);
		Context ctx = new Context(sessionId, date, cfg);

		// Absent staff on this date must never be picked as relievers.
		for (StaffAbsence a: StaffAbsenceDAO.getInstance().findApprovedOnDate(hibSession, sessionId, date))
			ctx.absentUids.add(a.getExternalUniqueId());

		// Instructor pool: names + departments.
		for (DepartmentalInstructor di: DepartmentalInstructor.findInstructorsForSession(sessionId)) {
			String uid = di.getExternalUniqueId();
			if (uid == null || uid.isEmpty()) continue;
			ctx.names.put(uid, di.nameLastNameFirst());
			if (di.getDepartment() != null) {
				Set<Long> ds = ctx.depts.get(uid);
				if (ds == null) { ds = new HashSet<Long>(); ctx.depts.put(uid, ds); }
				ds.add(di.getDepartment().getUniqueId());
			}
			List<DepartmentalInstructor> is = ctx.instructors.get(uid);
			if (is == null) { is = new ArrayList<DepartmentalInstructor>(); ctx.instructors.put(uid, is); }
			is.add(di);
		}

		// Teaching staff (at least one class in the session).
		for (String uid: (List<String>) hibSession.createQuery(
				"select distinct ci.instructor.externalUniqueId from ClassInstructor ci " +
				"where ci.instructor.department.session.uniqueId = :sessionId and ci.instructor.externalUniqueId is not null", String.class)
				.setParameter("sessionId", sessionId).list())
			ctx.teachingUids.add(uid);

		// Teaching busy intervals on the date (one query for all instructors).
		for (Object[] row: (List<Object[]>) hibSession.createQuery(
				"select distinct ci.instructor.externalUniqueId, m.startPeriod, m.stopPeriod " +
				"from ClassEvent e inner join e.meetings m inner join e.clazz.classInstructors ci " +
				"where ci.instructor.department.session.uniqueId = :sessionId " +
				"and m.meetingDate = :date and m.approvalStatus = 1", Object[].class)
				.setParameter("sessionId", sessionId).setParameter("date", date).list()) {
			String uid = (String) row[0];
			if (uid == null) continue;
			addInterval(ctx.busy, uid, ((Number) row[1]).intValue(), ((Number) row[2]).intValue());
		}

		// Existing relief load in the containing week (seeds fairness + cap enforcement).
		Date[] week = weekBounds(date);
		for (Object[] row: (List<Object[]>) hibSession.createQuery(
				"select r.reliefUid, count(r) from ReliefAssignment r where r.session.uniqueId = :sessionId " +
				"and r.reliefUid is not null and r.meetingDate >= :from and r.meetingDate <= :to group by r.reliefUid", Object[].class)
				.setParameter("sessionId", sessionId).setParameter("from", week[0]).setParameter("to", week[1]).list())
			ctx.weekLoad.put((String) row[0], ((Number) row[1]).intValue());

		return ctx;
	}

	/**
	 * (Re)generate relief assignments for the given date. Returns the number of
	 * affected lessons processed (assigned or left unassigned).
	 */
	@SuppressWarnings("unchecked")
	public static int generate(Long sessionId, Date date, String assignedBy, Session hibSession) {
		Context ctx = buildContext(sessionId, date, hibSession);

		// Preserve manual assignments; drop previous automatic ones for the date.
		Set<String> manualKeys = new HashSet<String>();
		for (ReliefAssignment r: ReliefAssignmentDAO.getInstance().findBySessionAndDate(hibSession, sessionId, date)) {
			if (r.isManual()) {
				manualKeys.add(lessonKey(r.getAbsence().getUniqueId(), r.getClazz().getUniqueId(), r.getStartPeriod()));
				// A preserved manual assignment reserves its reliever's time this run.
				if (r.getReliefUid() != null)
					addInterval(ctx.reservedThisRun, r.getReliefUid(), r.getStartPeriod(), r.getStopPeriod());
			} else {
				hibSession.remove(r);
			}
		}
		hibSession.flush();

		org.unitime.timetable.model.Session session = SessionDAO.getInstance().get(sessionId, hibSession);
		int processed = 0;
		for (StaffAbsence absence: StaffAbsenceDAO.getInstance().findApprovedOnDate(hibSession, sessionId, date)) {
			for (Object[] row: (List<Object[]>) hibSession.createQuery(
					"select distinct m, e.clazz from ClassEvent e inner join e.meetings m " +
					"where e.clazz.uniqueId in (select ci.classInstructing.uniqueId from ClassInstructor ci " +
					"  where ci.instructor.externalUniqueId = :uid and ci.instructor.department.session.uniqueId = :sessionId) " +
					"and m.meetingDate = :date and m.approvalStatus = 1", Object[].class)
					.setParameter("uid", absence.getExternalUniqueId())
					.setParameter("sessionId", sessionId)
					.setParameter("date", date).list()) {
				Meeting m = (Meeting) row[0];
				Class_ clazz = (Class_) row[1];
				if (manualKeys.contains(lessonKey(absence.getUniqueId(), clazz.getUniqueId(), m.getStartPeriod())))
					continue; // already covered by a preserved manual assignment

				int start = m.getStartPeriod(), stop = m.getStopPeriod();
				Long deptId = affectedDeptId(clazz);
				Candidate best = pickBest(ctx, start, stop, deptId);

				ReliefAssignment ra = new ReliefAssignment();
				ra.setSession(session);
				ra.setAbsence(absence);
				ra.setClazz(clazz);
				ra.setMeetingDate(date);
				ra.setStartPeriod(start);
				ra.setStopPeriod(stop);
				ra.setLocationPermanentId(m.getLocationPermanentId());
				ra.setAssignedBy(assignedBy);
				ra.setTimeStamp(new Date());
				if (best != null) {
					ra.setReliefUid(best.uid);
					ra.setReliefName(best.name);
					ra.setStatus(ReliefAssignment.Status.AUTO.value());
					addInterval(ctx.reservedThisRun, best.uid, start, stop);
					ctx.weekLoad.put(best.uid, weekLoad(ctx, best.uid) + 1);
				} else {
					ra.setStatus(ReliefAssignment.Status.UNASSIGNED.value());
				}
				hibSession.persist(ra);
				processed++;
			}
		}
		hibSession.flush();
		return processed;
	}

	/**
	 * The ranked list of teachers free to cover a lesson [start,stop) on the context's
	 * date (best first). Used by the oversight board's per-lesson dropdown. Pass the
	 * uid currently assigned (if any) so it is always included even if now at capacity.
	 */
	public static List<Candidate> rankedCandidates(Context ctx, int start, int stop, Long affectedDeptId, String currentUid) {
		List<Candidate> out = new ArrayList<Candidate>();
		for (String uid: ctx.names.keySet()) {
			boolean isCurrent = uid.equals(currentUid);
			if (!isCurrent && !isFree(ctx, uid, start, stop, affectedDeptId, true)) continue;
			boolean sameDept = affectedDeptId != null && ctx.depts.getOrDefault(uid, java.util.Collections.emptySet()).contains(affectedDeptId);
			out.add(new Candidate(uid, ctx.names.get(uid), sameDept, weekLoad(ctx, uid)));
		}
		out.sort(candidateOrder(ctx));
		return out;
	}

	/** The single best free candidate for a lesson, or null when none is available. */
	private static Candidate pickBest(Context ctx, int start, int stop, Long affectedDeptId) {
		Candidate best = null;
		Comparator<Candidate> order = candidateOrder(ctx);
		for (String uid: ctx.names.keySet()) {
			if (!isFree(ctx, uid, start, stop, affectedDeptId, false)) continue;
			boolean sameDept = affectedDeptId != null && ctx.depts.getOrDefault(uid, java.util.Collections.emptySet()).contains(affectedDeptId);
			Candidate c = new Candidate(uid, ctx.names.get(uid), sameDept, weekLoad(ctx, uid));
			if (best == null || order.compare(c, best) < 0) best = c;
		}
		return best;
	}

	/** Whether a teacher may cover a lesson: not absent/exempt/over-cap/busy/unavailable. */
	private static boolean isFree(Context ctx, String uid, int start, int stop, Long affectedDeptId, boolean allowAtCap) {
		if (uid == null) return false;
		if (ctx.absentUids.contains(uid)) return false;
		if (ctx.cfg.isExempt(uid)) return false;
		if (ctx.cfg.excludeNonTeaching() && !ctx.teachingUids.contains(uid)) return false;
		if (!allowAtCap && weekLoad(ctx, uid) >= ctx.cfg.weeklyCap()) return false;
		if (overlaps(ctx.busy.get(uid), start, stop)) return false;
		if (overlaps(ctx.reservedThisRun.get(uid), start, stop)) return false;
		if (isUnavailable(ctx, uid)) return false;
		return true;
	}

	/** Ordering: same-department first (if enabled), then lighter weekly load, then name. */
	private static Comparator<Candidate> candidateOrder(Context ctx) {
		final boolean continuity = ctx.cfg.preferContinuity() && ctx.cfg.sameDeptFirst();
		return new Comparator<Candidate>() {
			@Override public int compare(Candidate a, Candidate b) {
				if (continuity && a.sameDept != b.sameDept) return a.sameDept ? -1 : 1;
				if (a.weekLoad != b.weekLoad) return a.weekLoad - b.weekLoad;
				return (a.name == null ? "" : a.name).compareTo(b.name == null ? "" : b.name);
			}
		};
	}

	private static int weekLoad(Context ctx, String uid) {
		Integer n = ctx.weekLoad.get(uid);
		return n == null ? 0 : n;
	}

	/** True if the instructor declared the whole day unavailable. */
	private static boolean isUnavailable(Context ctx, String uid) {
		Calendar cal = Calendar.getInstance(Locale.US);
		cal.setTime(ctx.date);
		int day = cal.get(Calendar.DAY_OF_MONTH), month = cal.get(Calendar.MONTH);
		List<DepartmentalInstructor> is = ctx.instructors.get(uid);
		if (is != null)
			for (DepartmentalInstructor di: is)
				if (di.isUnavailable(day, month)) return true;
		return false;
	}

	/** Mark a teacher busy for [start,stop) in the given context (e.g. an existing relief). */
	public static void reserve(Context ctx, String uid, int start, int stop) {
		addInterval(ctx.reservedThisRun, uid, start, stop);
	}

	private static void addInterval(Map<String, List<int[]>> map, String uid, int start, int stop) {
		List<int[]> l = map.get(uid);
		if (l == null) { l = new ArrayList<int[]>(); map.put(uid, l); }
		l.add(new int[]{start, stop});
	}

	private static boolean overlaps(List<int[]> intervals, int start, int stop) {
		if (intervals == null) return false;
		for (int[] iv: intervals)
			if (start < iv[1] && iv[0] < stop) return true;
		return false;
	}

	/** Academic (subject-area) department of a class, for continuity ranking; null-safe. */
	public static Long affectedDeptId(Class_ clazz) {
		try {
			if (clazz.getSchedulingSubpart() != null
					&& clazz.getSchedulingSubpart().getControllingCourseOffering() != null
					&& clazz.getSchedulingSubpart().getControllingCourseOffering().getSubjectArea() != null
					&& clazz.getSchedulingSubpart().getControllingCourseOffering().getSubjectArea().getDepartment() != null)
				return clazz.getSchedulingSubpart().getControllingCourseOffering().getSubjectArea().getDepartment().getUniqueId();
		} catch (Exception e) {}
		try {
			if (clazz.getControllingDept() != null) return clazz.getControllingDept().getUniqueId();
		} catch (Exception e) {}
		return null;
	}

	private static String lessonKey(Long absenceId, Long classId, Integer startPeriod) {
		return absenceId + ":" + classId + ":" + startPeriod;
	}

	/** Monday..Sunday bounds of the week containing the given date. */
	public static Date[] weekBounds(Date date) {
		Calendar cal = Calendar.getInstance(Locale.US);
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
		int dow = cal.get(Calendar.DAY_OF_WEEK); // Sunday=1 .. Saturday=7
		int backToMonday = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
		cal.add(Calendar.DAY_OF_MONTH, -backToMonday);
		Date from = cal.getTime();
		cal.add(Calendar.DAY_OF_MONTH, 6);
		Date to = cal.getTime();
		return new Date[]{from, to};
	}
}
