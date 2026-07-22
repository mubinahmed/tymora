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
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StudentSectioningQueue;
import org.unitime.timetable.model.dao.DepartmentStatusTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Formats;

/**
 * Edit the core descriptive fields of an academic {@link Session}: academic
 * initiative / year / term, the session begin, classes end and session end
 * dates, and the status type. Backs the Angular sessions-edit screen and
 * replaces the descriptive-field portion of the legacy Struts sessionEdit page.
 *
 * <p>Faithful to the legacy action's permission model: LOAD/SAVE require
 * {@link Right#AcademicSessionEdit} on the session and DELETE requires
 * {@link Right#AcademicSessionDelete}. On SAVE the existing entity is loaded via
 * {@link SessionDAO} and ONLY the rendered fields are set (merge-on-update) --
 * exam/event periods, holidays, date/time patterns, roll-forward, the week
 * boundaries and every relation are left exactly as they were.</p>
 *
 * <p>CREATE is intentionally NOT supported: a Session row has several mandatory
 * columns this screen does not render (exam begin date, event begin/end dates,
 * status, enroll/change/drop week boundaries) plus holiday and date-pattern
 * setup, so a safe create is not possible from these fields alone. Creation
 * remains the responsibility of the legacy page.</p>
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
		response.setSessionBeginDateTime(session.getSessionBeginDateTime() == null ? "" : df.format(session.getSessionBeginDateTime()));
		response.setClassesEndDateTime(session.getClassesEndDateTime() == null ? "" : df.format(session.getClassesEndDateTime()));
		response.setSessionEndDateTime(session.getSessionEndDateTime() == null ? "" : df.format(session.getSessionEndDateTime()));
		response.setStatusTypeId(session.getStatusType() == null ? null : session.getStatusType().getUniqueId());
		response.setCanEdit(true);
		response.setCanDelete(context.hasPermission(session, Right.AcademicSessionDelete)
				&& !session.getUniqueId().equals(context.getUser().getCurrentAcademicSessionId()));

		boolean includeTest = context.hasPermission(Right.AllowTestSessions);
		for (DepartmentStatusType st : DepartmentStatusType.findAllForSession(includeTest))
			response.addStatus(new StatusOption(st.getUniqueId(), st.getLabel()));

		return response;
	}

	protected SessionEditResponse save(SessionEditRequest request, SessionContext context) {
		if (request.getUniqueId() == null)
			throw new GwtRpcException("Creating a new academic session is not supported here.");

		context.checkPermission(request.getUniqueId(), "Session", Right.AcademicSessionEdit);

		// Validate the descriptive fields before touching the database.
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

			// Merge-on-update: set ONLY the fields this screen renders. Every other
			// column and relation (exam/event dates, holidays, date/time patterns,
			// week boundaries, default sectioning/duration/instructional method,
			// subject areas, departments, rooms, offerings) is deliberately left
			// untouched.
			session.setAcademicInitiative(request.getAcademicInitiative().trim());
			session.setAcademicYear(request.getAcademicYear().trim());
			session.setAcademicTerm(request.getAcademicTerm().trim());
			session.setSessionBeginDateTime(begin);
			session.setClassesEndDateTime(classesEnd);
			session.setSessionEndDateTime(end);
			session.setStatusType(status);

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
}
