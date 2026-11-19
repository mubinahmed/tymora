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
package org.unitime.timetable.server.approval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unitime.commons.Email;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.defaults.SessionAttribute;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.shared.AssignmentChangeInterface.AssignmentChangeInfo;
import org.unitime.timetable.gwt.shared.SuggestionsInterface.SelectedAssignment;
import org.unitime.timetable.model.AssignmentChange;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.AssignmentChangeDAO;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.ExamPeriodDAO;
import org.unitime.timetable.model.dao.LocationDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.SolverProxy;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.exam.ui.ExamAssignment;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo;
import org.unitime.timetable.solver.exam.ui.ExamProposedChange;
import org.unitime.timetable.solver.exam.ui.ExamRoomInfo;
import org.unitime.timetable.solver.service.CourseTimetablingSolverService;
import org.unitime.timetable.solver.service.ExaminationSolverService;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Reason and approval step for class and examination assignment changes.
 *
 * Policy (tmtbl.assignment.approval): "all" (default) - every change by a user without
 * Right.AssignmentChangeApprove becomes a pending request; "database" - only examination changes
 * written straight to the database need approval; "none" - nothing needs approval. Users with the
 * approve right always apply their changes directly; the change is recorded as self-approved.
 *
 * Every change (applied, pending, rejected, ...) is stored as an AssignmentChange row with the
 * reason, requester, decision and outcome. Decisions and direct changes also get a ChangeLog entry
 * whose title carries the reason, so they show on the existing Change Log page.
 *
 * Pending solver-mode changes remember the requester's solver key and are applied to that
 * in-memory solution on approval; this fails (status FAILED) when it is no longer loaded.
 */
@Service("assignmentChangeService")
public class AssignmentChangeService {
	private static Logger sLog = LogManager.getLogger(AssignmentChangeService.class);
	public static final int MIN_REASON_LENGTH = 5;

	@Autowired CourseTimetablingSolverService courseTimetablingSolverService;
	@Autowired ExaminationSolverService examinationSolverService;

	private final Gson iGson = new Gson();

	/** One examination placement of a pending change. */
	public static class ExamMove {
		Long examId, periodId;
		List<Long> roomIds = new ArrayList<Long>();
	}

	/** Examination change payload: exams to (re)place and exams to unassign. */
	public static class ExamPayload {
		List<ExamMove> assign = new ArrayList<ExamMove>();
		List<Long> unassign = new ArrayList<Long>();
	}

	// ------------------------------------------------------------------ policy

	public String getPolicy() {
		String p = ApplicationProperty.AssignmentApprovalPolicy.value();
		if ("none".equalsIgnoreCase(p) || "database".equalsIgnoreCase(p)) return p.toLowerCase();
		return "all";
	}

	public boolean canApprove(SessionContext context) {
		return context.hasPermission(Right.AssignmentChangeApprove);
	}

	public boolean needsApproval(SessionContext context, AssignmentChange.Type type, AssignmentChange.Mode mode) {
		if (canApprove(context)) return false;
		String policy = getPolicy();
		if ("none".equals(policy)) return false;
		if ("database".equals(policy)) return type == AssignmentChange.Type.EXAM && mode == AssignmentChange.Mode.DATABASE;
		return true;
	}

	public static String checkReason(String reason) {
		String r = (reason == null ? "" : reason.trim());
		if (r.length() < MIN_REASON_LENGTH)
			throw new GwtRpcException("Please give a reason for the change (at least " + MIN_REASON_LENGTH + " characters).");
		return r.length() > 2000 ? r.substring(0, 2000) : r;
	}

	/** Key of the user's course / exam solver, as CourseTimetablingSolverService.getSolver() resolves it. */
	public String solverKey(SessionContext context, AssignmentChange.Type type) {
		String puid = (String)context.getAttribute(type == AssignmentChange.Type.CLASS ? SessionAttribute.CourseTimetablingUser : SessionAttribute.ExaminationUser);
		return puid != null ? puid : context.getUser().getExternalUserId();
	}

	// ------------------------------------------------------------------ class changes

	/** Submit a class change from the class assignment dialog: applied at once, or stored as pending. */
	public AssignmentChange submitClassChange(SessionContext context, Long classId, List<SelectedAssignment> assignments, String reason, String summary) {
		context.checkPermission(Right.Suggestions);
		String why = checkReason(reason);
		if (assignments == null || assignments.isEmpty()) throw new GwtRpcException("Nothing to assign.");
		SolverProxy solver = courseTimetablingSolverService.getSolver();
		if (solver == null) throw new GwtRpcException("The course timetabling solver is not loaded.");
		if (solver.isWorking()) throw new GwtRpcException("The course timetabling solver is working, try again when it stops.");
		Class_ clazz = (classId == null ? null : Class_DAO.getInstance().get(classId));
		String name = (clazz == null ? "Class " + classId : clazz.getClassLabel());
		String payload = iGson.toJson(assignments);

		if (needsApproval(context, AssignmentChange.Type.CLASS, AssignmentChange.Mode.SOLVER)) {
			AssignmentChange c = newChange(context, AssignmentChange.Type.CLASS, AssignmentChange.Mode.SOLVER, classId, name, summary, payload, why);
			c.setSolverKey(solverKey(context, AssignmentChange.Type.CLASS));
			c.setStatus(AssignmentChange.Status.PENDING.name());
			save(c, null, null);
			notifyApprovers(context, c);
			return c;
		}
		solver.assignSelectedAssignments(assignments);
		AssignmentChange c = newChange(context, AssignmentChange.Type.CLASS, AssignmentChange.Mode.SOLVER, classId, name, summary, payload, why);
		c.setSolverKey(solverKey(context, AssignmentChange.Type.CLASS));
		selfApprove(context, c);
		save(c, context, clazz);
		return c;
	}

	// ------------------------------------------------------------------ exam changes

	public ExamPayload examPayload(ExamProposedChange change) {
		ExamPayload p = new ExamPayload();
		for (ExamAssignmentInfo a: change.getAssignments()) {
			if (a.getPeriodId() == null) { p.unassign.add(a.getExamId()); continue; }
			ExamMove m = new ExamMove();
			m.examId = a.getExamId(); m.periodId = a.getPeriodId();
			if (a.getRooms() != null) for (ExamRoomInfo r: a.getRooms()) m.roomIds.add(r.getLocationId());
			p.assign.add(m);
		}
		for (ExamAssignment c: change.getConflicts())
			if (c != null && !p.unassign.contains(c.getExamId())) p.unassign.add(c.getExamId());
		return p;
	}

	/** Store an examination change as a pending request (the caller decided it needs approval). */
	public AssignmentChange submitPendingExamChange(SessionContext context, Exam exam, AssignmentChange.Mode mode, ExamProposedChange change, String reason, String summary) {
		AssignmentChange c = newChange(context, AssignmentChange.Type.EXAM, mode, exam.getUniqueId(), exam.getLabel(), summary, iGson.toJson(examPayload(change)), checkReason(reason));
		if (mode == AssignmentChange.Mode.SOLVER) c.setSolverKey(solverKey(context, AssignmentChange.Type.EXAM));
		c.setStatus(AssignmentChange.Status.PENDING.name());
		save(c, null, null);
		notifyApprovers(context, c);
		return c;
	}

	/** Record an examination change the user applied directly (approver, or no approval needed). */
	public AssignmentChange recordAppliedExamChange(SessionContext context, Exam exam, AssignmentChange.Mode mode, ExamProposedChange change, String reason, String summary, String message) {
		AssignmentChange c = newChange(context, AssignmentChange.Type.EXAM, mode, exam.getUniqueId(), exam.getLabel(), summary, iGson.toJson(examPayload(change)), checkReason(reason));
		if (mode == AssignmentChange.Mode.SOLVER) c.setSolverKey(solverKey(context, AssignmentChange.Type.EXAM));
		selfApprove(context, c);
		c.setResultMessage(message);
		save(c, context, exam);
		return c;
	}

	// ------------------------------------------------------------------ decisions

	public AssignmentChange decide(SessionContext context, Long id, boolean approve, String note) {
		if (!canApprove(context)) throw new GwtRpcException("You are not allowed to approve or reject assignment changes.");
		org.hibernate.Session hibSession = AssignmentChangeDAO.getInstance().getSession();
		AssignmentChange c = AssignmentChangeDAO.getInstance().get(id, hibSession);
		if (c == null || !c.getSession().getUniqueId().equals(context.getUser().getCurrentAcademicSessionId()))
			throw new GwtRpcException("Change request " + id + " does not exist.");
		if (!c.isPending()) throw new GwtRpcException("Change request " + id + " is " + c.getStatus().toLowerCase() + ", not pending.");
		if (!approve && (note == null || note.trim().isEmpty()))
			throw new GwtRpcException("Please say why the change is rejected.");
		c.setDecidedBy(context.getUser().getExternalUserId());
		c.setDecidedName(context.getUser().getName());
		c.setDecidedTs(new Date());
		c.setDecisionNote(note == null || note.trim().isEmpty() ? null : trim(note.trim(), 2000));
		c.setRequesterSeen(false);
		if (approve) {
			try {
				String message = apply(context, c);
				c.setStatus(AssignmentChange.Status.APPLIED.name());
				c.setResultMessage(message == null || message.trim().isEmpty() ? null : trim(message, 2000));
			} catch (Exception e) {
				sLog.warn("Failed to apply assignment change " + id + ": " + e.getMessage(), e);
				c.setStatus(AssignmentChange.Status.FAILED.name());
				c.setResultMessage(trim(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 2000));
			}
		} else {
			c.setStatus(AssignmentChange.Status.REJECTED.name());
		}
		Object subject = subject(c, hibSession);
		Transaction tx = hibSession.beginTransaction();
		try {
			hibSession.merge(c);
			if (subject != null)
				ChangeLog.addChange(hibSession, context, subject, logTitle(c, (approve ? "approved: " : "rejected: ") + c.getReason()),
						c.getTypeEnum() == AssignmentChange.Type.CLASS ? ChangeLog.Source.CLASS_INFO : ChangeLog.Source.EXAM_INFO,
						approve ? ChangeLog.Operation.APPROVE : ChangeLog.Operation.REJECT, null, null);
			hibSession.flush();
			tx.commit();
		} catch (Exception e) {
			tx.rollback();
			throw new GwtRpcException("Failed to record the decision: " + e.getMessage(), e);
		}
		notifyRequester(c);
		return c;
	}

	public AssignmentChange cancel(SessionContext context, Long id) {
		org.hibernate.Session hibSession = AssignmentChangeDAO.getInstance().getSession();
		AssignmentChange c = AssignmentChangeDAO.getInstance().get(id, hibSession);
		if (c == null || !c.getSession().getUniqueId().equals(context.getUser().getCurrentAcademicSessionId()))
			throw new GwtRpcException("Change request " + id + " does not exist.");
		if (!context.getUser().getExternalUserId().equals(c.getRequestedBy()))
			throw new GwtRpcException("Only the requester can withdraw a change request.");
		if (!c.isPending()) throw new GwtRpcException("Change request " + id + " is no longer pending.");
		c.setStatus(AssignmentChange.Status.CANCELLED.name());
		c.setDecidedBy(context.getUser().getExternalUserId());
		c.setDecidedName(context.getUser().getName());
		c.setDecidedTs(new Date());
		save(c, null, null);
		return c;
	}

	/** Apply an approved change. Returns warnings from the solver / database (null if none). */
	protected String apply(SessionContext context, AssignmentChange c) throws Exception {
		Long sessionId = c.getSession().getUniqueId();
		if (c.getTypeEnum() == AssignmentChange.Type.CLASS) {
			SolverProxy solver = courseTimetablingSolverService.getSolver(c.getSolverKey(), sessionId);
			if (solver == null)
				throw new Exception("The requester's course solver (" + c.getRequestedName() + ") is no longer loaded, so the change cannot be applied. Ask them to load it and resubmit.");
			if (solver.isWorking()) throw new Exception("The requester's course solver is working; approve again when it stops.");
			List<SelectedAssignment> assignments = iGson.fromJson(c.getPayload(), new TypeToken<List<SelectedAssignment>>() {}.getType());
			solver.assignSelectedAssignments(assignments);
			return null;
		}
		ExamPayload p = iGson.fromJson(c.getPayload(), ExamPayload.class);
		StringBuffer message = new StringBuffer();
		if (c.getModeEnum() == AssignmentChange.Mode.SOLVER) {
			ExamSolverProxy solver = examinationSolverService.getSolver(c.getSolverKey(), sessionId);
			if (solver == null)
				throw new Exception("The requester's examination solver (" + c.getRequestedName() + ") is no longer loaded, so the change cannot be applied. Ask them to load it and resubmit.");
			if (solver.isWorking()) throw new Exception("The requester's examination solver is working; approve again when it stops.");
			for (Long examId: p.unassign) {
				ExamAssignmentInfo current = solver.getAssignmentInfo(examId);
				if (current != null && current.getPeriodId() != null) append(message, solver.unassign(current));
			}
			for (ExamMove m: p.assign) {
				ExamAssignmentInfo current = solver.getAssignmentInfo(m.examId);
				if (current != null && current.getPeriodId() != null) append(message, solver.unassign(current));
				ExamAssignmentInfo a = solver.getAssignment(m.examId, m.periodId, m.roomIds);
				if (a == null) throw new Exception("Examination " + m.examId + " can no longer be placed in the requested period and rooms.");
				append(message, solver.assign(a));
			}
			return message.length() == 0 ? null : message.toString();
		}
		org.hibernate.Session hibSession = ExamDAO.getInstance().getSession();
		String managerId = context.getUser().getExternalUserId();
		for (Long examId: p.unassign) {
			Exam exam = ExamDAO.getInstance().get(examId, hibSession);
			if (exam != null && exam.getAssignedPeriod() != null) append(message, exam.unassign(managerId, hibSession));
		}
		for (ExamMove m: p.assign) {
			Exam exam = ExamDAO.getInstance().get(m.examId, hibSession);
			ExamPeriod period = ExamPeriodDAO.getInstance().get(m.periodId, hibSession);
			if (exam == null || period == null) throw new Exception("Examination " + m.examId + " or its period no longer exists.");
			TreeSet<ExamRoomInfo> rooms = new TreeSet<ExamRoomInfo>();
			for (Long roomId: m.roomIds) {
				Location location = LocationDAO.getInstance().get(roomId, hibSession);
				if (location != null) rooms.add(new ExamRoomInfo(location, 0));
			}
			append(message, exam.assign(new ExamAssignmentInfo(exam, period, rooms), managerId, hibSession));
		}
		return message.length() == 0 ? null : message.toString();
	}

	// ------------------------------------------------------------------ listing

	public AssignmentChangeInfo toInfo(SessionContext context, AssignmentChange c, boolean canApprove) {
		AssignmentChangeInfo i = new AssignmentChangeInfo();
		String me = context.getUser().getExternalUserId();
		i.setId(c.getUniqueId());
		i.setType(c.getChangeType());
		i.setMode(c.getChangeMode());
		i.setStatus(c.getStatus());
		i.setSubjectId(c.getSubjectId());
		i.setSubjectName(c.getSubjectName());
		i.setSummary(c.getSummary());
		i.setReason(c.getReason());
		i.setRequestedBy(c.getRequestedBy());
		i.setRequestedName(c.getRequestedName());
		i.setRequestedTs(c.getRequestedTs());
		i.setDecidedName(c.getDecidedName());
		i.setDecidedTs(c.getDecidedTs());
		i.setDecisionNote(c.getDecisionNote());
		i.setResultMessage(c.getResultMessage());
		i.setMine(me != null && me.equals(c.getRequestedBy()));
		i.setCanDecide(c.isPending() && canApprove);
		i.setCanCancel(c.isPending() && i.isMine());
		i.setSelfApproved(AssignmentChange.Status.APPLIED.name().equals(c.getStatus()) && c.getDecidedBy() != null && c.getDecidedBy().equals(c.getRequestedBy()) && c.getDecisionNote() != null && c.getDecisionNote().startsWith("Applied directly"));
		return i;
	}

	// ------------------------------------------------------------------ helpers

	protected AssignmentChange newChange(SessionContext context, AssignmentChange.Type type, AssignmentChange.Mode mode, Long subjectId, String subjectName, String summary, String payload, String reason) {
		AssignmentChange c = new AssignmentChange();
		c.setSession(SessionDAO.getInstance().get(context.getUser().getCurrentAcademicSessionId()));
		c.setChangeType(type.name());
		c.setChangeMode(mode.name());
		c.setSubjectId(subjectId);
		c.setSubjectName(trim(subjectName, 200));
		c.setSummary(trim(summary, 4000));
		c.setPayload(payload);
		c.setReason(reason);
		c.setRequestedBy(context.getUser().getExternalUserId());
		c.setRequestedName(trim(context.getUser().getName(), 100));
		c.setRequestedTs(new Date());
		c.setRequesterSeen(true);
		return c;
	}

	protected void selfApprove(SessionContext context, AssignmentChange c) {
		c.setStatus(AssignmentChange.Status.APPLIED.name());
		c.setDecidedBy(c.getRequestedBy());
		c.setDecidedName(c.getRequestedName());
		c.setDecidedTs(c.getRequestedTs());
		c.setDecisionNote(canApprove(context) ? "Applied directly by an approver." : "Applied directly (no approval needed by the " + getPolicy() + " policy).");
	}

	/** Save the change; with a subject also write a ChangeLog entry (direct changes). */
	protected void save(AssignmentChange c, SessionContext context, Object subject) {
		org.hibernate.Session hibSession = AssignmentChangeDAO.getInstance().getSession();
		Transaction tx = hibSession.beginTransaction();
		try {
			if (c.getUniqueId() == null) hibSession.persist(c); else hibSession.merge(c);
			if (context != null && subject != null)
				ChangeLog.addChange(hibSession, context, subject, logTitle(c, c.getReason()),
						c.getTypeEnum() == AssignmentChange.Type.CLASS ? ChangeLog.Source.CLASS_INFO : ChangeLog.Source.EXAM_INFO,
						ChangeLog.Operation.APPROVE, null, null);
			hibSession.flush();
			tx.commit();
		} catch (Exception e) {
			tx.rollback();
			throw new GwtRpcException("Failed to record the change: " + e.getMessage(), e);
		}
	}

	private Object subject(AssignmentChange c, org.hibernate.Session hibSession) {
		if (c.getSubjectId() == null) return null;
		return c.getTypeEnum() == AssignmentChange.Type.CLASS ? Class_DAO.getInstance().get(c.getSubjectId(), hibSession) : ExamDAO.getInstance().get(c.getSubjectId(), hibSession);
	}

	private static String logTitle(AssignmentChange c, String text) {
		return trim(c.getSubjectName() + " - " + text, 255);
	}

	private static String trim(String s, int max) {
		if (s == null) return null;
		return s.length() <= max ? s : s.substring(0, max - 3) + "...";
	}

	private static void append(StringBuffer buffer, String message) {
		if (message == null || message.trim().isEmpty()) return;
		if (buffer.length() > 0) buffer.append("\n");
		buffer.append(message);
	}

	// ------------------------------------------------------------------ notifications (best effort)

	/** Managers whose roles hold AssignmentChangeApprove and who have an email address. */
	protected Collection<TimetableManager> approvers() {
		return AssignmentChangeDAO.getInstance().getSession().createQuery(
				"select distinct mr.timetableManager from ManagerRole mr inner join mr.role.rights g where g = :right", TimetableManager.class)
				.setParameter("right", Right.AssignmentChangeApprove.name()).list();
	}

	protected void notifyApprovers(SessionContext context, AssignmentChange c) {
		if (!ApplicationProperty.AssignmentApprovalEmails.isTrue()) return;
		try {
			Email email = null;
			for (TimetableManager m: approvers()) {
				if (m.getEmailAddress() == null || m.getEmailAddress().isEmpty()) continue;
				if (email == null) email = Email.createEmail();
				email.addRecipient(m.getEmailAddress(), m.getName());
			}
			if (email == null) return;
			email.setSubject("Assignment change awaiting approval: " + c.getSubjectName());
			email.setText(c.getRequestedName() + " asks to change " + c.getSubjectName() + ".\n\n" +
					(c.getSummary() == null ? "" : c.getSummary() + "\n\n") +
					"Reason: " + c.getReason() + "\n\nApprove or reject it on the Change Requests page.");
			email.send();
		} catch (Throwable t) {
			sLog.info("Approval email not sent: " + t.getMessage());
		}
	}

	protected void notifyRequester(AssignmentChange c) {
		if (!ApplicationProperty.AssignmentApprovalEmails.isTrue()) return;
		try {
			TimetableManager m = TimetableManager.findByExternalId(c.getRequestedBy());
			if (m == null || m.getEmailAddress() == null || m.getEmailAddress().isEmpty()) return;
			Email email = Email.createEmail();
			email.addRecipient(m.getEmailAddress(), m.getName());
			email.setSubject("Assignment change " + c.getStatus().toLowerCase() + ": " + c.getSubjectName());
			email.setText("Your change to " + c.getSubjectName() + " was " + c.getStatus().toLowerCase() + " by " + c.getDecidedName() + ".\n\n" +
					(c.getDecisionNote() == null ? "" : "Note: " + c.getDecisionNote() + "\n\n") +
					(c.getResultMessage() == null ? "" : c.getResultMessage() + "\n\n") +
					"Your reason was: " + c.getReason());
			email.send();
		} catch (Throwable t) {
			sLog.info("Decision email not sent: " + t.getMessage());
		}
	}
}
