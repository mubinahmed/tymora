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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ReliefBoardInterface.CandidateInfo;
import org.unitime.timetable.gwt.shared.ReliefBoardInterface.LessonInfo;
import org.unitime.timetable.gwt.shared.ReliefBoardInterface.ReliefBoardRequest;
import org.unitime.timetable.gwt.shared.ReliefBoardInterface.ReliefBoardResponse;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.dao.ReliefAssignmentDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.relief.ReliefGenerationHelper.Candidate;
import org.unitime.timetable.server.relief.ReliefGenerationHelper.Context;
import org.unitime.timetable.util.Constants;

/**
 * Backing bean for the Relief Planning oversight board (see {@link ReliefBoardInterface}).
 * Runs the allocation engine, lists the day's vacated lessons with their relief teacher
 * and free-candidate options, and applies manual reassignments. Requires
 * {@code ReliefPlanning} to view and {@code ReliefPlanningEdit} to modify. Additive —
 * introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
@GwtRpcImplements(ReliefBoardRequest.class)
public class ReliefBoardBackend implements GwtRpcImplementation<ReliefBoardRequest, ReliefBoardResponse> {

	@Override
	public ReliefBoardResponse execute(ReliefBoardRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		context.checkPermission(Right.ReliefPlanning);
		boolean canManage = context.hasPermission(Right.ReliefPlanningEdit);

		Date date = parse(request.getDate());
		if (date == null)
			throw new GwtRpcException("A date is required.");

		org.hibernate.Session hibSession = ReliefAssignmentDAO.getInstance().getSession();
		Transaction tx = null;
		int generated = 0;
		try {
			tx = hibSession.beginTransaction();
			switch (request.getOperation()) {
				case GENERATE:
					if (!canManage) throw new GwtRpcException("You are not allowed to generate relief.");
					generated = ReliefGenerationHelper.generate(sessionId, date,
							context.getUser().getName(), hibSession);
					break;
				case REASSIGN:
					if (!canManage) throw new GwtRpcException("You are not allowed to change relief.");
					reassign(request, sessionId, date, context, hibSession);
					break;
				case CLEAR:
					if (!canManage) throw new GwtRpcException("You are not allowed to change relief.");
					clear(request, sessionId, hibSession);
					break;
				default: break;
			}
			ReliefBoardResponse response = load(sessionId, date, canManage, hibSession);
			response.setGeneratedCount(generated);
			tx.commit();
			return response;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Exception e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException("Relief board operation failed: " + e.getMessage(), e);
		}
	}

	private ReliefBoardResponse load(Long sessionId, Date date, boolean canManage, org.hibernate.Session hibSession) {
		ReliefBoardResponse r = new ReliefBoardResponse();
		r.setDate(fmt(date));
		r.setCanManage(canManage);

		List<ReliefAssignment> assignments = ReliefAssignmentDAO.getInstance().findBySessionAndDate(hibSession, sessionId, date);

		// One shared context for candidate lists; seed it with the existing relief so an
		// already-relieving teacher is not offered again for an overlapping lesson.
		Context ctx = ReliefGenerationHelper.buildContext(sessionId, date, hibSession);
		for (ReliefAssignment a: assignments)
			if (a.getReliefUid() != null)
				ReliefGenerationHelper.reserve(ctx, a.getReliefUid(), a.getStartPeriod(), a.getStopPeriod());

		for (ReliefAssignment a: assignments) {
			LessonInfo l = new LessonInfo();
			l.setId(a.getUniqueId());
			l.setAbsentName(a.getAbsence() == null ? "" : a.getAbsence().getName());
			l.setReasonLabel(a.getAbsence() != null && a.getAbsence().getReason() != null ? a.getAbsence().getReason().getLabel() : "");
			l.setClassName(a.getClazz() == null ? "" : a.getClazz().getClassLabel());
			l.setTimeText(timeText(a.getStartPeriod(), a.getStopPeriod()));
			l.setRoomName(roomName(a.getLocationPermanentId(), sessionId, hibSession));
			l.setReliefUid(a.getReliefUid());
			l.setReliefName(a.getReliefName());
			l.setStatus(a.getStatus() == null ? 0 : a.getStatus());
			l.setStatusLabel(a.status().name());

			Long deptId = ReliefGenerationHelper.affectedDeptId(a.getClazz());
			for (Candidate c: ReliefGenerationHelper.rankedCandidates(ctx, a.getStartPeriod(), a.getStopPeriod(), deptId, a.getReliefUid()))
				l.addCandidate(new CandidateInfo(c.uid, c.name, c.sameDept, c.weekLoad));
			r.addLesson(l);
		}
		return r;
	}

	private void reassign(ReliefBoardRequest request, Long sessionId, Date date, SessionContext context, org.hibernate.Session hibSession) {
		ReliefAssignment a = ReliefAssignmentDAO.getInstance().get(request.getLessonId(), hibSession);
		if (a == null || !sessionId.equals(a.getSession().getUniqueId()))
			throw new GwtRpcException("The lesson no longer exists.");
		String uid = request.getReliefUid();
		if (uid == null || uid.trim().isEmpty()) {
			a.setReliefUid(null);
			a.setReliefName(null);
		} else {
			uid = uid.trim();
			assertFree(uid, sessionId, date, a.getStartPeriod(), a.getStopPeriod(), a.getUniqueId(), hibSession);
			a.setReliefUid(uid);
			a.setReliefName(nameOf(uid, sessionId));
		}
		a.setStatus(ReliefAssignment.Status.MANUAL.value());
		a.setAssignedBy(context.getUser().getName());
		a.setTimeStamp(new Date());
		hibSession.merge(a);
	}

	private void clear(ReliefBoardRequest request, Long sessionId, org.hibernate.Session hibSession) {
		ReliefAssignment a = ReliefAssignmentDAO.getInstance().get(request.getLessonId(), hibSession);
		if (a == null || !sessionId.equals(a.getSession().getUniqueId())) return;
		a.setReliefUid(null);
		a.setReliefName(null);
		a.setStatus(ReliefAssignment.Status.UNASSIGNED.value());
		hibSession.merge(a);
	}

	/** Reject a manual pick that would double-book the teacher (teaching or other relief). */
	@SuppressWarnings("unchecked")
	private void assertFree(String uid, Long sessionId, Date date, int start, int stop, Long selfLessonId, org.hibernate.Session hibSession) {
		for (Object[] row: (List<Object[]>) hibSession.createQuery(
				"select m.startPeriod, m.stopPeriod from ClassEvent e inner join e.meetings m inner join e.clazz.classInstructors ci " +
				"where ci.instructor.externalUniqueId = :uid and ci.instructor.department.session.uniqueId = :sessionId " +
				"and m.meetingDate = :date and m.approvalStatus = 1", Object[].class)
				.setParameter("uid", uid).setParameter("sessionId", sessionId).setParameter("date", date).list()) {
			if (start < ((Number) row[1]).intValue() && ((Number) row[0]).intValue() < stop)
				throw new GwtRpcException("That teacher is already teaching at this time.");
		}
		for (ReliefAssignment other: ReliefAssignmentDAO.getInstance().findBySessionAndDate(hibSession, sessionId, date)) {
			if (other.getUniqueId().equals(selfLessonId)) continue;
			if (uid.equals(other.getReliefUid()) && start < other.getStopPeriod() && other.getStartPeriod() < stop)
				throw new GwtRpcException("That teacher is already covering another lesson at this time.");
		}
	}

	private String nameOf(String uid, Long sessionId) {
		for (org.unitime.timetable.model.DepartmentalInstructor di: org.unitime.timetable.model.DepartmentalInstructor.findInstructorsForSession(sessionId))
			if (uid.equals(di.getExternalUniqueId())) return di.nameLastNameFirst();
		return uid;
	}

	private String roomName(Long permId, Long sessionId, org.hibernate.Session hibSession) {
		if (permId == null) return "";
		Location loc = hibSession.createQuery(
				"from Location l where l.permanentId = :pid and l.session.uniqueId = :sid", Location.class)
				.setParameter("pid", permId).setParameter("sid", sessionId).setMaxResults(1).uniqueResult();
		return loc == null ? "" : loc.getLabel();
	}

	private static String timeText(int start, int stop) {
		return Constants.toTime(start * Constants.SLOT_LENGTH_MIN) + " - " + Constants.toTime(stop * Constants.SLOT_LENGTH_MIN);
	}

	private static Date parse(String s) {
		if (s == null || s.trim().isEmpty()) return null;
		try { return new SimpleDateFormat("yyyy-MM-dd").parse(s.trim()); }
		catch (Exception e) { return null; }
	}

	private static String fmt(Date d) {
		return d == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(d);
	}
}
