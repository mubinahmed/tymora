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

import java.util.Date;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SessionEditInterface.SessionEditRequest;
import org.unitime.timetable.gwt.shared.SessionEditInterface.SessionEditResponse;
import org.unitime.timetable.gwt.shared.SessionEditInterface.StatusOption;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ClassDurationType;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.InstructionalMethod;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StudentSectioningQueue;
import org.unitime.timetable.model.StudentSectioningStatus;
import org.unitime.timetable.model.dao.ClassDurationTypeDAO;
import org.unitime.timetable.model.dao.DatePatternDAO;
import org.unitime.timetable.model.dao.DepartmentStatusTypeDAO;
import org.unitime.timetable.model.dao.InstructionalMethodDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.StudentSectioningStatusDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Formats;

/**
 * Edit an academic {@link Session}, aligned with the legacy Struts sessionEdit
 * page: academic initiative / year / term, the date boundaries (session begin,
 * classes end, exam begin, session end, event begin/end), status, default date
 * pattern, default class duration type, default instructional method, the
 * online-student-scheduling week boundaries (enroll / change / drop), the default
 * sectioning status and the notification dates. Backs the Angular sessions-edit
 * screen.
 *
 * <p>LOAD/SAVE require {@link Right#AcademicSessionEdit}; DELETE requires
 * {@link Right#AcademicSessionDelete}. On SAVE the entity is loaded via
 * {@link SessionDAO} and the rendered fields are set (merge-on-update). The
 * interactive HOLIDAYS calendar, the DATE/TIME pattern editors and ROLL-FORWARD
 * remain dedicated legacy screens and are left untouched.</p>
 *
 * @author Angular migration
 */
@GwtRpcImplements(SessionEditRequest.class)
public class SessionEditBackend implements GwtRpcImplementation<SessionEditRequest, SessionEditResponse> {

	@Override
	public SessionEditResponse execute(SessionEditRequest request, SessionContext context) {
		if (request.getOperation() == null)
			throw new GwtRpcException("No operation specified.");
		switch (request.getOperation()) {
		case LOAD:
			return load(request, context);
		case SAVE:
			return save(request, context);
		case DELETE:
			return delete(request, context);
		default:
			throw new GwtRpcException("Unsupported operation: " + request.getOperation());
		}
	}

	protected SessionEditResponse load(SessionEditRequest request, SessionContext context) {
		if (request.getUniqueId() == null)
			throw new GwtRpcException("No academic session specified. Creating a new session is not supported here.");

		Session session = SessionDAO.getInstance().get(request.getUniqueId());
		if (session == null)
			throw new GwtRpcException("Academic session not found.");

		context.checkPermission(session, Right.AcademicSessionEdit);

		SessionEditResponse response = new SessionEditResponse();
		Formats.Format<Date> df = Formats.getDateFormat(Formats.Pattern.DATE_ENTRY_FORMAT);
		response.setDateFormat(df.toPattern());
		response.setUniqueId(session.getUniqueId());
		response.setLabel(session.getLabel());
		response.setAcademicInitiative(session.getAcademicInitiative());
		response.setAcademicYear(session.getAcademicYear());
		response.setAcademicTerm(session.getAcademicTerm());
		response.setSessionBeginDateTime(fmt(df, session.getSessionBeginDateTime()));
		response.setClassesEndDateTime(fmt(df, session.getClassesEndDateTime()));
		response.setSessionEndDateTime(fmt(df, session.getSessionEndDateTime()));
		response.setExamBeginDate(fmt(df, session.getExamBeginDate()));
		response.setEventBeginDate(fmt(df, session.getEventBeginDate()));
		response.setEventEndDate(fmt(df, session.getEventEndDate()));
		response.setNotificationsBegin(fmt(df, session.getNotificationsBeginDate()));
		response.setNotificationsEnd(fmt(df, session.getNotificationsEndDate()));
		response.setStatusTypeId(session.getStatusType() == null ? null : session.getStatusType().getUniqueId());
		response.setDefaultDatePatternId(session.getDefaultDatePattern() == null ? null : session.getDefaultDatePattern().getUniqueId());
		response.setDurationTypeId(session.getDefaultClassDurationType() == null ? null : session.getDefaultClassDurationType().getUniqueId());
		response.setInstructionalMethodId(session.getDefaultInstructionalMethod() == null ? null : session.getDefaultInstructionalMethod().getUniqueId());
		response.setSectStatusId(session.getDefaultSectioningStatus() == null ? null : session.getDefaultSectioningStatus().getUniqueId());
		response.setWkEnroll(session.getLastWeekToEnroll());
		response.setWkChange(session.getLastWeekToChange());
		response.setWkDrop(session.getLastWeekToDrop());
		response.setCanEdit(true);
		response.setCanDelete(context.hasPermission(session, Right.AcademicSessionDelete)
				&& !session.getUniqueId().equals(context.getUser().getCurrentAcademicSessionId()));

		boolean includeTest = context.hasPermission(Right.AllowTestSessions);
		for (DepartmentStatusType st : DepartmentStatusType.findAllForSession(includeTest))
			response.addStatus(new StatusOption(st.getUniqueId(), st.getLabel()));
		for (DatePattern dp : DatePattern.findAll(session.getUniqueId(), false, null, session.getDefaultDatePattern()))
			response.addDatePattern(new StatusOption(dp.getUniqueId(), dp.getName()));
		for (ClassDurationType t : ClassDurationType.findAll())
			if (t.isVisible() || t.getUniqueId().equals(response.getDurationTypeId()))
				response.addDurationType(new StatusOption(t.getUniqueId(), t.getLabel()));
		for (InstructionalMethod im : InstructionalMethod.findAll())
			response.addInstructionalMethod(new StatusOption(im.getUniqueId(), im.getLabel()));
		for (StudentSectioningStatus st : StudentSectioningStatus.findAll(session.getUniqueId()))
			response.addSectStatus(new StatusOption(st.getUniqueId(), st.getLabel()));

		return response;
	}

	protected SessionEditResponse save(SessionEditRequest request, SessionContext context) {
		if (request.getUniqueId() == null)
			throw new GwtRpcException("Creating a new academic session is not supported here.");

		context.checkPermission(request.getUniqueId(), "Session", Right.AcademicSessionEdit);

		if (isEmpty(request.getAcademicInitiative()))
			throw new GwtRpcException("Academic initiative is required.");
		if (isEmpty(request.getAcademicYear()))
			throw new GwtRpcException("Academic year is required.");
		if (isEmpty(request.getAcademicTerm()))
			throw new GwtRpcException("Academic term is required.");
		if (request.getStatusTypeId() == null)
			throw new GwtRpcException("Academic session status is required.");

		Formats.Format<Date> df = Formats.getDateFormat(Formats.Pattern.DATE_ENTRY_FORMAT);
		Date begin = parseDate(df, request.getSessionBeginDateTime(), "session start date");
		Date classesEnd = parseDate(df, request.getClassesEndDateTime(), "classes end date");
		Date end = parseDate(df, request.getSessionEndDateTime(), "session end date");
		Date examBegin = parseDate(df, request.getExamBeginDate(), "examination start date");
		Date eventBegin = parseDate(df, request.getEventBeginDate(), "event start date");
		Date eventEnd = parseDate(df, request.getEventEndDate(), "event end date");

		// Ordering checks, faithful to the legacy SessionEditForm.validateDates.
		if (!end.after(begin))
			throw new GwtRpcException("The session end date must be after the session start date.");
		if (!classesEnd.after(begin))
			throw new GwtRpcException("The classes end date must be after the session start date.");
		if (classesEnd.after(end))
			throw new GwtRpcException("The classes end date must be on or before the session end date.");
		if (!eventBegin.before(eventEnd))
			throw new GwtRpcException("The event end date must be after the event start date.");

		Date notifBegin = parseOptionalDate(df, request.getNotificationsBegin(), "notifications begin date");
		Date notifEnd = parseOptionalDate(df, request.getNotificationsEnd(), "notifications end date");
		if (notifBegin != null && notifEnd != null && !notifBegin.before(notifEnd))
			throw new GwtRpcException("The notifications end date must be after the notifications start date.");

		DepartmentStatusType status = DepartmentStatusTypeDAO.getInstance().get(request.getStatusTypeId());
		if (status == null)
			throw new GwtRpcException("Selected academic session status was not found.");

		Transaction tx = null;
		org.hibernate.Session hibSession = SessionDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			Session session = SessionDAO.getInstance().get(request.getUniqueId(), hibSession);
			if (session == null)
				throw new GwtRpcException("Academic session not found.");

			// Merge-on-update. The interactive holidays calendar, the date/time
			// pattern editors and roll-forward are left to their legacy screens.
			session.setAcademicInitiative(request.getAcademicInitiative().trim());
			session.setAcademicYear(request.getAcademicYear().trim());
			session.setAcademicTerm(request.getAcademicTerm().trim());
			session.setSessionBeginDateTime(begin);
			session.setClassesEndDateTime(classesEnd);
			session.setSessionEndDateTime(end);
			session.setExamBeginDate(examBegin);
			session.setEventBeginDate(eventBegin);
			session.setEventEndDate(eventEnd);
			session.setStatusType(status);
			session.setDefaultDatePattern(resolveId(request.getDefaultDatePatternId()) == null ? null
					: DatePatternDAO.getInstance().get(request.getDefaultDatePatternId(), hibSession));
			session.setDefaultClassDurationType(resolveId(request.getDurationTypeId()) == null ? null
					: ClassDurationTypeDAO.getInstance().get(request.getDurationTypeId(), hibSession));
			session.setDefaultInstructionalMethod(resolveId(request.getInstructionalMethodId()) == null ? null
					: InstructionalMethodDAO.getInstance().get(request.getInstructionalMethodId(), hibSession));
			session.setDefaultSectioningStatus(resolveId(request.getSectStatusId()) == null ? null
					: StudentSectioningStatusDAO.getInstance().get(request.getSectStatusId(), hibSession));
			if (request.getWkEnroll() != null) session.setLastWeekToEnroll(request.getWkEnroll());
			if (request.getWkChange() != null) session.setLastWeekToChange(request.getWkChange());
			if (request.getWkDrop() != null) session.setLastWeekToDrop(request.getWkDrop());
			session.setNotificationsBeginDate(notifBegin);
			session.setNotificationsEndDate(notifEnd);

			hibSession.merge(session);

			ChangeLog.addChange(
					hibSession,
					context,
					session,
					ChangeLog.Source.SESSION_EDIT,
					ChangeLog.Operation.UPDATE,
					null,
					null);

			StudentSectioningQueue.sessionStatusChanged(hibSession, context.getUser(), session.getUniqueId(), false);

			tx.commit();

			SessionEditResponse response = new SessionEditResponse();
			response.setUniqueId(session.getUniqueId());
			response.setLabel(session.getLabel());
			return response;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Exception e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(e.getMessage(), e);
		}
	}

	protected SessionEditResponse delete(SessionEditRequest request, SessionContext context) {
		if (request.getUniqueId() == null)
			throw new GwtRpcException("No academic session specified.");

		if (request.getUniqueId().equals(context.getUser().getCurrentAcademicSessionId()))
			throw new GwtRpcException("The currently selected academic session cannot be deleted.");

		context.checkPermission(request.getUniqueId(), "Session", Right.AcademicSessionDelete);

		Session session = SessionDAO.getInstance().get(request.getUniqueId());
		if (session == null)
			throw new GwtRpcException("Academic session not found.");
		String label = session.getLabel();

		// Mirrors the legacy action: the full cascade delete lives in the model.
		Session.deleteSessionById(request.getUniqueId());

		SessionEditResponse response = new SessionEditResponse();
		response.setUniqueId(request.getUniqueId());
		response.setLabel(label);
		return response;
	}

	private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

	/** null / negative -> null ("none" selection); otherwise the id itself. */
	private static Long resolveId(Long id) {
		return (id == null || id < 0) ? null : id;
	}

	private static String fmt(Formats.Format<Date> df, Date d) {
		return d == null ? "" : df.format(d);
	}

	private static Date parseDate(Formats.Format<Date> df, String value, String what) {
		if (isEmpty(value))
			throw new GwtRpcException("The " + what + " is required.");
		try {
			Date d = df.parse(value.trim());
			if (d == null)
				throw new GwtRpcException("The " + what + " is not a valid date.");
			return d;
		} catch (GwtRpcException e) {
			throw e;
		} catch (Exception e) {
			throw new GwtRpcException("The " + what + " '" + value + "' could not be parsed (expected format " + df.toPattern() + ").");
		}
	}

	private static Date parseOptionalDate(Formats.Format<Date> df, String value, String what) {
		if (isEmpty(value)) return null;
		return parseDate(df, value, what);
	}
}
