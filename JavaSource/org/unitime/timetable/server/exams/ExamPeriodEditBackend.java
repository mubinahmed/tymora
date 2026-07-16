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
package org.unitime.timetable.server.exams;

import java.util.Date;
import java.util.List;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamPeriodEditInterface.ExamPeriodEditRequest;
import org.unitime.timetable.gwt.shared.ExamPeriodEditInterface.ExamPeriodEditResponse;
import org.unitime.timetable.gwt.shared.ExamPeriodEditInterface.ExamPeriodRecord;
import org.unitime.timetable.gwt.shared.ExamPeriodEditInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.ExamPeriodEditInterface.Operation;
import org.unitime.timetable.gwt.shared.ExamPeriodEditInterface.PrefLevelInfo;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.ExamPeriodDAO;
import org.unitime.timetable.model.dao.ExamTypeDAO;
import org.unitime.timetable.model.dao.PreferenceLevelDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;
import org.unitime.timetable.util.Formats;

/**
 * Create / Edit backend for {@link ExamPeriod} - the editable sibling of the
 * read-only {@link ExamPeriodListBackend} (both back the legacy
 * examPeriodEdit.action Examination Periods Struts page). LOAD returns every
 * examination period of the current academic session (optionally filtered by
 * exam type) projected to editable records plus the exam-type and
 * preference-level selectors; SAVE creates or updates one period; DELETE
 * removes one period.
 *
 * Field encoding mirrors the legacy {@code ExamPeriodEditForm} exactly:
 * <ul>
 *   <li>date is a DATE_ENTRY_FORMAT string; the persisted date offset is
 *       derived by {@link ExamPeriod#setStartDate(Date)} from the session exam
 *       begin date.</li>
 *   <li>start is an HHMM integer; the start slot is
 *       {@code (hour*60 + min - FIRST_SLOT_TIME_MIN) / SLOT_LENGTH_MIN}.</li>
 *   <li>length and the event start/stop offsets are entered in minutes and
 *       stored in slots ({@code minutes / SLOT_LENGTH_MIN}).</li>
 * </ul>
 * On update the entity is loaded and only the rendered fields are re-set
 * (merge-on-update); no unmanaged collections are touched. Every operation is
 * gated by {@link Right#ExaminationPeriods} for the current academic session
 * and a CREATE/UPDATE/DELETE {@link ChangeLog} entry is written, matching the
 * legacy action.
 *
 * DEFERRED (not implemented here, still available on the legacy page): the
 * multi-period "auto-setup" wizard, and deletion of a period already assigned
 * to exams (the legacy cascade that unassigns those exams is intentionally not
 * replicated - such a delete is rejected with a message instead of risking
 * corruption of exam assignments).
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamPeriodEditRequest.class)
public class ExamPeriodEditBackend implements GwtRpcImplementation<ExamPeriodEditRequest, ExamPeriodEditResponse> {

	@Override
	public ExamPeriodEditResponse execute(ExamPeriodEditRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.ExaminationPeriods);

		switch (request.getOperation() == null ? Operation.LOAD : request.getOperation()) {
		case SAVE:
			save(request.getRecord(), sessionId, context);
			break;
		case DELETE:
			delete(request.getRecord(), sessionId, context);
			break;
		case LOAD:
		default:
			break;
		}

		return load(request, sessionId, context);
	}

	protected ExamPeriodEditResponse load(ExamPeriodEditRequest request, Long sessionId, SessionContext context) {
		ExamPeriodEditResponse response = new ExamPeriodEditResponse();
		response.setTitle("Examination Periods");

		boolean canEdit = context.hasPermission(sessionId, "Session", Right.ExaminationPeriods);
		response.setEditable(canEdit);
		response.setAddable(canEdit);
		response.setDeletable(canEdit);

		// Exam-type selector (mirror the legacy examPeriodEdit.action selector).
		List<ExamType> types = ExamType.findAll();
		for (ExamType t : types)
			response.addExamType(new ExamTypeInfo(t.getUniqueId(), t.getLabel()));

		// Preference-level selector (mirror ExamPeriodEditForm.getPrefLevels(): all but Required).
		for (PreferenceLevel level : PreferenceLevel.getPreferenceLevelList()) {
			if (PreferenceLevel.sRequired.equals(level.getPrefProlog())) continue;
			response.addPrefLevel(new PrefLevelInfo(level.getUniqueId(), level.getPrefName(), level.getPrefProlog()));
		}

		// Resolve the (optional) exam-type filter.
		Long examTypeId = request.getExamTypeId();
		if (examTypeId != null) {
			boolean ok = false;
			for (ExamType t : types)
				if (t.getUniqueId().equals(examTypeId)) { ok = true; break; }
			if (!ok) examTypeId = null;
		}
		response.setExamTypeId(examTypeId);

		// Default new-period date = session exam begin date (mirror ExamPeriodEditForm.load(null)).
		Formats.Format<Date> df = Formats.getDateFormat(Formats.Pattern.DATE_ENTRY_FORMAT);
		try {
			Session session = SessionDAO.getInstance().get(sessionId);
			if (session != null && session.getExamBeginDate() != null)
				response.setDefaultDate(df.format(session.getExamBeginDate()));
		} catch (Exception e) {}

		for (ExamPeriod ep : ExamPeriod.findAll(sessionId, examTypeId)) {
			ExamPeriodRecord r = new ExamPeriodRecord();
			r.setId(ep.getUniqueId());

			try { r.setExamTypeId(ep.getExamType() == null ? null : ep.getExamType().getUniqueId()); } catch (Exception e) {}
			try { r.setExamTypeLabel(ep.getExamType() == null ? "" : ep.getExamType().getLabel()); } catch (Exception e) { r.setExamTypeLabel(""); }

			try { r.setDate(df.format(ep.getStartDate())); } catch (Exception e) {}
			try { r.setDateLabel(ep.getStartDateLabel()); } catch (Exception e) { r.setDateLabel(""); }

			try { r.setStart(ep.getStartHour() * 100 + ep.getStartMinute()); } catch (Exception e) {}
			try { r.setStartLabel(ep.getStartTimeLabel()); } catch (Exception e) { r.setStartLabel(""); }
			try { r.setEndLabel(ep.getEndTimeLabel()); } catch (Exception e) { r.setEndLabel(""); }

			try { r.setLength(ep.getLength() == null ? null : Constants.SLOT_LENGTH_MIN * ep.getLength()); } catch (Exception e) {}
			try { r.setStartOffset(ep.getEventStartOffset() == null ? 0 : Constants.SLOT_LENGTH_MIN * ep.getEventStartOffset()); } catch (Exception e) {}
			try { r.setStopOffset(ep.getEventStopOffset() == null ? 0 : Constants.SLOT_LENGTH_MIN * ep.getEventStopOffset()); } catch (Exception e) {}

			try {
				PreferenceLevel pref = ep.getPrefLevel();
				if (pref != null) {
					r.setPrefLevelId(pref.getUniqueId());
					r.setPrefName(PreferenceLevel.sNeutral.equals(pref.getPrefProlog()) ? "" : pref.getPrefName());
				}
			} catch (Exception e) {}

			boolean used = false;
			try { used = ep.isUsed(); } catch (Exception e) {}
			r.setUsed(used);
			r.setEditable(canEdit && !used);

			response.addRecord(r);
		}

		return response;
	}

	protected void save(ExamPeriodRecord record, Long sessionId, SessionContext context) {
		if (record == null)
			throw new GwtRpcException("No examination period provided.");

		// --- validate (mirror ExamPeriodEditForm.validate for a single period) ---
		Formats.Format<Date> df = Formats.getDateFormat(Formats.Pattern.DATE_ENTRY_FORMAT);
		Date date = null;
		try { date = df.parse(record.getDate()); } catch (Exception e) {}
		if (date == null)
			throw new GwtRpcException("The examination date is not valid.");

		if (record.getExamTypeId() == null || record.getExamTypeId() < 0)
			throw new GwtRpcException("An examination type is required.");

		Integer start = record.getStart();
		if (start == null || start <= 0)
			throw new GwtRpcException("A start time is required.");
		int hour = start / 100;
		int min = start % 100;
		if (hour >= 24)
			throw new GwtRpcException("The start time hour " + hour + " is not valid.");
		if (min >= 60)
			throw new GwtRpcException("The start time minute " + min + " is not valid.");
		if ((min % Constants.SLOT_LENGTH_MIN) != 0)
			throw new GwtRpcException("The start time must be divisible by " + Constants.SLOT_LENGTH_MIN + " minutes.");

		Integer length = record.getLength();
		if (length == null || length <= 0)
			throw new GwtRpcException("A length is required.");
		if ((length % Constants.SLOT_LENGTH_MIN) != 0)
			throw new GwtRpcException("The length must be divisible by " + Constants.SLOT_LENGTH_MIN + " minutes.");

		Integer startOffset = record.getStartOffset();
		if (startOffset != null) {
			if (startOffset < 0)
				throw new GwtRpcException("The event start offset cannot be negative.");
			if ((startOffset % Constants.SLOT_LENGTH_MIN) != 0)
				throw new GwtRpcException("The event start offset must be divisible by " + Constants.SLOT_LENGTH_MIN + " minutes.");
		}
		Integer stopOffset = record.getStopOffset();
		if (stopOffset != null) {
			if (stopOffset < 0)
				throw new GwtRpcException("The event stop offset cannot be negative.");
			if ((stopOffset % Constants.SLOT_LENGTH_MIN) != 0)
				throw new GwtRpcException("The event stop offset must be divisible by " + Constants.SLOT_LENGTH_MIN + " minutes.");
		}

		Transaction tx = null;
		org.hibernate.Session hibSession = ExamPeriodDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			Session session = SessionDAO.getInstance().get(sessionId, hibSession);
			if (session == null || session.getExamBeginDate() == null)
				throw new GwtRpcException("The academic session has no examination begin date.");

			ExamType type = ExamTypeDAO.getInstance().get(record.getExamTypeId(), hibSession);
			if (type == null)
				throw new GwtRpcException("The selected examination type no longer exists.");

			// Duplicate check (mirror ExamPeriodEditForm.validate).
			long diff = date.getTime() - session.getExamBeginDate().getTime();
			int dateOffset = (int) Math.round(diff / (1000.0 * 60 * 60 * 24));
			int slot = (hour * 60 + min - Constants.FIRST_SLOT_TIME_MIN) / Constants.SLOT_LENGTH_MIN;
			ExamPeriod duplicate = ExamPeriod.findByDateStart(session.getUniqueId(), dateOffset, slot, type.getUniqueId());
			if (duplicate != null && !duplicate.getUniqueId().equals(record.getId()))
				throw new GwtRpcException("An examination period with the same date, start time and type already exists.");

			ExamPeriod ep = null;
			if (record.getId() != null && record.getId() >= 0)
				ep = ExamPeriodDAO.getInstance().get(record.getId(), hibSession);
			boolean isNew = (ep == null);
			if (isNew) {
				ep = new ExamPeriod();
				ep.setSession(session);
			}

			// Session must be set before setStartDate (it derives the date offset).
			ep.setSession(session);
			ep.setStartDate(date);
			ep.setStartSlot(slot);
			ep.setLength(length / Constants.SLOT_LENGTH_MIN);
			ep.setExamType(type);
			ep.setEventStartOffset(startOffset == null ? Integer.valueOf(0) : Integer.valueOf(startOffset / Constants.SLOT_LENGTH_MIN));
			ep.setEventStopOffset(stopOffset == null ? Integer.valueOf(0) : Integer.valueOf(stopOffset / Constants.SLOT_LENGTH_MIN));

			PreferenceLevel pref = null;
			if (record.getPrefLevelId() != null)
				pref = PreferenceLevelDAO.getInstance().get(record.getPrefLevelId(), hibSession);
			if (pref == null)
				pref = PreferenceLevel.getPreferenceLevel(PreferenceLevel.sNeutral);
			ep.setPrefLevel(pref);

			if (isNew)
				hibSession.persist(ep);
			else
				ep = (ExamPeriod) hibSession.merge(ep);

			ChangeLog.addChange(
					hibSession,
					context,
					ep,
					ChangeLog.Source.EXAM_PERIOD_EDIT,
					isNew ? ChangeLog.Operation.CREATE : ChangeLog.Operation.UPDATE,
					null,
					null);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	protected void delete(ExamPeriodRecord record, Long sessionId, SessionContext context) {
		if (record == null || record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("No examination period provided.");

		Transaction tx = null;
		org.hibernate.Session hibSession = ExamPeriodDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			ExamPeriod ep = ExamPeriodDAO.getInstance().get(record.getId(), hibSession);
			if (ep == null)
				throw new GwtRpcException("The examination period no longer exists.");

			// Conservative: refuse to delete a period that is assigned to exams.
			// The legacy cascade that unassigns those exams is deferred to avoid
			// corrupting exam assignments.
			boolean used = false;
			try { used = ep.isUsed(); } catch (Exception e) {}
			if (used)
				throw new GwtRpcException("This examination period is assigned to one or more exams and cannot be deleted here. Unassign the exams first on the legacy page.");

			ChangeLog.addChange(
					hibSession,
					context,
					ep,
					ChangeLog.Source.EXAM_PERIOD_EDIT,
					ChangeLog.Operation.DELETE,
					null,
					null);

			hibSession.remove(ep);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}
}
