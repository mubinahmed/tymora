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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Issue;
import org.unitime.timetable.model.ExamSeat;
import org.unitime.timetable.model.ExamSpecialArrangement;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.server.seating.ExamPlanData.ExamData;
import org.unitime.timetable.server.seating.ExamPlanData.StudentData;

/**
 * Examination seat allocation engine (deterministic, no solver).
 *
 * Per examination period:
 *  1. locked seats are kept;
 *  2. students with a separate-room arrangement go to that room;
 *  3. the other students of each exam are split across the exam's rooms in sort order, keeping a
 *     paper's students together and filling single-room exams first;
 *  4. within a room, students needing an accessible seat get accessible seats, those needing a front
 *     seat get front-row seats, then the rest are seated paper by paper (BLOCK) or alternating papers
 *     in adjacent seats (INTERLEAVE).
 * Capacity is never exceeded: students that do not fit are left unseated and reported.
 */
public class SeatAllocator {
	public static final List<String> DEFAULT_SORT = java.util.Arrays.asList("EXAM", "LAST_NAME", "FIRST_NAME");

	private final ExamPlanData iData;
	private final List<String> iSort;
	private final boolean iInterleave;

	public SeatAllocator(ExamPlanData data, List<String> sort, String pattern) {
		iData = data;
		iSort = (sort == null || sort.isEmpty() ? DEFAULT_SORT : sort);
		iInterleave = "INTERLEAVE".equalsIgnoreCase(pattern);
	}

	/** A seat decision (new or kept). */
	public static class Placement {
		public Long studentId, examId, roomId;
		public String seat;
		public boolean locked;
	}

	public boolean alternate(Long periodId, Long roomId) {
		for (ExamData e: iData.exams(periodId))
			if (e.seatingType == 1) for (Location l: e.rooms) if (l.getUniqueId().equals(roomId)) return true;
		return false;
	}

	public SeatGrid grid(Long periodId, Location room) {
		return new SeatGrid(room, iData.layouts.get(room.getUniqueId()), alternate(periodId, room.getUniqueId()));
	}

	private Comparator<Long> studentOrder(final ExamData exam) {
		return new Comparator<Long>() {
			@Override
			public int compare(Long a, Long b) {
				StudentData x = iData.students.get(a), y = iData.students.get(b);
				for (String key: iSort) {
					int cmp = 0;
					if ("LAST_NAME".equals(key)) cmp = s(x == null ? null : x.lastName).compareToIgnoreCase(s(y == null ? null : y.lastName));
					else if ("FIRST_NAME".equals(key)) cmp = s(x == null ? null : x.firstName).compareToIgnoreCase(s(y == null ? null : y.firstName));
					else if ("STUDENT_ID".equals(key)) cmp = s(x == null ? null : x.externalId).compareTo(s(y == null ? null : y.externalId));
					if (cmp != 0) return cmp;
				}
				return a.compareTo(b);
			}
		};
	}

	private Comparator<ExamData> examOrder() {
		return new Comparator<ExamData>() {
			@Override
			public int compare(ExamData a, ExamData b) {
				for (String key: iSort) {
					int cmp = 0;
					if ("SCHOOL".equals(key)) cmp = s(a.school).compareTo(s(b.school));
					else if ("EXAM".equals(key)) cmp = s(a.name).compareTo(s(b.name));
					if (cmp != 0) return cmp;
				}
				return a.id.compareTo(b.id);
			}
		};
	}

	private static String s(String x) { return x == null ? "" : x; }

	/** Allocate the seats of one period. existing = current seats of the period (locked ones are kept). */
	public List<Placement> allocate(Long periodId, List<ExamSeat> existing, boolean keepLocked) {
		List<Placement> result = new ArrayList<Placement>();
		Map<Long, SeatGrid> grids = new HashMap<Long, SeatGrid>();
		Map<Long, Set<String>> taken = new HashMap<Long, Set<String>>();
		Map<Long, Location> rooms = new LinkedHashMap<Long, Location>();
		for (Location l: iData.rooms(periodId)) { rooms.put(l.getUniqueId(), l); grids.put(l.getUniqueId(), grid(periodId, l)); taken.put(l.getUniqueId(), new HashSet<String>()); }
		Set<String> seated = new HashSet<String>(); // studentId:examId
		if (keepLocked && existing != null)
			for (ExamSeat s: existing) {
				if (!Boolean.TRUE.equals(s.getLocked()) || !rooms.containsKey(s.getLocationId())) continue;
				Placement p = new Placement();
				p.studentId = s.getStudentId(); p.examId = s.getExamId(); p.roomId = s.getLocationId(); p.seat = s.getSeatLabel(); p.locked = true;
				result.add(p); seated.add(p.studentId + ":" + p.examId);
				if (p.seat != null) taken.get(p.roomId).add(p.seat);
			}

		// free usable seats per room
		Map<Long, Integer> free = new HashMap<Long, Integer>();
		for (Long r: rooms.keySet()) {
			int n = 0;
			for (SeatGrid.Cell c: grids.get(r).usable()) if (!taken.get(r).contains(c.label)) n++;
			free.put(r, n);
		}

		// who goes to which room
		Map<Long, List<Long[]>> byRoom = new HashMap<Long, List<Long[]>>(); // roomId -> [studentId, examId]
		for (Long r: rooms.keySet()) byRoom.put(r, new ArrayList<Long[]>());
		List<ExamData> exams = new ArrayList<ExamData>(iData.exams(periodId));
		Collections.sort(exams, new Comparator<ExamData>() {
			@Override
			public int compare(ExamData a, ExamData b) {
				int cmp = Integer.compare(a.rooms.size(), b.rooms.size()); // single-room exams first
				return cmp != 0 ? cmp : examOrder().compare(a, b);
			}
		});
		Set<String> withSeatThisPeriod = new HashSet<String>();
		for (ExamData e: exams) {
			List<Long> students = new ArrayList<Long>();
			for (Long sid: e.studentIds) if (!seated.contains(sid + ":" + e.id)) students.add(sid);
			Collections.sort(students, studentOrder(e));
			List<Long> regular = new ArrayList<Long>();
			for (Long sid: students) {
				ExamSpecialArrangement a = iData.arrangements.get(sid);
				if (a != null && Boolean.TRUE.equals(a.getSeparateRoom()) && a.getLocationId() != null && rooms.containsKey(a.getLocationId())) {
					if (free.get(a.getLocationId()) > 0) {
						byRoom.get(a.getLocationId()).add(new Long[] {sid, e.id});
						free.put(a.getLocationId(), free.get(a.getLocationId()) - 1);
					}
				} else regular.add(sid);
			}
			// split across the exam's rooms in order, keeping the paper together where possible
			List<Location> examRooms = new ArrayList<Location>(e.rooms);
			Collections.sort(examRooms, new Comparator<Location>() {
				@Override
				public int compare(Location a, Location b) { return -Integer.compare(free.get(a.getUniqueId()), free.get(b.getUniqueId())); }
			});
			int idx = 0;
			for (Location room: examRooms) {
				Long r = room.getUniqueId();
				while (idx < regular.size() && free.get(r) > 0) {
					Long sid = regular.get(idx++);
					if (!withSeatThisPeriod.add(sid + ":" + e.id)) continue;
					byRoom.get(r).add(new Long[] {sid, e.id});
					free.put(r, free.get(r) - 1);
				}
			}
			// remaining students (no room capacity left) stay unseated and are reported by validation
		}

		// seats within each room
		for (Long r: rooms.keySet()) {
			List<Long[]> list = byRoom.get(r);
			if (list.isEmpty()) continue;
			List<SeatGrid.Cell> cells = new ArrayList<SeatGrid.Cell>();
			for (SeatGrid.Cell c: grids.get(r).usable()) if (!taken.get(r).contains(c.label)) cells.add(c);
			List<Long[]> accessible = new ArrayList<Long[]>(), front = new ArrayList<Long[]>(), rest = new ArrayList<Long[]>();
			for (Long[] x: list) {
				ExamSpecialArrangement a = iData.arrangements.get(x[0]);
				if (a != null && Boolean.TRUE.equals(a.getAccessibleSeat())) accessible.add(x);
				else if (a != null && Boolean.TRUE.equals(a.getFrontSeat())) front.add(x);
				else rest.add(x);
			}
			List<SeatGrid.Cell> pool = new ArrayList<SeatGrid.Cell>(cells);
			for (Long[] x: accessible) place(result, x, r, take(pool, true, false));
			for (Long[] x: front) place(result, x, r, take(pool, false, true));
			for (Long[] x: order(rest)) place(result, x, r, take(pool, false, false));
		}
		return result;
	}

	/** Students of a room in seating order: paper by paper, or alternating papers. */
	private List<Long[]> order(List<Long[]> list) {
		if (!iInterleave) return list; // already grouped by exam in sort order
		Map<Long, List<Long[]>> byExam = new LinkedHashMap<Long, List<Long[]>>();
		for (Long[] x: list) {
			List<Long[]> l = byExam.get(x[1]);
			if (l == null) { l = new ArrayList<Long[]>(); byExam.put(x[1], l); }
			l.add(x);
		}
		List<Long[]> ret = new ArrayList<Long[]>();
		boolean added = true;
		for (int i = 0; added; i++) {
			added = false;
			for (List<Long[]> l: byExam.values()) if (i < l.size()) { ret.add(l.get(i)); added = true; }
		}
		return ret;
	}

	private static SeatGrid.Cell take(List<SeatGrid.Cell> pool, boolean accessible, boolean front) {
		if (pool.isEmpty()) return null;
		for (int i = 0; i < pool.size(); i++) {
			SeatGrid.Cell c = pool.get(i);
			if ((accessible && c.accessible) || (front && c.front && !c.accessible)) { pool.remove(i); return c; }
		}
		// regular students prefer non-accessible seats
		if (!accessible)
			for (int i = 0; i < pool.size(); i++) if (!pool.get(i).accessible) return pool.remove(i);
		return pool.remove(0);
	}

	private static void place(List<Placement> result, Long[] x, Long roomId, SeatGrid.Cell cell) {
		if (cell == null) return;
		Placement p = new Placement();
		p.studentId = x[0]; p.examId = x[1]; p.roomId = roomId; p.seat = cell.label;
		result.add(p);
	}

	// ------------------------------------------------------------------ validation

	/** Problems of the seats of one period. */
	public List<Issue> validate(Long periodId, List<ExamSeat> seats) {
		List<Issue> ret = new ArrayList<Issue>();
		String period = iData.periodName(periodId);
		Map<String, ExamSeat> byStudentExam = new HashMap<String, ExamSeat>();
		Map<Long, List<ExamSeat>> byStudent = new HashMap<Long, List<ExamSeat>>();
		Map<String, ExamSeat> bySeat = new HashMap<String, ExamSeat>();
		Map<Long, Integer> perRoom = new HashMap<Long, Integer>();
		for (ExamSeat s: seats) {
			byStudentExam.put(s.getStudentId() + ":" + s.getExamId(), s);
			List<ExamSeat> l = byStudent.get(s.getStudentId());
			if (l == null) { l = new ArrayList<ExamSeat>(); byStudent.put(s.getStudentId(), l); }
			l.add(s);
			perRoom.put(s.getLocationId(), (perRoom.containsKey(s.getLocationId()) ? perRoom.get(s.getLocationId()) : 0) + 1);
			if (s.getSeatLabel() != null) {
				String key = s.getLocationId() + ":" + s.getSeatLabel();
				ExamSeat other = bySeat.put(key, s);
				if (other != null)
					ret.add(issue("ERROR", "DOUBLE_SEAT", periodId, period, s, "Seat " + s.getSeatLabel() + " is given to two students (" + name(other.getStudentId()) + ", " + name(s.getStudentId()) + ")",
							"Move one of the students to a free seat."));
			}
		}
		for (ExamData e: iData.exams(periodId))
			for (Long sid: e.studentIds)
				if (!byStudentExam.containsKey(sid + ":" + e.id)) {
					Issue i = new Issue();
					i.severity = "ERROR"; i.type = "NO_SEAT"; i.periodId = periodId; i.period = period;
					i.studentId = sid; i.student = name(sid); i.examId = e.id; i.exam = e.name;
					i.message = name(sid) + " has no seat for " + e.name;
					i.action = "Run the automatic allocation again, add a room to the exam, or place the student manually.";
					ret.add(i);
				}
		for (Map.Entry<Long, List<ExamSeat>> en: byStudent.entrySet())
			if (en.getValue().size() > 1) {
				ExamSeat s = en.getValue().get(1);
				ret.add(issue("WARNING", "DIRECT_CONFLICT", periodId, period, s, name(en.getKey()) + " has " + en.getValue().size() + " examinations in this period",
						"Resolve the clash in the examination timetable (assignment dialog)."));
			}
		for (Map.Entry<Long, Integer> en: perRoom.entrySet()) {
			Location room = iData.location(en.getKey());
			if (room == null) continue;
			SeatGrid grid = grid(periodId, room);
			int usable = grid.usable().size();
			if (en.getValue() > usable) {
				Issue i = new Issue();
				i.severity = "ERROR"; i.type = "OVER_CAPACITY"; i.periodId = periodId; i.period = period; i.roomId = room.getUniqueId(); i.room = room.getLabel();
				i.message = room.getLabel() + " has " + en.getValue() + " students for " + usable + " usable seats";
				i.action = "Move students to another room or allocate a larger venue.";
				ret.add(i);
			}
		}
		for (ExamSeat s: seats) {
			Location room = iData.location(s.getLocationId());
			if (room != null && s.getSeatLabel() != null) {
				SeatGrid.Cell cell = grid(periodId, room).cell(s.getSeatLabel());
				if (cell == null || cell.blocked)
					ret.add(issue("ERROR", "INVALID_SEAT", periodId, period, s, name(s.getStudentId()) + " has seat " + s.getSeatLabel() + " which is not a seat of " + room.getLabel(), "Move the student to a valid seat."));
				ExamSpecialArrangement a = iData.arrangements.get(s.getStudentId());
				if (a != null) {
					if (Boolean.TRUE.equals(a.getSeparateRoom()) && a.getLocationId() != null && !a.getLocationId().equals(s.getLocationId()))
						ret.add(issue("ERROR", "SPECIAL_ROOM", periodId, period, s, name(s.getStudentId()) + " needs a separate room (" + roomName(a.getLocationId()) + ") but sits in " + room.getLabel(), "Run the allocation again or move the student to the separate room."));
					if (Boolean.TRUE.equals(a.getAccessibleSeat()) && cell != null && !cell.accessible)
						ret.add(issue("ERROR", "SPECIAL_SEAT", periodId, period, s, name(s.getStudentId()) + " needs an accessible seat but has " + s.getSeatLabel(), "Move the student to an accessible seat (mark accessible seats in the room layout)."));
					if (Boolean.TRUE.equals(a.getFrontSeat()) && cell != null && !cell.front && !cell.accessible)
						ret.add(issue("WARNING", "SPECIAL_SEAT", periodId, period, s, name(s.getStudentId()) + " should sit in the front but has " + s.getSeatLabel(), "Move the student to a front-row seat."));
				}
			}
			ExamSpecialArrangement sa = iData.arrangements.get(s.getStudentId());
			if (sa != null && Boolean.TRUE.equals(sa.getSeparateRoom()) && sa.getLocationId() == null)
				ret.add(issue("WARNING", "SPECIAL_ROOM", periodId, period, s, name(s.getStudentId()) + " needs a separate room but none is set in the special arrangement", "Set the separate room in Special arrangements."));
		}
		return ret;
	}

	private Issue issue(String severity, String type, Long periodId, String period, ExamSeat s, String message, String action) {
		Issue i = new Issue();
		i.severity = severity; i.type = type; i.periodId = periodId; i.period = period; i.message = message; i.action = action;
		i.studentId = s.getStudentId(); i.student = name(s.getStudentId()); i.examId = s.getExamId();
		ExamData e = iData.examById.get(s.getExamId()); i.exam = (e == null ? null : e.name);
		i.roomId = s.getLocationId(); i.room = roomName(s.getLocationId()); i.seat = s.getSeatLabel();
		return i;
	}

	private String name(Long studentId) {
		StudentData d = iData.students.get(studentId);
		return d == null ? "Student " + studentId : d.name();
	}

	private String roomName(Long roomId) {
		Location l = iData.location(roomId);
		return l == null ? "room " + roomId : l.getLabel();
	}

	public static ExamSeat toSeat(ExamPlanData data, Long periodId, Placement p, String who) {
		ExamSeat s = new ExamSeat();
		s.setSessionId(data.sessionId); s.setExamTypeId(data.examTypeId); s.setExamPeriodId(periodId);
		s.setExamId(p.examId); s.setStudentId(p.studentId); s.setLocationId(p.roomId); s.setSeatLabel(p.seat);
		s.setSource("AUTO"); s.setLocked(false); s.setLastModified(new Date()); s.setModifiedBy(who);
		return s;
	}
}
