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
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.gwt.shared.PatternEditInterface.CalendarDate;
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
import org.unitime.timetable.model.TimePatternDays;
import org.unitime.timetable.model.TimePatternTime;
import org.unitime.timetable.model.TimePref;
import org.unitime.timetable.model.dao.DatePatternDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.TimePatternDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;
import org.unitime.timetable.util.DateUtils;

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
 * SAVE additionally rewrites the DatePattern day bitmap (faithful port of
 * {@code DatePattern.setPatternAndOffset}) when {@code record.offeredDays} is
 * supplied, and the TimePattern day / start-slot grid (port of
 * {@code TimePatternEditForm.update}, gated on {@code TimePattern.isEditable()})
 * when {@code record.dayCodes}/{@code startSlots} are supplied.
 *
 * STILL DEFERRED (managed only on the legacy JSP page): DatePattern
 * numberOfWeeks, the department / parent / child (pattern-set) associations, and
 * creating a brand-new pattern (addable=false).
 *
 * @author Angular migration
 */
@GwtRpcImplements(PatternEditRequest.class)
public class PatternEditBackend implements GwtRpcImplementation<PatternEditRequest, PatternEditResponse> {
	protected static GwtConstants CONSTANTS = Localization.create(GwtConstants.class);

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

		// Build the shared session calendar once (all date patterns of a session
		// span the same date range). The running index (idx) is the same key math
		// the legacy DatePattern.setPatternAndOffset uses so LOAD/SAVE agree.
		int startMonth = 0, endMonth = 0, year = 0;
		if (session != null) {
			startMonth = session.getPatternStartMonth();
			endMonth = session.getPatternEndMonth();
			year = session.getSessionStartYear();
			int idx = session.getDayOfYear(1, startMonth);
			for (int m = startMonth; m <= endMonth; m++) {
				int daysOfMonth = DateUtils.getNrDaysOfMonth(m, year);
				int yr = DateUtils.calculateActualYear(m, year);
				int dispMonth = ((m % 12) + 12) % 12;
				for (int d = 1; d <= daysOfMonth; d++) {
					CalendarDate cd = new CalendarDate();
					cd.setKey(idx);
					cd.setYear(yr);
					cd.setMonth(dispMonth);
					cd.setDay(d);
					Calendar cal = Calendar.getInstance(Locale.US);
					cal.clear();
					cal.set(yr, dispMonth, d, 0, 0, 0);
					cd.setDayOfWeek((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7); // 0=Mon..6=Sun
					cd.setHoliday(session.getHoliday(d, m));
					response.addSessionDate(cd);
					idx++;
				}
			}
		}

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

			// Per-date offered flags, keyed by the same running index as the calendar.
			if (session != null) {
				List<Integer> offered = new ArrayList<Integer>();
				try {
					int idx = session.getDayOfYear(1, startMonth);
					for (int m = startMonth; m <= endMonth; m++) {
						int daysOfMonth = DateUtils.getNrDaysOfMonth(m, year);
						for (int d = 1; d <= daysOfMonth; d++) {
							if (dp.isOffered(d, m)) offered.add(idx);
							idx++;
						}
					}
				} catch (Exception e) {}
				r.setOfferedDays(offered);
			}
			r.setPatternEditable(canEdit);
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

			// Merge-on-update: descriptive fields plus (optionally) the day bitmap.
			// numberOfWeeks and the department / parent / child collections are
			// intentionally left untouched (deferred).
			dp.setName(record.getName().trim());
			if (record.getType() != null)
				dp.setType(record.getType());
			dp.setVisible(record.isVisible());

			// Day-bitmap: only rewritten when the client supplies the edited grid.
			// This is a faithful re-implementation of DatePattern.setPatternAndOffset
			// (the exact bit indexing the legacy datePatternEdit page stores).
			if (record.getOfferedDays() != null) {
				Session dpSession = dp.getSession();
				if (dpSession == null)
					throw new GwtRpcException("The date pattern has no academic session.");
				int startMonth = dpSession.getPatternStartMonth();
				int endMonth = dpSession.getPatternEndMonth();
				int year = dpSession.getSessionStartYear();
				Set<Integer> offered = new HashSet<Integer>(record.getOfferedDays());
				StringBuffer sb = null;
				int firstOne = 0, lastOne = 0;
				int idx = dpSession.getDayOfYear(1, startMonth);
				for (int m = startMonth; m <= endMonth; m++) {
					int daysOfMonth = DateUtils.getNrDaysOfMonth(m, year);
					for (int d = 1; d <= daysOfMonth; d++) {
						String off = offered.contains(idx) ? "1" : "0";
						if (sb != null || !off.equals("0")) {
							if (sb == null) { firstOne = idx; sb = new StringBuffer(); }
							sb.append(off);
						}
						if (!off.equals("0")) lastOne = idx;
						idx++;
					}
				}
				Calendar cal = Calendar.getInstance(Locale.US);
				cal.setTime(dpSession.getSessionBeginDateTime());
				if (sb != null) {
					dp.setPattern(sb.substring(0, lastOne - firstOne + 1));
					dp.setOffset(cal.get(Calendar.DAY_OF_YEAR) - firstOne - 1);
				} else {
					dp.setPattern("0"); dp.setOffset(0);
				}
			}

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

		// Encoding constants (supplied so the client never hard-codes them).
		int[] dayCodes = new int[Constants.NR_DAYS];
		for (int i = 0; i < Constants.NR_DAYS; i++) {
			dayCodes[i] = Constants.DAY_CODES[i];
			try { response.addDayName(CONSTANTS.shortDays()[i]); } catch (Exception e) { response.addDayName("?"); }
		}
		response.setDayCodes(dayCodes);
		response.setSlotLengthMin(Constants.SLOT_LENGTH_MIN);
		response.setFirstSlotTimeMin(Constants.FIRST_SLOT_TIME_MIN);
		response.setSlotsPerDay(Constants.SLOTS_PER_DAY);

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

			// Day / start-time grid (sorted for stable display).
			try {
				TreeSet<Integer> dc = new TreeSet<Integer>();
				for (TimePatternDays d : tp.getDays()) dc.add(d.getDayCode());
				r.setDayCodes(new ArrayList<Integer>(dc));
			} catch (Exception e) { r.setDayCodes(new ArrayList<Integer>()); }
			try {
				TreeSet<Integer> ss = new TreeSet<Integer>();
				for (TimePatternTime t : tp.getTimes()) ss.add(t.getStartSlot());
				r.setStartSlots(new ArrayList<Integer>(ss));
			} catch (Exception e) { r.setStartSlots(new ArrayList<Integer>()); }
			try { r.setGridEditable(canEdit && tp.isEditable()); } catch (Exception e) { r.setGridEditable(false); }

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

			// Merge-on-update: descriptive fields plus (optionally) the day/time grid.
			// The department associations are intentionally left untouched (deferred).
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

			// Day / start-time grid: only rewritten when the client supplies it AND
			// the pattern is editable (mirrors TimePatternEditForm.update, which
			// gates the grid on TimePattern.isEditable()). Faithful re-implementation
			// of that method's remove-old / persist-new / clear-affected-prefs flow.
			boolean gridProvided = record.getDayCodes() != null && record.getStartSlots() != null;
			if (gridProvided && tp.isEditable()) {
				int effType = (record.getType() != null ? record.getType()
						: (tp.getType() == null ? -1 : tp.getType()));
				boolean exact = effType == TimePatternType.ExactTime.ordinal();
				int nrMtg = (record.getNrMeetings() != null ? record.getNrMeetings()
						: (tp.getNrMeetings() == null ? 0 : tp.getNrMeetings()));
				int slotsPerMtg = (record.getSlotsPerMtg() != null ? record.getSlotsPerMtg()
						: (tp.getSlotsPerMtg() == null ? 0 : tp.getSlotsPerMtg()));

				int dayMask = 0;
				for (int i = 0; i < Constants.NR_DAYS; i++) dayMask |= Constants.DAY_CODES[i];

				TreeSet<TimePatternDays> newDays = new TreeSet<TimePatternDays>();
				for (Integer dc : record.getDayCodes()) {
					if (dc == null) continue;
					if (dc <= 0 || (dc & ~dayMask) != 0)
						throw new GwtRpcException("Invalid day combination.");
					int nrDays = 0;
					for (int i = 0; i < Constants.NR_DAYS; i++)
						if ((dc & Constants.DAY_CODES[i]) != 0) nrDays++;
					if (!exact && nrMtg > 0 && nrDays != nrMtg)
						throw new GwtRpcException("Each day combination must select exactly " + nrMtg + " day(s).");
					TimePatternDays d = new TimePatternDays();
					d.setDayCode(dc);
					if (!newDays.add(d))
						throw new GwtRpcException("Duplicate day combination.");
				}

				TreeSet<TimePatternTime> newTimes = new TreeSet<TimePatternTime>();
				for (Integer ss : record.getStartSlots()) {
					if (ss == null) continue;
					if (ss < 0 || ss >= Constants.SLOTS_PER_DAY)
						throw new GwtRpcException("Invalid start time.");
					if (slotsPerMtg > 0 && ss + slotsPerMtg > Constants.SLOTS_PER_DAY)
						throw new GwtRpcException("A start time would run past midnight.");
					TimePatternTime t = new TimePatternTime();
					t.setStartSlot(ss);
					if (!newTimes.add(t))
						throw new GwtRpcException("Duplicate start time.");
				}

				int oldDays = tp.getDays().size();
				int oldTimes = tp.getTimes().size();
				for (TimePatternTime t : new ArrayList<TimePatternTime>(tp.getTimes()))
					hibSession.remove(t);
				for (TimePatternDays d : new ArrayList<TimePatternDays>(tp.getDays()))
					hibSession.remove(d);
				tp.setTimes(new HashSet<TimePatternTime>(newTimes));
				tp.setDays(new HashSet<TimePatternDays>(newDays));
				for (TimePatternTime t : tp.getTimes())
					hibSession.persist(t);
				for (TimePatternDays d : tp.getDays())
					hibSession.persist(d);
				if (tp.getSession() != null && tp.getSession().getStatusType().isAllowRollForward()) {
					if (oldDays != tp.getDays().size() || oldTimes != tp.getTimes().size()) {
						for (TimePref tpref : hibSession.createQuery(
								"from TimePref tp where tp.timePattern.uniqueId = :tpid", TimePref.class)
								.setParameter("tpid", tp.getUniqueId()).list()) {
							tpref.setPreference(null);
							hibSession.merge(tpref);
						}
					}
				}
			}

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
