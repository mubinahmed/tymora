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

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.unitime.localization.impl.Localization;
import org.unitime.localization.messages.ExaminationMessages;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.gwt.shared.ExamRoomAvailabilityInterface.ExamRoomAvailabilityRequest;
import org.unitime.timetable.gwt.shared.ExamRoomAvailabilityInterface.ExamRoomAvailabilityResponse;
import org.unitime.timetable.gwt.shared.ExamRoomAvailabilityInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.interfaces.RoomAvailabilityInterface;
import org.unitime.timetable.interfaces.RoomAvailabilityInterface.TimeBlock;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.ExamTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.exam.ui.ExamAssignment;
import org.unitime.timetable.util.Constants;
import org.unitime.timetable.util.Formats;
import org.unitime.timetable.util.RoomAvailability;

/**
 * Read-only backing bean for the legacy roomAvailability.action (Examination
 * Room Availability) Struts page. Faithful port of
 * {@code RoomAvailabilityAction} projected to string {@link Row} cells:
 * <ul>
 *   <li>availability mode ({@code compare=false}) mirrors {@code getTable(...)}
 *       — external room-availability time blocks overlapping the exam periods
 *       (8 columns);</li>
 *   <li>comparison mode ({@code compare=true}) mirrors {@code getCompareTable(...)}
 *       — those blocks matched against the committed examination assignments to
 *       surface mismatches (9 columns).</li>
 * </ul>
 * Exam assignments are read from persisted data (the in-memory exam solver is
 * not consulted, as in {@link AssignedExamsBackend}). Permission gated by
 * {@link Right#RoomAvailability} (Session qualified), mirroring the legacy
 * action. PDF/CSV export and the HTML mismatch highlighting remain on the legacy
 * page (Angular renders plain cells + a CSV export client-side). Additive:
 * introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamRoomAvailabilityRequest.class)
public class ExamRoomAvailabilityBackend implements GwtRpcImplementation<ExamRoomAvailabilityRequest, ExamRoomAvailabilityResponse> {
	protected static final ExaminationMessages MSG = Localization.create(ExaminationMessages.class);
	protected static final GwtConstants GWT_CONST = Localization.create(GwtConstants.class);

	@Override
	public ExamRoomAvailabilityResponse execute(ExamRoomAvailabilityRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.RoomAvailability);

		ExamRoomAvailabilityResponse response = new ExamRoomAvailabilityResponse();
		response.setTitle(MSG.sectRoomAvailability());
		response.setCompare(request.isCompare());

		// Applicable exam types (mirror LookupTables.setupExamTypes selector).
		List<ExamType> types = ExamType.findAllApplicable(context.getUser(),
				DepartmentStatusType.Status.ExamView, DepartmentStatusType.Status.ExamTimetable);
		for (ExamType t : types)
			response.addExamType(new ExamTypeInfo(t.getUniqueId(), t.getLabel()));

		// Resolve the selected exam type (fall back to the first applicable one).
		Long examTypeId = request.getExamTypeId();
		if (examTypeId != null) {
			boolean ok = false;
			for (ExamType t : types)
				if (t.getUniqueId().equals(examTypeId)) { ok = true; break; }
			if (!ok) examTypeId = null;
		}
		if (examTypeId == null && !types.isEmpty())
			examTypeId = types.get(0).getUniqueId();
		response.setExamTypeId(examTypeId);

		for (String c : (request.isCompare() ? COMPARE_COLUMNS : AVAILABILITY_COLUMNS))
			response.addColumn(c);

		if (examTypeId == null)
			return response;

		Session session = SessionDAO.getInstance().get(sessionId);
		ExamType examType = ExamTypeDAO.getInstance().get(examTypeId);
		if (session == null || examType == null)
			return response;

		RoomAvailabilityInterface ra = RoomAvailability.getInstance();
		if (ra == null) {
			// No external room-availability service configured -> nothing to display.
			response.setServiceAvailable(false);
			return response;
		}

		Date[] bounds = ExamPeriod.getBounds(session, examTypeId);
		String exclude = (request.isIncludeExams() ? null :
				(examType.getType() == ExamType.sExamTypeFinal ? RoomAvailabilityInterface.sFinalExamType : RoomAvailabilityInterface.sMidtermExamType));
		if (bounds != null)
			ra.activate(session.getUniqueId(), bounds[0], bounds[1], exclude, request.isRefresh());

		if (request.isCompare())
			fillCompareTable(response, ra, request, session, examType, bounds);
		else
			fillAvailabilityTable(response, ra, request, session, examType, bounds);

		return response;
	}

	private static final String[] AVAILABILITY_COLUMNS = new String[] {
			"Room", "Room Capacity", "Examination Capacity", "Event", "Event Type", "Date", "Start Time", "End Time" };

	private static final String[] COMPARE_COLUMNS = new String[] {
			"Room", "Room Capacity", "Examination Capacity",
			"Examination", "Examination Date", "Examination Time",
			"Event", "Event Date", "Event Time" };

	/** Room-name filter, mirroring RoomAvailabilityAction.match(String). */
	private boolean match(String filter, String name) {
		if (filter == null || filter.trim().length() == 0) return true;
		String n = name.toUpperCase();
		StringTokenizer stk1 = new StringTokenizer(filter.toUpperCase(), ";");
		while (stk1.hasMoreTokens()) {
			StringTokenizer stk2 = new StringTokenizer(stk1.nextToken(), " ,");
			boolean match = true;
			while (match && stk2.hasMoreTokens()) {
				String token = stk2.nextToken().trim();
				if (token.length() == 0) continue;
				if (token.indexOf('*') >= 0 || token.indexOf('?') >= 0) {
					try {
						String tokenRegExp = "\\s+" + token.replaceAll("\\.", "\\.").replaceAll("\\?", ".+").replaceAll("\\*", ".*") + "\\s";
						if (!Pattern.compile(tokenRegExp).matcher(" " + n + " ").find()) match = false;
					} catch (PatternSyntaxException e) { match = false; }
				} else if (n.indexOf(token) < 0) match = false;
			}
			if (match) return true;
		}
		return false;
	}

	private String time(Formats.Format<Date> timeFormat, Date d) {
		return timeFormat.format(d).replaceAll("AM", GWT_CONST.timeShortAm()).replaceAll("PM", GWT_CONST.timeShortPm());
	}

	/** Faithful port of RoomAvailabilityAction.getTable(...). */
	private void fillAvailabilityTable(ExamRoomAvailabilityResponse response, RoomAvailabilityInterface ra,
			ExamRoomAvailabilityRequest request, Session session, ExamType examType, Date[] bounds) {
		Long sessionId = session.getUniqueId();
		TreeSet<ExamPeriod> periods = ExamPeriod.findAll(sessionId, examType.getUniqueId());
		if (periods.isEmpty()) {
			response.setWarning(MSG.warnNoExaminationPeriods());
			return;
		}
		if (bounds == null) return;
		Formats.Format<Date> dateFormat = Formats.getDateFormat(Formats.Pattern.DATE_MEETING);
		Formats.Format<Date> timeFormat = Formats.getDateFormat(Formats.Pattern.TIME_SHORT);
		String exclude = (request.isIncludeExams() ? null :
				(examType.getType() == ExamType.sExamTypeFinal ? RoomAvailabilityInterface.sFinalExamType : RoomAvailabilityInterface.sMidtermExamType));
		String ts = null;
		try {
			for (Location location : Location.findAllExamLocations(sessionId, examType.getUniqueId())) {
				if (!match(request.getFilter(), location.getLabel())) continue;
				Collection<TimeBlock> events = ra.getRoomAvailability(location.getUniqueId(), bounds[0], bounds[1], exclude);
				if (events == null) continue;
				if (ts == null) ts = ra.getTimeStamp(bounds[0], bounds[1], exclude);
				for (TimeBlock event : events) {
					boolean overlaps = false;
					for (ExamPeriod period : periods)
						if (period.overlap(event)) { overlaps = true; break; }
					if (!overlaps) continue;
					Row r = response.addRow(location.getUniqueId());
					r.add(location.getLabel());
					r.add(location.getCapacity().toString());
					r.add(location.getExamCapacity().toString());
					r.add(event.getEventName());
					r.add(event.getEventType());
					r.add(dateFormat.format(event.getStartTime()));
					r.add(time(timeFormat, event.getStartTime()));
					r.add(time(timeFormat, event.getEndTime()));
				}
			}
			if (ts != null) response.setTimestamp(ts);
		} catch (Exception e) {
			response.setWarning(MSG.error(e.getMessage()));
		}
	}

	/** Faithful port of RoomAvailabilityAction.getCompareTable(...) — persisted exam assignments only. */
	private void fillCompareTable(ExamRoomAvailabilityResponse response, RoomAvailabilityInterface ra,
			ExamRoomAvailabilityRequest request, Session session, ExamType examType, Date[] bounds) {
		Long sessionId = session.getUniqueId();
		TreeSet<ExamPeriod> periods = ExamPeriod.findAll(sessionId, examType.getUniqueId());
		if (periods.isEmpty()) {
			response.setWarning(MSG.warnNoExaminationPeriods());
			return;
		}
		if (bounds == null) return;
		Formats.Format<Date> dateFormat = Formats.getDateFormat(Formats.Pattern.DATE_EXAM_PERIOD);
		Formats.Format<Date> timeFormat = Formats.getDateFormat(Formats.Pattern.TIME_SHORT);
		String ts = null;
		String eventType = (examType.getType() == ExamType.sExamTypeFinal ? RoomAvailabilityInterface.sFinalExamType : RoomAvailabilityInterface.sMidtermExamType);
		try {
			for (Location location : Location.findAllExamLocations(sessionId, examType.getUniqueId())) {
				if (!match(request.getFilter(), location.getLabel())) continue;
				Collection<TimeBlock> events = ra.getRoomAvailability(location.getUniqueId(), bounds[0], bounds[1], null);
				if (ts == null) ts = ra.getTimeStamp(bounds[0], bounds[1], null);
				TreeSet<ExamAssignment> exams = new TreeSet<ExamAssignment>();
				for (Exam x : ExamDAO.getInstance().getSession().createQuery(
						"select x from Exam x inner join x.assignedRooms r where x.examType.uniqueId=:examTypeId and r.uniqueId=:locationId", Exam.class)
						.setParameter("examTypeId", examType.getUniqueId())
						.setParameter("locationId", location.getUniqueId())
						.setCacheable(true)
						.list()) {
					exams.add(new ExamAssignment(x));
				}
				if (events == null) events = new TreeSet<TimeBlock>();

				Hashtable<TimeBlock, ExamAssignment> mapping = new Hashtable<TimeBlock, ExamAssignment>();
				// Pass 1: name + period overlap.
				for (TimeBlock event : events) {
					if (!eventType.equals(event.getEventType())) continue;
					ExamAssignment m = null;
					for (ExamAssignment exam : exams)
						if (event.getEventName().trim().equalsIgnoreCase(exam.getExamName().trim()) && exam.getPeriod().overlap(event)) { m = exam; break; }
					if (m != null) { mapping.put(event, m); exams.remove(m); }
				}
				// Pass 2: name only.
				for (TimeBlock event : events) {
					if (!eventType.equals(event.getEventType())) continue;
					ExamAssignment m = null;
					for (ExamAssignment exam : exams)
						if (event.getEventName().trim().equalsIgnoreCase(exam.getExamName().trim())) { m = exam; break; }
					if (m != null) { mapping.put(event, m); exams.remove(m); }
				}
				// Pass 3: period overlap only.
				for (TimeBlock event : events) {
					if (!eventType.equals(event.getEventType())) continue;
					ExamAssignment m = null;
					for (ExamAssignment exam : exams)
						if (exam.getPeriod().overlap(event)) { m = exam; break; }
					if (m != null) { mapping.put(event, m); exams.remove(m); }
				}
				// Emit one row per matched/unmatched event.
				for (TimeBlock event : events) {
					if (!eventType.equals(event.getEventType())) continue;
					ExamAssignment match = mapping.get(event);
					if (match == null) {
						Row r = response.addRow(location.getUniqueId());
						r.add(location.getLabel());
						r.add(location.getCapacity().toString());
						r.add(location.getExamCapacity().toString());
						r.add("");
						r.add("");
						r.add("");
						r.add(event.getEventName());
						r.add(dateFormat.format(event.getStartTime()));
						r.add(time(timeFormat, event.getStartTime()) + " - " + time(timeFormat, event.getEndTime()));
					} else {
						Calendar c = Calendar.getInstance();
						c.setTime(match.getPeriod().getStartTime());
						c.add(Calendar.MINUTE, match.getPrintOffset());
						Date startTime = c.getTime();
						c.add(Calendar.MINUTE, match.getLength());
						Date endTime = c.getTime();
						boolean nameMatch = event.getEventName().trim().equalsIgnoreCase(match.getExamName().trim());
						boolean dateMatch = dateFormat.format(event.getStartTime()).equals(dateFormat.format(match.getPeriod().getStartDate()));
						Date start = event.getStartTime();
						int breakTimeStart = match.getPeriod().getEventStartOffset().intValue() * Constants.SLOT_LENGTH_MIN;
						c = Calendar.getInstance(Locale.US);
						c.setTime(start);
						c.add(Calendar.MINUTE, breakTimeStart);
						start = c.getTime();
						Date stop = event.getEndTime();
						int breakTimeStop = match.getPeriod().getEventStopOffset().intValue() * Constants.SLOT_LENGTH_MIN;
						c = Calendar.getInstance(Locale.US);
						c.setTime(stop);
						c.add(Calendar.MINUTE, -breakTimeStop);
						stop = c.getTime();
						boolean startMatch = start.equals(startTime);
						boolean endMatch = stop.equals(endTime);
						if (nameMatch && dateMatch && startMatch && endMatch) continue;
						Row r = response.addRow(location.getUniqueId());
						r.add(location.getLabel());
						r.add(location.getCapacity().toString());
						r.add(location.getExamCapacity().toString());
						r.add(match.getExamName());
						r.add(dateFormat.format(match.getPeriod().getStartDate()));
						r.add(time(timeFormat, startTime) + " - " + time(timeFormat, endTime));
						r.add(event.getEventName());
						r.add(dateFormat.format(event.getStartTime()));
						r.add(time(timeFormat, event.getStartTime()) + " - " + time(timeFormat, event.getEndTime()));
					}
				}
				// Exams with no matching availability block.
				for (ExamAssignment exam : exams) {
					Calendar c = Calendar.getInstance();
					c.setTime(exam.getPeriod().getStartTime());
					c.add(Calendar.MINUTE, exam.getLength());
					Date endTime = c.getTime();
					Row r = response.addRow(location.getUniqueId());
					r.add(location.getLabel());
					r.add(location.getCapacity().toString());
					r.add(location.getExamCapacity().toString());
					r.add(exam.getExamName());
					r.add(dateFormat.format(exam.getPeriod().getStartDate()));
					r.add(time(timeFormat, exam.getPeriod().getStartTime()) + " - " + time(timeFormat, endTime));
					r.add("");
					r.add("");
					r.add("");
				}
			}
			if (ts != null) response.setTimestamp(ts);
		} catch (Exception e) {
			response.setWarning(MSG.error(e.getMessage()));
		}
	}
}
