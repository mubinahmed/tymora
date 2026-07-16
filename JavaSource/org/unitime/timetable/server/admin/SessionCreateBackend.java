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
import org.unitime.timetable.gwt.shared.SessionCreateInterface.Operation;
import org.unitime.timetable.gwt.shared.SessionCreateInterface.SessionCreateRequest;
import org.unitime.timetable.gwt.shared.SessionCreateInterface.SessionCreateResponse;
import org.unitime.timetable.gwt.shared.SessionCreateInterface.StatusOption;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.DepartmentStatusTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Formats;

/**
 * Create a brand new academic {@link Session}. Implements the CREATE half that
 * {@link SessionEditBackend} intentionally deferred (that bean is edit-only).
 * Backs the Angular session-create screen and replaces the "Add Academic
 * Session" portion of the legacy Struts sessionEdit page (op=addSession /
 * saveSession with a null session id).
 *
 * <p>Permission model mirrors the legacy action: both LOAD and SAVE require the
 * global {@link Right#AcademicSessionAdd}.</p>
 *
 * <p>SAVE sets every mandatory NOT-NULL column the model requires:
 * academicInitiative / academicYear / academicTerm, the session begin, classes
 * end and session end dates, the exam begin date, the event begin and end dates,
 * the status type, and the last-week-to-enroll / change / drop boundaries
 * (defaulted to the legacy form defaults 1 / 1 / 4). Every OPTIONAL column is
 * left null / unset on create -- default date pattern, holidays, notifications
 * dates, default sectioning status, default class duration type and default
 * instructional method are all nullable in the schema and are configured later
 * via the Edit / legacy pages.</p>
 *
 * <p>DEFERRED (noted, intentionally NOT done here): the legacy saveSession also
 * (a) auto-creates an EventContact for the current user and (b) regenerates
 * committed class / exam events. Both operate over offerings and exams that a
 * freshly created, empty session does not yet have, so they are no-ops for a new
 * session and are omitted to keep this create path conservative. Holiday
 * encoding and roll-forward remain out of scope.</p>
 *
 * @author Angular migration
 */
@GwtRpcImplements(SessionCreateRequest.class)
public class SessionCreateBackend implements GwtRpcImplementation<SessionCreateRequest, SessionCreateResponse> {

	@Override
	public SessionCreateResponse execute(SessionCreateRequest request, SessionContext context) {
		if (request.getOperation() == null)
			throw new GwtRpcException("No operation specified.");
		if (request.getOperation() == Operation.LOAD)
			return load(request, context);
		if (request.getOperation() == Operation.SAVE)
			return save(request, context);
		throw new GwtRpcException("Unsupported operation: " + request.getOperation());
	}

	protected SessionCreateResponse load(SessionCreateRequest request, SessionContext context) {
		context.checkPermission(Right.AcademicSessionAdd);

		SessionCreateResponse response = new SessionCreateResponse();
		Formats.Format<Date> df = Formats.getDateFormat(Formats.Pattern.DATE_ENTRY_FORMAT);
		response.setDateFormat(df.toPattern());
		response.setCanAdd(true);

		boolean includeTest = context.hasPermission(Right.AllowTestSessions);
		for (DepartmentStatusType st : DepartmentStatusType.findAllForSession(includeTest))
			response.addStatus(new StatusOption(st.getUniqueId(), st.getLabel()));

		return response;
	}

	protected SessionCreateResponse save(SessionCreateRequest request, SessionContext context) {
		context.checkPermission(Right.AcademicSessionAdd);

		// Validate the descriptive fields before touching the database.
		String initiative = trimToNull(request.getAcademicInitiative());
		String year = trimToNull(request.getAcademicYear());
		String term = trimToNull(request.getAcademicTerm());
		if (initiative == null)
			throw new GwtRpcException("Academic initiative is required.");
		if (year == null)
			throw new GwtRpcException("Academic year is required.");
		try {
			Integer.parseInt(year);
		} catch (NumberFormatException e) {
			throw new GwtRpcException("Academic year must be a number.");
		}
		if (term == null)
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

		DepartmentStatusType status = DepartmentStatusTypeDAO.getInstance().get(request.getStatusTypeId());
		if (status == null)
			throw new GwtRpcException("Selected academic session status was not found.");

		Transaction tx = null;
		org.hibernate.Session hibSession = SessionDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			// Duplicate initiative / year / term guard, faithful to the legacy form.
			Session existing = Session.getSessionUsingInitiativeYearTerm(initiative, year, term, hibSession);
			if (existing != null)
				throw new GwtRpcException("An academic session with the same initiative, year and term already exists.");

			Session session = new Session();
			session.setAcademicInitiative(initiative);
			session.setAcademicYear(year);
			session.setAcademicTerm(term);
			session.setSessionBeginDateTime(begin);
			session.setClassesEndDateTime(classesEnd);
			session.setSessionEndDateTime(end);
			session.setExamBeginDate(examBegin);
			session.setEventBeginDate(eventBegin);
			session.setEventEndDate(eventEnd);
			session.setStatusType(status);
			// Mandatory NOT-NULL enrollment week boundaries: use the legacy form defaults.
			session.setLastWeekToEnroll(1);
			session.setLastWeekToChange(1);
			session.setLastWeekToDrop(4);
			// Optional (nullable) setup left unset on create -- configured later.
			session.setDefaultDatePattern(null);
			session.setHolidays((String) null);
			session.setNotificationsBeginDate(null);
			session.setNotificationsEndDate(null);
			session.setDefaultSectioningStatus(null);
			session.setDefaultClassDurationType(null);
			session.setDefaultInstructionalMethod(null);

			hibSession.persist(session);

			ChangeLog.addChange(
					hibSession,
					context,
					session,
					ChangeLog.Source.SESSION_EDIT,
					ChangeLog.Operation.CREATE,
					null,
					null);

			tx.commit();

			SessionCreateResponse response = new SessionCreateResponse();
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

	private static String trimToNull(String s) {
		if (s == null) return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static Date parseDate(Formats.Format<Date> df, String value, String what) {
		if (value == null || value.trim().isEmpty())
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
