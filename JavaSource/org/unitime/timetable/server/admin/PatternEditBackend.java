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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.PatternEditInterface.Kind;
import org.unitime.timetable.gwt.shared.PatternEditInterface.Operation;
import org.unitime.timetable.gwt.shared.PatternEditInterface.PatternEditRequest;
import org.unitime.timetable.gwt.shared.PatternEditInterface.PatternEditResponse;
import org.unitime.timetable.gwt.shared.PatternEditInterface.PatternRecord;
import org.unitime.timetable.gwt.shared.PatternEditInterface.PatternTypeOption;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.DatePattern.DatePatternType;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.TimePattern;
import org.unitime.timetable.model.TimePattern.TimePatternType;
import org.unitime.timetable.model.dao.DatePatternDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.TimePatternDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Create / Edit backend for the legacy Struts Date Pattern
 * (datePatternEdit.action) and Time Pattern (timePatternEdit.action) pages,
 * unified behind {@link PatternEditRequest}. The {@code kind} discriminator
 * (DATE|TIME) selects the entity.
 *
 * LOAD returns all patterns of the current academic session, the available
 * type options and the permission flags. SAVE merges the descriptive fields of
 * one existing pattern (DatePattern: name, type, visible; TimePattern: name,
 * type, nrMeetings, minPerMtg, slotsPerMtg, breakTime, visible). DELETE removes
 * one pattern of the current session, guarded against patterns that are in use,
 * the default date pattern and patterns still holding deferred associations.
 *
 * Session id is resolved from {@code context.getUser().getCurrentAcademicSessionId()}
 * and every operation is permission-gated with {@code Right.DatePatterns} /
 * {@code Right.TimePatterns} (both Session qualified). ChangeLog entries are
 * written with {@code Source.DATE_PATTERN_EDIT} / {@code Source.TIME_PATTERN_EDIT}
 * to mirror the legacy pages.
 *
 * DEFERRED (managed only on the legacy JSP page): the DatePattern day bitmap
 * (pattern / offset / numberOfWeeks) and the TimePattern day / start-slot /
 * exact-time grid, plus the department / parent / child (pattern-set)
 * associations. Because those complex bit-encoded / relational sub-parts are
 * deferred, creating a brand-new pattern (which requires a bitmap / grid) is
 * not offered here (addable=false); SAVE edits existing patterns only.
 *
 * @author Angular migration
 */
@GwtRpcImplements(PatternEditRequest.class)
public class PatternEditBackend implements GwtRpcImplementation<PatternEditRequest, PatternEditResponse> {

	@Override
	public PatternEditResponse execute(PatternEditRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		Kind kind = (request.getKind() == null ? Kind.DATE : request.getKind());
		Right right = (kind == Kind.TIME ? Right.TimePatterns : Right.DatePatterns);
		context.checkPermission(sessionId, "Session", right);

		switch (request.getOperation() == null ? Operation.LOAD : request.getOperation()) {
		case SAVE:
			if (kind == Kind.TIME) saveTime(request.getRecord(), sessionId, context);
			else saveDate(request.getRecord(), sessionId, context);
			break;
		case DELETE:
			if (kind == Kind.TIME) deleteTime(request.getRecord(), sessionId, context);
			else deleteDate(request.getRecord(), sessionId, context);
			break;
		case LOAD:
		default:
			break;
		}

		return (kind == Kind.TIME ? loadTime(sessionId, context) : loadDate(sessionId, context));
	}

	// ------------------------------------------------------------------ DATE

	protected PatternEditResponse loadDate(Long sessionId, SessionContext context) {
		PatternEditResponse response = new PatternEditResponse();
		response.setKind(Kind.DATE);
		boolean canEdit = context.hasPermission(sessionId, "Session", Right.DatePatterns);
		response.setEditable(canEdit);
		response.setAddable(false); // creation requires the deferred day-bitmap editor
		response.setDeletable(canEdit);

		for (DatePatternType t : DatePatternType.values())
			response.addType(new PatternTypeOption(t.ordinal(), safeLabel(t)));

		boolean includeExtended = context.getUser().getCurrentAuthority().hasRight(Right.ExtendedDatePatterns);
		Set<DatePattern> used = DatePattern.findAllUsed(sessionId);
		Session session = SessionDAO.getInstance().get(sessionId);
		DatePattern defaultDp = (session == null ? null : session.getDefaultDatePattern());

		for (DatePattern dp : DatePattern.findAll(sessionId, includeExtended, null, null)) {
			PatternRecord r = new PatternRecord();
			r.setId(dp.getUniqueId());
			r.setName(dp.getName());
			r.setType(dp.getType());
			try { r.setTypeLabel(dp.getDatePatternType() == null ? "" : dp.getDatePatternType().getLabel()); }
			catch (Exception e) { r.setTypeLabel(""); }
			r.setVisible(Boolean.TRUE.equals(dp.isVisible()));
			r.setUsed(used.contains(dp));
			r.setDefault(dp.equals(defaultDp));
			try { r.setPatternPreview(dp.getPatternString()); } catch (Exception e) { r.setPatternPreview(""); }
			try {
				Float w = dp.getNumberOfWeeks();
				r.setNumberOfWeeks(w == null ? "" : String.valueOf(w));
			} catch (Exception e) { r.setNumberOfWeeks(""); }
			response.addRecord(r);
		}
		return response;
	}

	protected void saveDate(PatternRecord record, Long sessionId, SessionContext context) {
		if (record == null)
			throw new GwtRpcException("No date pattern provided.");
		if (record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("Creating a new date pattern is not supported here; it requires the day-pattern editor on the legacy page.");
		if (record.getName() == null || record.getName().trim().isEmpty())
			throw new GwtRpcException("Name is required.");
		if (record.getType() != null && (record.getType() < 0 || record.getType() >= DatePatternType.values().length))
			throw new GwtRpcException("Invalid date pattern type.");

		Transaction tx = null;
		org.hibernate.Session hibSession = DatePatternDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			DatePattern dp = DatePatternDAO.getInstance().get(record.getId(), hibSession);
			if (dp == null)
				throw new GwtRpcException("The date pattern no longer exists.");
			if (dp.getSession() == null || !sessionId.equals(dp.getSession().getUniqueId()))
				throw new GwtRpcException("The date pattern does not belong to the current academic session.");

			// Merge-on-update: only the rendered descriptive fields are touched.
			// pattern / offset / numberOfWeeks and the department / parent / child
			// collections are intentionally left untouched (deferred).
			dp.setName(record.getName().trim());
			if (record.getType() != null)
				dp.setType(record.getType());
			dp.setVisible(record.isVisible());

			hibSession.merge(dp);

			ChangeLog.addChange(hibSession, context, dp, ChangeLog.Source.DATE_PATTERN_EDIT,
					ChangeLog.Operation.UPDATE, null, null);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	protected void deleteDate(PatternRecord record, Long sessionId, SessionContext context) {
		if (record == null || record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("No date pattern provided.");

		Transaction tx = null;
		org.hibernate.Session hibSession = DatePatternDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			DatePattern dp = DatePatternDAO.getInstance().get(record.getId(), hibSession);
			if (dp == null)
				throw new GwtRpcException("The date pattern no longer exists.");
			if (dp.getSession() == null || !sessionId.equals(dp.getSession().getUniqueId()))
				throw new GwtRpcException("The date pattern does not belong to the current academic session.");
			if (dp.equals(dp.getSession().getDefaultDatePattern()))
				throw new GwtRpcException("The default date pattern cannot be deleted.");
			if (DatePattern.findAllUsed(sessionId).contains(dp))
				throw new GwtRpcException("The date pattern is in use and cannot be deleted.");
			if ((dp.getParents() != null && !dp.getParents().isEmpty())
					|| (dp.getChildren() != null && !dp.getChildren().isEmpty()))
				throw new GwtRpcException("The date pattern is part of an alternate pattern set; manage it on the legacy page.");

			// Clear the (owning-side) department links before removing the pattern.
			if (dp.getDepartments() != null) {
				for (Department d : new ArrayList<Department>(dp.getDepartments())) {
					if (d.getDatePatterns() != null && d.getDatePatterns().remove(dp))
						hibSession.merge(d);
				}
			}

			ChangeLog.addChange(hibSession, context, dp, ChangeLog.Source.DATE_PATTERN_EDIT,
					ChangeLog.Operation.DELETE, null, null);

			hibSession.remove(dp);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	// ------------------------------------------------------------------ TIME

	protected PatternEditResponse loadTime(Long sessionId, SessionContext context) {
		PatternEditResponse response = new PatternEditResponse();
		response.setKind(Kind.TIME);
		boolean canEdit = context.hasPermission(sessionId, "Session", Right.TimePatterns);
		response.setEditable(canEdit);
		response.setAddable(false); // creation requires the deferred day/time-grid editor
		response.setDeletable(canEdit);

		for (TimePatternType t : TimePatternType.values())
			response.addType(new PatternTypeOption(t.ordinal(), safeLabel(t)));

		@SuppressWarnings("unchecked")
		Set<TimePattern> used = TimePattern.findAllUsed(sessionId);

		for (TimePattern tp : TimePattern.findAll(sessionId, null)) {
			PatternRecord r = new PatternRecord();
			r.setId(tp.getUniqueId());
			r.setName(tp.getName());
			r.setType(tp.getType());
			try { r.setTypeLabel(tp.getTimePatternType() == null ? "" : tp.getTimePatternType().getLabel()); }
			catch (Exception e) { r.setTypeLabel(""); }
			r.setVisible(Boolean.TRUE.equals(tp.isVisible()));
			r.setUsed(used.contains(tp));
			r.setNrMeetings(tp.getNrMeetings());
			r.setMinPerMtg(tp.getMinPerMtg());
			r.setSlotsPerMtg(tp.getSlotsPerMtg());
			try { r.setBreakTime(tp.getBreakTime()); } catch (Exception e) { r.setBreakTime(null); }
			response.addRecord(r);
		}
		return response;
	}

	protected void saveTime(PatternRecord record, Long sessionId, SessionContext context) {
		if (record == null)
			throw new GwtRpcException("No time pattern provided.");
		if (record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("Creating a new time pattern is not supported here; it requires the day/time-grid editor on the legacy page.");
		if (record.getName() == null || record.getName().trim().isEmpty())
			throw new GwtRpcException("Name is required.");
		if (record.getType() != null && (record.getType() < 0 || record.getType() >= TimePatternType.values().length))
			throw new GwtRpcException("Invalid time pattern type.");

		Transaction tx = null;
		org.hibernate.Session hibSession = TimePatternDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			TimePattern tp = TimePatternDAO.getInstance().get(record.getId(), hibSession);
			if (tp == null)
				throw new GwtRpcException("The time pattern no longer exists.");
			if (tp.getSession() == null || !sessionId.equals(tp.getSession().getUniqueId()))
				throw new GwtRpcException("The time pattern does not belong to the current academic session.");

			// Merge-on-update: only the rendered descriptive fields are touched.
			// The day / start-slot grid (days / times) and the department
			// associations are intentionally left untouched (deferred).
			tp.setName(record.getName().trim());
			if (record.getType() != null)
				tp.setType(record.getType());
			if (record.getNrMeetings() != null)
				tp.setNrMeetings(record.getNrMeetings());
			if (record.getMinPerMtg() != null)
				tp.setMinPerMtg(record.getMinPerMtg());
			if (record.getSlotsPerMtg() != null)
				tp.setSlotsPerMtg(record.getSlotsPerMtg());
			tp.setBreakTime(record.getBreakTime());
			tp.setVisible(record.isVisible());

			hibSession.merge(tp);

			ChangeLog.addChange(hibSession, context, tp, ChangeLog.Source.TIME_PATTERN_EDIT,
					ChangeLog.Operation.UPDATE, null, null);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	protected void deleteTime(PatternRecord record, Long sessionId, SessionContext context) {
		if (record == null || record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("No time pattern provided.");

		Transaction tx = null;
		org.hibernate.Session hibSession = TimePatternDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			TimePattern tp = TimePatternDAO.getInstance().get(record.getId(), hibSession);
			if (tp == null)
				throw new GwtRpcException("The time pattern no longer exists.");
			if (tp.getSession() == null || !sessionId.equals(tp.getSession().getUniqueId()))
				throw new GwtRpcException("The time pattern does not belong to the current academic session.");
			if (TimePattern.findAllUsed(sessionId).contains(tp))
				throw new GwtRpcException("The time pattern is in use and cannot be deleted.");

			// Clear the (owning-side) department links before removing the pattern.
			if (tp.getDepartments() != null) {
				for (Object o : new ArrayList<Object>(tp.getDepartments())) {
					Department d = (Department) o;
					if (d.getTimePatterns() != null && d.getTimePatterns().remove(tp))
						hibSession.merge(d);
				}
			}

			ChangeLog.addChange(hibSession, context, tp, ChangeLog.Source.TIME_PATTERN_EDIT,
					ChangeLog.Operation.DELETE, null, null);

			hibSession.remove(tp);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	private static String safeLabel(DatePatternType t) {
		try { return t.getLabel(); } catch (Exception e) { return t.name(); }
	}

	private static String safeLabel(TimePatternType t) {
		try { return t.getLabel(); } catch (Exception e) { return t.name(); }
	}
}
