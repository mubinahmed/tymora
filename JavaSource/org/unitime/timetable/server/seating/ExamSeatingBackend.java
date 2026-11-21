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
package org.unitime.timetable.server.seating;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Transaction;
import org.unitime.commons.Email;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Arrangement;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.ExamSeatingRequest;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.ExamSeatingResponse;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.IdName;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Issue;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Period;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.RoomPlan;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Seat;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.SeatLayout;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.SeatRow;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamSeat;
import org.unitime.timetable.model.ExamSeatLayout;
import org.unitime.timetable.model.ExamSpecialArrangement;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.dao.ExamSeatDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.seating.ExamPlanData.ExamData;
import org.unitime.timetable.server.seating.ExamPlanData.StudentData;

/**
 * Backend of the Examination Seating page (ExamSeatingRequest): seating plans per period and venue,
 * automatic allocation, manual moves / swaps / locks, venue seat layouts, special arrangements,
 * validation, the seat list (attendance lists, export), approval and publication, and the audit log.
 * Viewing needs Right.ExamSeating, changes Right.ExamSeatingEdit; approval and publication an approver.
 */
@GwtRpcImplements(ExamSeatingRequest.class)
public class ExamSeatingBackend implements GwtRpcImplementation<ExamSeatingRequest, ExamSeatingResponse> {
	private static Logger sLog = LogManager.getLogger(ExamSeatingBackend.class);

	@Override
	public ExamSeatingResponse execute(ExamSeatingRequest request, SessionContext context) {
		context.checkPermission(Right.ExamSeating);
		String op = (request.operation == null ? "INIT" : request.operation);
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		org.hibernate.Session hibSession = ExamSeatDAO.getInstance().getSession();
		ExamSeatingResponse response = new ExamSeatingResponse();
		response.examTypes = ExamAllocationSupport.examTypes(context);
		Long examTypeId = ExamAllocationSupport.examType(context, request.examTypeId, response.examTypes);
		response.examTypeId = examTypeId;
		response.canEdit = context.hasPermission(Right.ExamSeatingEdit);
		if (examTypeId == null) { response.message = "No examination type is available."; return response; }
		context.setAttribute("Exam.Type", examTypeId);
		ExamPlanData data = new ExamPlanData(hibSession, sessionId, examTypeId);
		// seats left behind by examinations that moved after allocation (editors: removed and recorded; others: hidden below)
		int stale = (response.canEdit ? ExamAllocationSupport.removeStale(hibSession, context, data, ExamAllocationSupport.SEATS) : 0);
		SeatAllocator allocator = new SeatAllocator(data, request.sort, request.pattern);
		String who = context.getUser().getName();

		switch (op) {
		case "ALLOCATE": {
			edit(context);
			List<Long> periods = new ArrayList<Long>();
			if (request.periodId != null) periods.add(request.periodId);
			else for (ExamPeriod p: data.periods) if (!data.exams(p.getUniqueId()).isEmpty()) periods.add(p.getUniqueId());
			boolean keepLocked = (request.keepLocked == null || request.keepLocked);
			int total = 0;
			Transaction tx = hibSession.beginTransaction();
			try {
				for (Long periodId: periods) {
					List<ExamSeat> existing = seats(hibSession, data, periodId);
					List<SeatAllocator.Placement> placements = allocator.allocate(periodId, existing, keepLocked);
					for (ExamSeat s: existing) if (!keepLocked || !Boolean.TRUE.equals(s.getLocked())) hibSession.remove(s);
					hibSession.flush();
					for (SeatAllocator.Placement p: placements) {
						if (p.locked) continue;
						hibSession.persist(SeatAllocator.toSeat(data, periodId, p, who));
						total++;
					}
					ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, "AUTO_ALLOCATE", periodId,
							placements.size() + " seats (" + (placements.size() - countNew(placements)) + " locked kept); sort " + (request.sort == null ? SeatAllocator.DEFAULT_SORT : request.sort) + ", " + (request.pattern == null ? "BLOCK" : request.pattern), true);
				}
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			response.message = total + " seat(s) allocated in " + periods.size() + " period(s).";
			break;
		}
		case "MOVE": {
			edit(context);
			ExamSeat target = seatAt(hibSession, data, request.periodId, request.toRoomId, request.toSeat);
			if (target != null && !(target.getStudentId().equals(request.studentId) && target.getExamId().equals(request.examId)))
				throw new GwtRpcException("Seat " + request.toSeat + " is taken by " + studentName(data, target.getStudentId()) + "; swap the two students instead.");
			checkSeat(data, allocator, request.periodId, request.toRoomId, request.toSeat);
			Transaction tx = hibSession.beginTransaction();
			try {
				ExamSeat s = seatOf(hibSession, data, request.periodId, request.studentId, request.examId);
				String from = (s == null ? "no seat" : roomName(data, s.getLocationId()) + " " + s.getSeatLabel());
				if (s == null) {
					s = SeatAllocator.toSeat(data, request.periodId, new SeatAllocator.Placement(), who);
					s.setStudentId(request.studentId); s.setExamId(request.examId);
				}
				s.setLocationId(request.toRoomId); s.setSeatLabel(request.toSeat); s.setSource("MANUAL"); s.setLocked(true);
				s.setLastModified(new Date()); s.setModifiedBy(who);
				if (s.getUniqueId() == null) hibSession.persist(s); else hibSession.merge(s);
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, "MOVE", request.periodId,
						studentName(data, request.studentId) + " (" + examName(data, request.examId) + "): " + from + " -> " + roomName(data, request.toRoomId) + " " + request.toSeat, true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			response.message = "Moved.";
			break;
		}
		case "SWAP": {
			edit(context);
			ExamSeat a = seatAt(hibSession, data, request.periodId, request.roomId, request.seat);
			ExamSeat b = seatAt(hibSession, data, request.periodId, request.toRoomId, request.toSeat);
			if (a == null) throw new GwtRpcException("Seat " + request.seat + " is empty.");
			Transaction tx = hibSession.beginTransaction();
			try {
				if (b == null) {
					checkSeat(data, allocator, request.periodId, request.toRoomId, request.toSeat);
					a.setLocationId(request.toRoomId); a.setSeatLabel(request.toSeat);
				} else {
					Long r = a.getLocationId(); String l = a.getSeatLabel();
					a.setLocationId(b.getLocationId()); a.setSeatLabel(b.getSeatLabel());
					b.setLocationId(r); b.setSeatLabel(l);
					b.setSource("MANUAL"); b.setLocked(true); b.setLastModified(new Date()); b.setModifiedBy(who);
					hibSession.merge(b);
				}
				a.setSource("MANUAL"); a.setLocked(true); a.setLastModified(new Date()); a.setModifiedBy(who);
				hibSession.merge(a);
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, "SWAP", request.periodId,
						studentName(data, a.getStudentId()) + " <-> " + (b == null ? "empty seat " + request.toSeat : studentName(data, b.getStudentId())) + " in " + roomName(data, request.roomId), true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			response.message = "Swapped.";
			break;
		}
		case "LOCK": case "UNLOCK": case "UNSEAT": {
			edit(context);
			ExamSeat s = seatOf(hibSession, data, request.periodId, request.studentId, request.examId);
			if (s == null) throw new GwtRpcException("The student has no seat in this period.");
			Transaction tx = hibSession.beginTransaction();
			try {
				if ("UNSEAT".equals(op)) hibSession.remove(s);
				else { s.setLocked("LOCK".equals(op)); s.setLastModified(new Date()); s.setModifiedBy(who); hibSession.merge(s); }
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, op, request.periodId,
						studentName(data, s.getStudentId()) + " (" + examName(data, s.getExamId()) + ") " + roomName(data, s.getLocationId()) + " " + s.getSeatLabel(), !"LOCK".equals(op) && !"UNLOCK".equals(op));
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			break;
		}
		case "SAVE_LAYOUT": case "DELETE_LAYOUT": {
			edit(context);
			SeatLayout l = request.layout;
			if (l == null || l.roomId == null) throw new GwtRpcException("No room given.");
			Transaction tx = hibSession.beginTransaction();
			try {
				ExamSeatLayout x = data.layouts.get(l.roomId);
				if ("DELETE_LAYOUT".equals(op)) { if (x != null) hibSession.remove(hibSession.merge(x)); }
				else {
					if (l.rows <= 0 || l.columns <= 0 || l.rows > 200 || l.columns > 100) throw new GwtRpcException("Rows must be 1-200 and columns 1-100.");
					if (x == null) { x = new ExamSeatLayout(); x.setSessionId(sessionId); x.setLocationId(l.roomId); }
					x.setNrRows(l.rows); x.setNrColumns(l.columns); x.setFrontRows(l.frontRows < 0 ? 0 : l.frontRows);
					x.setBlockedSeats(normalize(l.blocked)); x.setAccessibleSeats(normalize(l.accessible));
					if (x.getUniqueId() == null) hibSession.persist(x); else hibSession.merge(x);
				}
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, op, null,
						roomName(data, l.roomId) + ("DELETE_LAYOUT".equals(op) ? "" : ": " + l.rows + " x " + l.columns + ", front rows " + l.frontRows), true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			data = new ExamPlanData(hibSession, sessionId, examTypeId);
			allocator = new SeatAllocator(data, request.sort, request.pattern);
			op = "LAYOUTS";
			break;
		}
		case "SAVE_ARRANGEMENT": case "DELETE_ARRANGEMENT": {
			edit(context);
			Transaction tx = hibSession.beginTransaction();
			try {
				if ("DELETE_ARRANGEMENT".equals(op)) {
					ExamSpecialArrangement a = hibSession.get(ExamSpecialArrangement.class, request.id);
					if (a != null) {
						ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, op, null, "Removed for " + studentName(data, a.getStudentId()), true);
						hibSession.remove(a);
					}
				} else {
					Arrangement x = request.arrangement;
					if (x == null) throw new GwtRpcException("No arrangement given.");
					Long studentId = x.studentId;
					if (studentId == null && x.studentExternalId != null) {
						Student st = Student.findByExternalId(sessionId, x.studentExternalId.trim());
						if (st == null) throw new GwtRpcException("No student with id " + x.studentExternalId + " in this session.");
						studentId = st.getUniqueId();
					}
					if (studentId == null) throw new GwtRpcException("Pick a student.");
					ExamSpecialArrangement a = data.arrangements.get(studentId);
					if (a == null) { a = new ExamSpecialArrangement(); a.setSessionId(sessionId); a.setStudentId(studentId); }
					a.setSeparateRoom(x.separateRoom); a.setLocationId(x.separateRoom ? x.roomId : null);
					a.setAccessibleSeat(x.accessibleSeat); a.setFrontSeat(x.frontSeat); a.setExtraTimePct(x.extraTimePct);
					a.setNote(x.note == null || x.note.length() <= 500 ? x.note : x.note.substring(0, 500)); a.setApprovedBy(who);
					if (a.getUniqueId() == null) hibSession.persist(a); else hibSession.merge(a);
					data.loadStudents(hibSession, java.util.Collections.singleton(studentId));
					ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, op, null,
							studentName(data, studentId) + ": " + describe(data, a), true);
				}
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			data = new ExamPlanData(hibSession, sessionId, examTypeId);
			allocator = new SeatAllocator(data, request.sort, request.pattern);
			op = "ARRANGEMENTS";
			break;
		}
		case "APPROVE": case "PUBLISH": case "REOPEN": {
			int errors = 0;
			for (Issue i: allIssues(hibSession, data, allocator)) if ("ERROR".equals(i.severity)) errors++;
			response.message = ExamAllocationSupport.transition(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, op, errors, Boolean.TRUE.equals(request.override), request.note);
			if ("PUBLISH".equals(op)) response.message += " " + notifyStudents(hibSession, data);
			break;
		}
		default: break;
		}

		// ---- response content
		response.status = ExamAllocationSupport.toStatus(hibSession, context, sessionId, examTypeId, ExamAllocationSupport.SEATS, Right.ExamSeatingEdit);
		List<ExamSeat> all = hibSession.createQuery("from ExamSeat where sessionId = :s and examTypeId = :t", ExamSeat.class)
				.setParameter("s", sessionId).setParameter("t", examTypeId).list();
		if (!response.canEdit) {
			Map<Long, Set<Long>> roomsByPeriod = new HashMap<Long, Set<Long>>();
			all = new ArrayList<ExamSeat>(all);
			final ExamPlanData current = data;
			all.removeIf(s -> ExamAllocationSupport.isStale(current, s, roomsByPeriod));
		}
		if (stale > 0) {
			String note = stale + " seat(s) of examinations that moved after allocation were removed. Allocate the affected periods again.";
			response.message = (response.message == null ? note : response.message + " " + note);
		}
		Map<Long, List<ExamSeat>> seatsByPeriod = new HashMap<Long, List<ExamSeat>>();
		for (ExamSeat s: all) {
			List<ExamSeat> l = seatsByPeriod.get(s.getExamPeriodId());
			if (l == null) { l = new ArrayList<ExamSeat>(); seatsByPeriod.put(s.getExamPeriodId(), l); }
			l.add(s);
		}
		for (ExamPeriod p: data.periods) {
			List<ExamData> exams = data.exams(p.getUniqueId());
			if (exams.isEmpty()) continue;
			Period x = new Period();
			x.id = p.getUniqueId(); x.name = data.periodName(p.getUniqueId()); x.date = p.getStartDateLabel();
			x.nrExams = exams.size();
			Set<String> pairs = new HashSet<String>();
			for (ExamData e: exams) for (Long sid: e.studentIds) pairs.add(sid + ":" + e.id);
			x.nrStudents = pairs.size(); x.nrRooms = data.rooms(p.getUniqueId()).size();
			List<ExamSeat> ps = seatsByPeriod.get(p.getUniqueId());
			x.nrSeated = (ps == null ? 0 : ps.size());
			response.periods.add(x);
			response.nrStudents += x.nrStudents; response.nrSeated += x.nrSeated;
		}
		List<Issue> issues = new ArrayList<Issue>();
		for (Period p: response.periods) {
			List<ExamSeat> ps = seatsByPeriod.get(p.id);
			issues.addAll(allocator.validate(p.id, ps == null ? new ArrayList<ExamSeat>() : ps));
		}
		for (Issue i: issues) if ("ERROR".equals(i.severity)) response.nrErrors++; else response.nrWarnings++;
		if ("VALIDATE".equals(op) || "APPROVE".equals(op) || "PUBLISH".equals(op) || "ALLOCATE".equals(op) || "INIT".equals(op)) response.issues = issues;

		Long periodId = request.periodId;
		if (periodId == null && !response.periods.isEmpty()) periodId = response.periods.get(0).id;
		if (periodId != null && ("INIT".equals(op) || "ROOM".equals(op) || "ALLOCATE".equals(op) || "MOVE".equals(op) || "SWAP".equals(op) || "LOCK".equals(op) || "UNLOCK".equals(op) || "UNSEAT".equals(op))) {
			for (Location l: data.rooms(periodId)) response.rooms.add(new IdName(l.getUniqueId(), l.getLabel(), String.valueOf(allocator.grid(periodId, l).usable().size())));
			Long roomId = request.roomId;
			if (roomId == null || !containsRoom(response.rooms, roomId)) roomId = (response.rooms.isEmpty() ? null : response.rooms.get(0).id);
			List<ExamSeat> ps = seatsByPeriod.get(periodId);
			if (ps == null) ps = new ArrayList<ExamSeat>();
			if (roomId != null) response.plan = plan(data, allocator, periodId, data.location(roomId), ps);
			response.unseated = unseated(data, periodId, ps);
		}
		if ("LAYOUTS".equals(op)) layouts(hibSession, data, allocator, response);
		if ("ARRANGEMENTS".equals(op)) arrangements(hibSession, data, response);
		if ("LIST".equals(op)) response.rows = rows(data, all);
		if ("LOG".equals(op)) response.log = ExamAllocationSupport.logEntries(hibSession, data, ExamAllocationSupport.SEATS, 300);
		return response;
	}

	private static void edit(SessionContext context) {
		context.checkPermission(Right.ExamSeatingEdit);
	}

	private static int countNew(List<SeatAllocator.Placement> placements) {
		int n = 0;
		for (SeatAllocator.Placement p: placements) if (!p.locked) n++;
		return n;
	}

	private static boolean containsRoom(List<IdName> rooms, Long id) {
		for (IdName r: rooms) if (r.id.equals(id)) return true;
		return false;
	}

	static List<ExamSeat> seats(org.hibernate.Session hibSession, ExamPlanData data, Long periodId) {
		return hibSession.createQuery("from ExamSeat where sessionId = :s and examTypeId = :t and examPeriodId = :p", ExamSeat.class)
				.setParameter("s", data.sessionId).setParameter("t", data.examTypeId).setParameter("p", periodId).list();
	}

	private static ExamSeat seatAt(org.hibernate.Session hibSession, ExamPlanData data, Long periodId, Long roomId, String seat) {
		if (periodId == null || roomId == null || seat == null) throw new GwtRpcException("Period, room and seat are required.");
		for (ExamSeat s: seats(hibSession, data, periodId)) if (roomId.equals(s.getLocationId()) && seat.equals(s.getSeatLabel())) return s;
		return null;
	}

	private static ExamSeat seatOf(org.hibernate.Session hibSession, ExamPlanData data, Long periodId, Long studentId, Long examId) {
		if (periodId == null || studentId == null) throw new GwtRpcException("Period and student are required.");
		for (ExamSeat s: seats(hibSession, data, periodId)) if (studentId.equals(s.getStudentId()) && (examId == null || examId.equals(s.getExamId()))) return s;
		return null;
	}

	private static void checkSeat(ExamPlanData data, SeatAllocator allocator, Long periodId, Long roomId, String seat) {
		Location room = data.location(roomId);
		if (room == null) throw new GwtRpcException("Unknown room.");
		SeatGrid.Cell cell = allocator.grid(periodId, room).cell(seat);
		if (cell == null || cell.blocked) throw new GwtRpcException(seat + " is not a seat of " + room.getLabel() + ".");
		if (!cell.usable) throw new GwtRpcException(seat + " is not used in this period (alternate seating or above the room capacity).");
	}

	private static String normalize(String list) {
		Set<String> labels = new TreeSet<String>(SeatGrid.labels(list));
		return labels.isEmpty() ? null : String.join(",", labels);
	}

	static String studentName(ExamPlanData data, Long id) {
		StudentData d = data.students.get(id);
		return d == null ? "Student " + id : d.name();
	}

	static String examName(ExamPlanData data, Long id) {
		ExamData e = data.examById.get(id);
		return e == null ? "exam " + id : e.name;
	}

	static String roomName(ExamPlanData data, Long id) {
		Location l = data.location(id);
		return l == null ? "room " + id : l.getLabel();
	}

	private static String describe(ExamPlanData data, ExamSpecialArrangement a) {
		List<String> p = new ArrayList<String>();
		if (Boolean.TRUE.equals(a.getSeparateRoom())) p.add("separate room" + (a.getLocationId() == null ? "" : " " + roomName(data, a.getLocationId())));
		if (Boolean.TRUE.equals(a.getAccessibleSeat())) p.add("accessible seat");
		if (Boolean.TRUE.equals(a.getFrontSeat())) p.add("front seat");
		if (a.getExtraTimePct() != null && a.getExtraTimePct() > 0) p.add(a.getExtraTimePct() + "% extra time");
		return p.isEmpty() ? "no arrangement" : String.join(", ", p);
	}

	private static List<Issue> allIssues(org.hibernate.Session hibSession, ExamPlanData data, SeatAllocator allocator) {
		List<Issue> ret = new ArrayList<Issue>();
		for (ExamPeriod p: data.periods)
			if (!data.exams(p.getUniqueId()).isEmpty()) ret.addAll(allocator.validate(p.getUniqueId(), seats(hibSession, data, p.getUniqueId())));
		return ret;
	}

	private static RoomPlan plan(ExamPlanData data, SeatAllocator allocator, Long periodId, Location room, List<ExamSeat> seats) {
		RoomPlan plan = new RoomPlan();
		SeatGrid grid = allocator.grid(periodId, room);
		plan.roomId = room.getUniqueId(); plan.room = room.getLabel(); plan.rows = grid.rows; plan.columns = grid.columns; plan.usableSeats = grid.usable().size();
		Map<String, ExamSeat> bySeat = new HashMap<String, ExamSeat>();
		Map<Long, Integer> perExam = new HashMap<Long, Integer>();
		for (ExamSeat s: seats) if (room.getUniqueId().equals(s.getLocationId())) {
			if (s.getSeatLabel() != null) bySeat.put(s.getSeatLabel(), s);
			perExam.put(s.getExamId(), (perExam.containsKey(s.getExamId()) ? perExam.get(s.getExamId()) : 0) + 1);
			plan.nrSeated++;
		}
		for (SeatGrid.Cell c: grid.cells) {
			Seat seat = new Seat();
			seat.label = c.label; seat.row = c.row; seat.column = c.column; seat.blocked = c.blocked; seat.accessible = c.accessible; seat.front = c.front; seat.usable = c.usable;
			ExamSeat s = bySeat.get(c.label);
			if (s != null) {
				StudentData d = data.students.get(s.getStudentId());
				seat.studentId = s.getStudentId(); seat.examId = s.getExamId(); seat.exam = examName(data, s.getExamId());
				seat.studentName = (d == null ? null : d.name()); seat.studentExternalId = (d == null ? null : d.externalId);
				seat.locked = Boolean.TRUE.equals(s.getLocked()); seat.manual = "MANUAL".equals(s.getSource());
				seat.special = data.arrangements.containsKey(s.getStudentId());
			}
			plan.seats.add(seat);
		}
		for (Map.Entry<Long, Integer> e: perExam.entrySet()) plan.exams.add(new IdName(e.getKey(), examName(data, e.getKey()), String.valueOf(e.getValue())));
		Collections.sort(plan.exams, new Comparator<IdName>() { @Override public int compare(IdName a, IdName b) { return a.name.compareTo(b.name); } });
		return plan;
	}

	private static List<SeatRow> unseated(ExamPlanData data, Long periodId, List<ExamSeat> seats) {
		Set<String> seated = new HashSet<String>();
		for (ExamSeat s: seats) seated.add(s.getStudentId() + ":" + s.getExamId());
		List<SeatRow> ret = new ArrayList<SeatRow>();
		for (ExamData e: data.exams(periodId))
			for (Long sid: e.studentIds)
				if (!seated.contains(sid + ":" + e.id)) {
					SeatRow r = new SeatRow();
					StudentData d = data.students.get(sid);
					r.studentId = sid; r.examId = e.id; r.exam = e.name; r.periodId = periodId;
					r.studentName = (d == null ? "Student " + sid : d.name()); r.studentExternalId = (d == null ? null : d.externalId);
					r.special = data.arrangements.containsKey(sid);
					ret.add(r);
				}
		Collections.sort(ret, new Comparator<SeatRow>() { @Override public int compare(SeatRow a, SeatRow b) { return (a.exam + a.studentName).compareTo(b.exam + b.studentName); } });
		return ret;
	}

	private static List<SeatRow> rows(ExamPlanData data, List<ExamSeat> seats) {
		List<SeatRow> ret = new ArrayList<SeatRow>();
		for (ExamSeat s: seats) {
			SeatRow r = new SeatRow();
			StudentData d = data.students.get(s.getStudentId());
			ExamData e = data.examById.get(s.getExamId());
			ExamPeriod p = data.periodById.get(s.getExamPeriodId());
			r.studentId = s.getStudentId(); r.examId = s.getExamId(); r.roomId = s.getLocationId(); r.periodId = s.getExamPeriodId();
			r.studentName = (d == null ? "Student " + s.getStudentId() : d.name()); r.studentExternalId = (d == null ? null : d.externalId);
			r.exam = (e == null ? null : e.name); r.school = (e == null ? null : e.school);
			r.room = roomName(data, s.getLocationId()); r.seat = s.getSeatLabel();
			r.period = data.periodName(s.getExamPeriodId());
			if (p != null) { r.date = p.getStartDateLabel(); r.time = p.getStartTimeLabel() + " - " + p.getEndTimeLabel(); }
			r.locked = Boolean.TRUE.equals(s.getLocked()); r.manual = "MANUAL".equals(s.getSource());
			ExamSpecialArrangement a = data.arrangements.get(s.getStudentId());
			r.special = (a != null); r.extraTimePct = (a == null ? null : a.getExtraTimePct());
			ret.add(r);
		}
		Collections.sort(ret, new Comparator<SeatRow>() {
			@Override
			public int compare(SeatRow a, SeatRow b) {
				int cmp = Long.compare(order(data, a.periodId), order(data, b.periodId));
				if (cmp != 0) return cmp;
				cmp = String.valueOf(a.room).compareTo(String.valueOf(b.room));
				if (cmp != 0) return cmp;
				return seatOrder(a.seat).compareTo(seatOrder(b.seat));
			}
		});
		return ret;
	}

	private static long order(ExamPlanData data, Long periodId) {
		ExamPeriod p = data.periodById.get(periodId);
		return p == null ? 0 : p.getStartTime().getTime();
	}

	/** A1 < A2 < A10 < B1 */
	static String seatOrder(String label) {
		if (label == null) return "";
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Z]+)([0-9]+)$").matcher(label);
		return m.matches() ? String.format("%3s%5s", m.group(1), m.group(2)).replace(' ', '0') : label;
	}

	private static void layouts(org.hibernate.Session hibSession, ExamPlanData data, SeatAllocator allocator, ExamSeatingResponse response) {
		Set<Location> rooms = new TreeSet<Location>(data.locations.values());
		response.layouts = new ArrayList<SeatLayout>();
		for (Location room: rooms) {
			ExamSeatLayout x = data.layouts.get(room.getUniqueId());
			SeatGrid grid = new SeatGrid(room, x, false);
			SeatLayout l = new SeatLayout();
			l.roomId = room.getUniqueId(); l.room = room.getLabel();
			l.capacity = (room.getCapacity() == null ? 0 : room.getCapacity()); l.examCapacity = (room.getExamCapacity() == null ? 0 : room.getExamCapacity());
			l.rows = grid.rows; l.columns = grid.columns; l.frontRows = grid.frontRows; l.generated = (x == null);
			l.blocked = (x == null ? null : x.getBlockedSeats()); l.accessible = (x == null ? SeatGrid.label(grid.rows - 1, 0) : x.getAccessibleSeats());
			l.usableSeats = grid.usable().size();
			response.layouts.add(l);
		}
		response.allRooms = allRooms(hibSession, data.sessionId);
	}

	private static void arrangements(org.hibernate.Session hibSession, ExamPlanData data, ExamSeatingResponse response) {
		response.arrangements = new ArrayList<Arrangement>();
		for (ExamSpecialArrangement a: data.arrangements.values()) {
			Arrangement x = new Arrangement();
			StudentData d = data.students.get(a.getStudentId());
			if (d == null) { data.loadStudents(hibSession, java.util.Collections.singleton(a.getStudentId())); d = data.students.get(a.getStudentId()); }
			x.id = a.getUniqueId(); x.studentId = a.getStudentId();
			x.studentName = (d == null ? "Student " + a.getStudentId() : d.name()); x.studentExternalId = (d == null ? null : d.externalId);
			x.separateRoom = Boolean.TRUE.equals(a.getSeparateRoom()); x.roomId = a.getLocationId(); x.room = (a.getLocationId() == null ? null : roomName(data, a.getLocationId()));
			x.accessibleSeat = Boolean.TRUE.equals(a.getAccessibleSeat()); x.frontSeat = Boolean.TRUE.equals(a.getFrontSeat());
			x.extraTimePct = a.getExtraTimePct(); x.note = a.getNote(); x.approvedBy = a.getApprovedBy();
			response.arrangements.add(x);
		}
		Collections.sort(response.arrangements, new Comparator<Arrangement>() { @Override public int compare(Arrangement a, Arrangement b) { return a.studentName.compareTo(b.studentName); } });
		response.allRooms = allRooms(hibSession, data.sessionId);
	}

	static List<IdName> allRooms(org.hibernate.Session hibSession, Long sessionId) {
		List<IdName> ret = new ArrayList<IdName>();
		for (Location l: new TreeSet<Location>(hibSession.createQuery("from Location where session.uniqueId = :s", Location.class).setParameter("s", sessionId).list()))
			ret.add(new IdName(l.getUniqueId(), l.getLabel(), String.valueOf(l.getCapacity())));
		return ret;
	}

	/** Email each student their seats (best effort; needs a configured mail server). */
	private static String notifyStudents(org.hibernate.Session hibSession, ExamPlanData data) {
		List<ExamSeat> all = hibSession.createQuery("from ExamSeat where sessionId = :s and examTypeId = :t", ExamSeat.class)
				.setParameter("s", data.sessionId).setParameter("t", data.examTypeId).list();
		Map<Long, List<ExamSeat>> byStudent = new HashMap<Long, List<ExamSeat>>();
		for (ExamSeat s: all) {
			List<ExamSeat> l = byStudent.get(s.getStudentId());
			if (l == null) { l = new ArrayList<ExamSeat>(); byStudent.put(s.getStudentId(), l); }
			l.add(s);
		}
		int sent = 0, noEmail = 0;
		String failure = null;
		for (Map.Entry<Long, List<ExamSeat>> e: byStudent.entrySet()) {
			StudentData d = data.students.get(e.getKey());
			if (d == null || d.email == null || d.email.isEmpty()) { noEmail++; continue; }
			if (failure != null) continue;
			try {
				Email mail = Email.createEmail();
				mail.addRecipient(d.email, d.name());
				mail.setSubject("Your examination seats");
				StringBuffer text = new StringBuffer("Dear " + d.name() + ",\n\nyour examination seats:\n\n");
				for (ExamSeat s: e.getValue()) text.append(examName(data, s.getExamId())).append(": ").append(data.periodName(s.getExamPeriodId())).append(", ").append(roomName(data, s.getLocationId())).append(", seat ").append(s.getSeatLabel()).append("\n");
				mail.setText(text.toString());
				mail.send();
				sent++;
			} catch (Throwable t) {
				failure = t.getMessage();
				sLog.info("Seat notification not sent: " + t.getMessage());
			}
		}
		return byStudent.size() + " student(s) can now see their seats under My Examinations" + (sent > 0 ? "; " + sent + " email(s) sent" : "") + (failure != null ? "; email not sent (" + failure + ")" : "") + (noEmail > 0 ? "; " + noEmail + " without an email address" : "") + ".";
	}
}
