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
package org.unitime.timetable.server.invigilation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unitime.timetable.gwt.shared.ExamSeatingInterface.InvigilationConfig;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Issue;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamSeat;
import org.unitime.timetable.model.ExamSpecialArrangement;
import org.unitime.timetable.model.InvigilationAssignment;
import org.unitime.timetable.model.Invigilator;
import org.unitime.timetable.model.InvigilatorAvailability;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.server.seating.ExamPlanData;
import org.unitime.timetable.server.seating.ExamPlanData.ExamData;
import org.unitime.timetable.server.seating.SeatGrid;

/**
 * Invigilation engine: staffing requirements per examination period and venue, availability (stored
 * availability plus approved staff absences of the Relief Planning module), automatic allocation,
 * conflict detection and replacement candidates.
 *
 * Requirement of a venue = max(min per room, ceil(students / students per invigilator)), plus extra
 * invigilators for a separate (special-arrangement) room; one of them is the chief invigilator when
 * configured. Reserves are per period (no venue).
 *
 * Allocation fills the hardest-to-staff duties first with the eligible invigilator of the lowest load
 * (preferred periods first), then balances workloads by moving duties from the busiest to the least busy
 * invigilators. Hard rules: active, eligible duty type, available, not absent, one duty per period,
 * daily and total limits, and (optionally) not invigilating one's own examination.
 */
public class InvigilationPlanner {
	public static final String CHIEF = "CHIEF", INVIGILATOR = "INVIGILATOR", RESERVE = "RESERVE";

	private final ExamPlanData iData;
	private final InvigilationConfig iConfig;
	private final List<Invigilator> iInvigilators;
	private final Map<Long, Invigilator> iById = new HashMap<Long, Invigilator>();
	/** invigilatorId:periodId -> UNAVAILABLE / PREFERRED / ABSENT */
	private final Map<String, String> iAvailability = new HashMap<String, String>();
	private final SimpleDateFormat iDay = new SimpleDateFormat("yyyyMMdd");

	public static class Requirement {
		public Long periodId, roomId;
		public String room;
		public int students, required;
		public boolean chief, special;
	}

	public static class Slot {
		public Long periodId, roomId;
		public String dutyType;
		public Slot(Long periodId, Long roomId, String dutyType) { this.periodId = periodId; this.roomId = roomId; this.dutyType = dutyType; }
	}

	public InvigilationPlanner(ExamPlanData data, InvigilationConfig config, List<Invigilator> invigilators, List<InvigilatorAvailability> availability, List<StaffAbsence> absences) {
		iData = data; iConfig = config; iInvigilators = invigilators;
		for (Invigilator i: invigilators) iById.put(i.getUniqueId(), i);
		for (InvigilatorAvailability a: availability) iAvailability.put(a.getInvigilatorId() + ":" + a.getExamPeriodId(), a.getStatus());
		if (absences != null)
			for (StaffAbsence a: absences) {
				if (!a.isApproved() || a.getExternalUniqueId() == null) continue;
				for (Invigilator i: invigilators)
					if (a.getExternalUniqueId().equals(i.getExternalId()))
						for (ExamPeriod p: data.periods)
							if (overlaps(a, p)) iAvailability.put(i.getUniqueId() + ":" + p.getUniqueId(), "ABSENT");
			}
	}

	private boolean overlaps(StaffAbsence a, ExamPeriod p) {
		String d = iDay.format(p.getStartDate());
		return a.getStartDate() != null && a.getEndDate() != null && iDay.format(a.getStartDate()).compareTo(d) <= 0 && iDay.format(a.getEndDate()).compareTo(d) >= 0;
	}

	public String availability(Long invigilatorId, Long periodId) { return iAvailability.get(invigilatorId + ":" + periodId); }

	public Invigilator invigilator(Long id) { return iById.get(id); }

	public String day(Long periodId) {
		ExamPeriod p = iData.periodById.get(periodId);
		return p == null ? "" : iDay.format(p.getStartDate());
	}

	// ------------------------------------------------------------------ requirements

	public List<Requirement> requirements(Map<Long, List<ExamSeat>> seatsByPeriod) {
		List<Requirement> ret = new ArrayList<Requirement>();
		for (ExamPeriod p: iData.periods) {
			Long periodId = p.getUniqueId();
			List<ExamData> exams = iData.exams(periodId);
			if (exams.isEmpty()) continue;
			Map<Long, Integer> students = new HashMap<Long, Integer>();
			List<ExamSeat> seats = (seatsByPeriod == null ? null : seatsByPeriod.get(periodId));
			if (seats != null && !seats.isEmpty()) {
				for (ExamSeat s: seats) students.put(s.getLocationId(), (students.containsKey(s.getLocationId()) ? students.get(s.getLocationId()) : 0) + 1);
			} else {
				for (ExamData e: exams) {
					int total = 0;
					for (Location l: e.rooms) total += SeatGrid.capacity(l, e.seatingType == 1);
					int left = e.studentIds.size();
					for (int i = 0; i < e.rooms.size(); i++) {
						Location l = e.rooms.get(i);
						int n = (i == e.rooms.size() - 1 ? left : (int)Math.round(e.studentIds.size() * (double)SeatGrid.capacity(l, e.seatingType == 1) / Math.max(1, total)));
						n = Math.min(n, left); left -= n;
						students.put(l.getUniqueId(), (students.containsKey(l.getUniqueId()) ? students.get(l.getUniqueId()) : 0) + n);
					}
				}
			}
			Set<Long> examRooms = new HashSet<Long>();
			for (ExamData e: exams) for (Location l: e.rooms) examRooms.add(l.getUniqueId());
			for (Location l: iData.rooms(periodId)) {
				int n = (students.containsKey(l.getUniqueId()) ? students.get(l.getUniqueId()) : 0);
				boolean special = !examRooms.contains(l.getUniqueId());
				if (special && n == 0) n = specialStudents(periodId, l.getUniqueId());
				if (n == 0) continue;
				Requirement r = new Requirement();
				r.periodId = periodId; r.roomId = l.getUniqueId(); r.room = l.getLabel(); r.students = n; r.special = special;
				r.required = Math.max(iConfig.minPerRoom, (int)Math.ceil(n / (double)Math.max(1, iConfig.studentsPerInvigilator))) + (special ? iConfig.extraSpecialRoom : 0);
				r.chief = iConfig.chiefPerRoom && r.required > 0;
				ret.add(r);
			}
			if (iConfig.reservesPerPeriod > 0) {
				Requirement r = new Requirement();
				r.periodId = periodId; r.room = "Reserve"; r.required = iConfig.reservesPerPeriod;
				ret.add(r);
			}
		}
		return ret;
	}

	private int specialStudents(Long periodId, Long roomId) {
		int n = 0;
		for (Long sid: iData.studentsOf(periodId).keySet()) {
			ExamSpecialArrangement a = iData.arrangements.get(sid);
			if (a != null && Boolean.TRUE.equals(a.getSeparateRoom()) && roomId.equals(a.getLocationId())) n++;
		}
		return n;
	}

	public static List<Slot> slots(Requirement r) {
		List<Slot> ret = new ArrayList<Slot>();
		for (int i = 0; i < r.required; i++)
			ret.add(new Slot(r.periodId, r.roomId, r.roomId == null ? RESERVE : (i == 0 && r.chief ? CHIEF : INVIGILATOR)));
		return ret;
	}

	// ------------------------------------------------------------------ rules

	public static boolean eligible(Invigilator i, String dutyType) {
		String types = (i.getDutyTypes() == null ? INVIGILATOR : i.getDutyTypes().toUpperCase());
		if (CHIEF.equals(dutyType)) return types.contains(CHIEF);
		if (RESERVE.equals(dutyType)) return types.contains(RESERVE) || types.contains(INVIGILATOR);
		return types.contains(INVIGILATOR) || types.contains(CHIEF);
	}

	public int maxPerDay(Invigilator i) { return i.getMaxPerDay() != null && i.getMaxPerDay() > 0 ? i.getMaxPerDay() : iConfig.defaultMaxPerDay; }
	public int maxTotal(Invigilator i) { return i.getMaxTotal() != null && i.getMaxTotal() > 0 ? i.getMaxTotal() : iConfig.defaultMaxTotal; }

	public boolean ownExam(Invigilator i, Long periodId, Long roomId) {
		if (i.getExternalId() == null || roomId == null) return false;
		for (ExamData e: iData.exams(periodId))
			if (e.instructorIds.contains(i.getExternalId()))
				for (Location l: e.rooms) if (l.getUniqueId().equals(roomId)) return true;
		return false;
	}

	/** Why the invigilator cannot take the slot (null when suitable), given the current duties. */
	public String reason(Invigilator i, Slot slot, List<InvigilationAssignment> duties, Long ignoreDutyId) {
		if (!Boolean.TRUE.equals(i.getActive())) return "inactive";
		if (!eligible(i, slot.dutyType)) return "not eligible for " + slot.dutyType.toLowerCase() + " duty";
		String av = availability(i.getUniqueId(), slot.periodId);
		if ("UNAVAILABLE".equals(av)) return "unavailable";
		if ("ABSENT".equals(av)) return "on approved leave";
		int day = 0, total = 0;
		String d = day(slot.periodId);
		for (InvigilationAssignment a: duties) {
			if (!a.getInvigilatorId().equals(i.getUniqueId()) || a.getUniqueId() != null && a.getUniqueId().equals(ignoreDutyId)) continue;
			if (a.getExamPeriodId().equals(slot.periodId)) return "already on duty in this period";
			total++;
			if (day(a.getExamPeriodId()).equals(d)) day++;
		}
		if (day >= maxPerDay(i)) return "daily limit (" + maxPerDay(i) + ") reached";
		if (total >= maxTotal(i)) return "total limit (" + maxTotal(i) + ") reached";
		if (iConfig.avoidOwnExam && ownExam(i, slot.periodId, slot.roomId)) return "examiner of a paper in this room";
		return null;
	}

	public int load(Invigilator i, List<InvigilationAssignment> duties) {
		int n = 0;
		for (InvigilationAssignment a: duties) if (a.getInvigilatorId().equals(i.getUniqueId())) n++;
		return n;
	}

	public int dutiesThatDay(Invigilator i, Long periodId, List<InvigilationAssignment> duties) {
		int n = 0; String d = day(periodId);
		for (InvigilationAssignment a: duties) if (a.getInvigilatorId().equals(i.getUniqueId()) && day(a.getExamPeriodId()).equals(d)) n++;
		return n;
	}

	// ------------------------------------------------------------------ allocation

	/** Open slots of the requirements, given the existing duties (which fill matching slots first). */
	public List<Slot> openSlots(List<Requirement> requirements, List<InvigilationAssignment> duties) {
		List<Slot> ret = new ArrayList<Slot>();
		for (Requirement r: requirements) {
			List<InvigilationAssignment> here = new ArrayList<InvigilationAssignment>();
			for (InvigilationAssignment a: duties)
				if (a.getExamPeriodId().equals(r.periodId) && (r.roomId == null ? a.getLocationId() == null : r.roomId.equals(a.getLocationId()))) here.add(a);
			boolean chiefCovered = false;
			for (InvigilationAssignment a: here) if (CHIEF.equals(a.getDutyType())) chiefCovered = true;
			int missing = r.required - here.size();
			for (Slot s: slots(r)) {
				if (missing <= 0) break;
				if (CHIEF.equals(s.dutyType) && chiefCovered) continue;
				ret.add(s); missing--;
			}
			while (missing-- > 0) ret.add(new Slot(r.periodId, r.roomId, r.roomId == null ? RESERVE : INVIGILATOR));
		}
		return ret;
	}

	/**
	 * Fill the open slots. duties holds the kept duties and receives the new ones (unsaved, with
	 * session / type / source set). Returns the slots that could not be filled.
	 */
	public List<Slot> allocate(List<Requirement> requirements, final List<InvigilationAssignment> duties, String who) {
		List<Slot> open = openSlots(requirements, duties);
		final Map<Slot, Integer> candidates = new HashMap<Slot, Integer>();
		for (Slot s: open) {
			int n = 0;
			for (Invigilator i: iInvigilators) if (reason(i, s, new ArrayList<InvigilationAssignment>(), null) == null) n++;
			candidates.put(s, n);
		}
		Collections.sort(open, new Comparator<Slot>() {
			@Override
			public int compare(Slot a, Slot b) {
				int cmp = Boolean.compare(!CHIEF.equals(a.dutyType), !CHIEF.equals(b.dutyType)); // chiefs first
				if (cmp != 0) return cmp;
				cmp = Integer.compare(candidates.get(a), candidates.get(b)); // hardest to staff first
				if (cmp != 0) return cmp;
				return Long.compare(start(a.periodId), start(b.periodId));
			}
		});
		List<Slot> unfilled = new ArrayList<Slot>();
		for (final Slot s: open) {
			Invigilator best = null; int bestLoad = 0, bestDay = 0; boolean bestPref = false;
			for (Invigilator i: iInvigilators) {
				if (reason(i, s, duties, null) != null) continue;
				int load = load(i, duties), day = dutiesThatDay(i, s.periodId, duties);
				boolean pref = "PREFERRED".equals(availability(i.getUniqueId(), s.periodId));
				if (best == null || better(pref, load, day, i, bestPref, bestLoad, bestDay, best)) { best = i; bestLoad = load; bestDay = day; bestPref = pref; }
			}
			if (best == null) { unfilled.add(s); continue; }
			duties.add(newDuty(s, best.getUniqueId(), who));
		}
		balance(duties);
		return unfilled;
	}

	private static boolean better(boolean pref, int load, int day, Invigilator i, boolean bestPref, int bestLoad, int bestDay, Invigilator best) {
		if (pref != bestPref) return pref;
		if (load != bestLoad) return load < bestLoad;
		if (day != bestDay) return day < bestDay;
		return i.getName().compareTo(best.getName()) < 0;
	}

	private long start(Long periodId) {
		ExamPeriod p = iData.periodById.get(periodId);
		return p == null ? 0 : p.getStartTime().getTime();
	}

	public InvigilationAssignment newDuty(Slot s, Long invigilatorId, String who) {
		InvigilationAssignment a = new InvigilationAssignment();
		a.setSessionId(iData.sessionId); a.setExamTypeId(iData.examTypeId); a.setExamPeriodId(s.periodId); a.setLocationId(s.roomId);
		a.setInvigilatorId(invigilatorId); a.setDutyType(s.dutyType); a.setSource("AUTO"); a.setLocked(false);
		a.setLastModified(new java.util.Date()); a.setModifiedBy(who);
		return a;
	}

	/** Move unlocked duties from the busiest to the least busy invigilators while it narrows the spread. */
	private void balance(List<InvigilationAssignment> duties) {
		for (int iter = 0; iter < 1000; iter++) {
			Invigilator max = null, min = null; int maxLoad = -1, minLoad = Integer.MAX_VALUE;
			for (Invigilator i: iInvigilators) {
				if (!Boolean.TRUE.equals(i.getActive())) continue;
				int l = load(i, duties);
				if (l > maxLoad) { maxLoad = l; max = i; }
				if (l < minLoad) { minLoad = l; min = i; }
			}
			if (max == null || min == null || maxLoad - minLoad <= 1) return;
			boolean moved = false;
			for (Invigilator to: sortedByLoad(duties)) {
				if (load(to, duties) >= maxLoad - 1) break;
				for (InvigilationAssignment a: duties) {
					if (!a.getInvigilatorId().equals(max.getUniqueId()) || Boolean.TRUE.equals(a.getLocked())) continue;
					Slot s = new Slot(a.getExamPeriodId(), a.getLocationId(), a.getDutyType());
					if (reason(to, s, duties, null) == null) { a.setInvigilatorId(to.getUniqueId()); moved = true; break; }
				}
				if (moved) break;
			}
			if (!moved) return;
		}
	}

	private List<Invigilator> sortedByLoad(final List<InvigilationAssignment> duties) {
		List<Invigilator> ret = new ArrayList<Invigilator>();
		for (Invigilator i: iInvigilators) if (Boolean.TRUE.equals(i.getActive())) ret.add(i);
		Collections.sort(ret, new Comparator<Invigilator>() { @Override public int compare(Invigilator a, Invigilator b) { return Integer.compare(load(a, duties), load(b, duties)); } });
		return ret;
	}

	// ------------------------------------------------------------------ validation

	/** Problems of one duty (empty when fine). */
	public List<String> problems(InvigilationAssignment a, List<InvigilationAssignment> duties) {
		List<String> ret = new ArrayList<String>();
		Invigilator i = iById.get(a.getInvigilatorId());
		if (i == null) { ret.add("unknown invigilator"); return ret; }
		if (!Boolean.TRUE.equals(i.getActive())) ret.add("inactive");
		if (!eligible(i, a.getDutyType())) ret.add("not eligible for " + a.getDutyType().toLowerCase() + " duty");
		String av = availability(i.getUniqueId(), a.getExamPeriodId());
		if ("UNAVAILABLE".equals(av)) ret.add("unavailable");
		if ("ABSENT".equals(av)) ret.add("on approved leave");
		int same = 0, day = 0, total = 0; String d = day(a.getExamPeriodId());
		for (InvigilationAssignment b: duties) {
			if (!b.getInvigilatorId().equals(i.getUniqueId())) continue;
			total++;
			if (b.getExamPeriodId().equals(a.getExamPeriodId())) same++;
			if (day(b.getExamPeriodId()).equals(d)) day++;
		}
		if (same > 1) ret.add("double booked (" + same + " duties in this period)");
		if (day > maxPerDay(i)) ret.add("exceeds the daily limit (" + day + "/" + maxPerDay(i) + ")");
		if (total > maxTotal(i)) ret.add("exceeds the total limit (" + total + "/" + maxTotal(i) + ")");
		if (iConfig.avoidOwnExam && ownExam(i, a.getExamPeriodId(), a.getLocationId())) ret.add("examiner of a paper in this room");
		return ret;
	}

	public static boolean isError(String problem) {
		return problem.startsWith("double booked") || problem.equals("unavailable") || problem.equals("on approved leave") || problem.equals("inactive") || problem.equals("unknown invigilator");
	}

	public List<Issue> validate(List<Requirement> requirements, List<InvigilationAssignment> duties) {
		List<Issue> ret = new ArrayList<Issue>();
		for (InvigilationAssignment a: duties)
			for (String p: problems(a, duties)) {
				Issue x = new Issue();
				Invigilator i = iById.get(a.getInvigilatorId());
				x.severity = isError(p) ? "ERROR" : "WARNING"; x.type = "DUTY";
				x.periodId = a.getExamPeriodId(); x.period = iData.periodName(a.getExamPeriodId());
				x.roomId = a.getLocationId(); x.room = (a.getLocationId() == null ? "Reserve" : iData.location(a.getLocationId()).getLabel());
				x.invigilatorId = a.getInvigilatorId(); x.invigilator = (i == null ? null : i.getName());
				x.message = x.invigilator + " (" + a.getDutyType().toLowerCase() + ", " + x.room + "): " + p;
				x.action = "Pick a replacement from the suitable invigilators.";
				ret.add(x);
			}
		for (Requirement r: requirements) {
			int n = 0; boolean chief = false;
			for (InvigilationAssignment a: duties)
				if (a.getExamPeriodId().equals(r.periodId) && (r.roomId == null ? a.getLocationId() == null : r.roomId.equals(a.getLocationId()))) {
					n++; if (CHIEF.equals(a.getDutyType())) chief = true;
				}
			if (n < r.required) {
				Issue x = new Issue();
				x.severity = r.roomId == null ? "WARNING" : "ERROR"; x.type = "INSUFFICIENT";
				x.periodId = r.periodId; x.period = iData.periodName(r.periodId); x.roomId = r.roomId; x.room = r.room;
				x.message = r.room + " needs " + r.required + " invigilator(s) for " + r.students + " student(s) but has " + n;
				x.action = "Run the allocation again, add invigilators or relax the limits.";
				ret.add(x);
			}
			if (r.chief && n > 0 && !chief) {
				Issue x = new Issue();
				x.severity = "WARNING"; x.type = "NO_CHIEF"; x.periodId = r.periodId; x.period = iData.periodName(r.periodId); x.roomId = r.roomId; x.room = r.room;
				x.message = r.room + " has no chief invigilator";
				x.action = "Assign an invigilator eligible for chief duty.";
				ret.add(x);
			}
		}
		return ret;
	}
}
