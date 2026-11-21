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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamSeatLayout;
import org.unitime.timetable.model.ExamSpecialArrangement;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.LocationDAO;

/**
 * Snapshot of the committed examination timetable of one examination type, as used by seat allocation
 * and invigilation: periods, the exams assigned to each period with their rooms and students, the
 * students, venues, seat layouts and special arrangements.
 */
public class ExamPlanData {
	public final Long sessionId, examTypeId;
	public final List<ExamPeriod> periods = new ArrayList<ExamPeriod>();
	public final Map<Long, ExamPeriod> periodById = new HashMap<Long, ExamPeriod>();
	public final Map<Long, List<ExamData>> examsByPeriod = new HashMap<Long, List<ExamData>>();
	public final Map<Long, ExamData> examById = new HashMap<Long, ExamData>();
	public final Map<Long, StudentData> students = new HashMap<Long, StudentData>();
	public final Map<Long, Location> locations = new HashMap<Long, Location>();
	public final Map<Long, ExamSeatLayout> layouts = new HashMap<Long, ExamSeatLayout>();
	public final Map<Long, ExamSpecialArrangement> arrangements = new HashMap<Long, ExamSpecialArrangement>();

	public static class ExamData {
		public Long id;
		public String name, school;
		public int seatingType;
		public Long periodId;
		public List<Location> rooms = new ArrayList<Location>();
		public Set<Long> studentIds = new HashSet<Long>();
		/** external ids of the exam's instructors (to keep examiners away from their own paper) */
		public Set<String> instructorIds = new HashSet<String>();
	}

	public static class StudentData {
		public Long id;
		public String externalId, firstName, lastName, email;
		public String name() { return (lastName == null ? "" : lastName) + (firstName == null ? "" : ", " + firstName); }
	}

	@SuppressWarnings("unchecked")
	public ExamPlanData(org.hibernate.Session hibSession, Long sessionId, Long examTypeId) {
		this.sessionId = sessionId;
		this.examTypeId = examTypeId;
		for (ExamPeriod p: ExamPeriod.findAll(sessionId, examTypeId)) { periods.add(p); periodById.put(p.getUniqueId(), p); }
		for (Exam exam: (List<Exam>)Exam.findAll(sessionId, examTypeId)) {
			if (exam.getAssignedPeriod() == null) continue;
			ExamData e = new ExamData();
			e.id = exam.getUniqueId();
			e.name = exam.getLabel();
			e.seatingType = (exam.getSeatingType() == null ? Exam.sSeatingTypeNormal : exam.getSeatingType());
			e.periodId = exam.getAssignedPeriod().getUniqueId();
			if (exam.getAssignedRooms() != null) {
				for (Location l: new TreeSet<Location>(exam.getAssignedRooms())) { e.rooms.add(l); locations.put(l.getUniqueId(), l); }
			}
			try {
				ExamOwner owner = exam.firstOwner();
				e.school = owner.getCourse().getSubjectArea().getDepartment().getDeptCode();
			} catch (Exception x) { e.school = ""; }
			e.studentIds.addAll(exam.getStudentIds());
			if (exam.getInstructors() != null)
				for (DepartmentalInstructor i: exam.getInstructors())
					if (i.getExternalUniqueId() != null) e.instructorIds.add(i.getExternalUniqueId());
			examById.put(e.id, e);
			List<ExamData> list = examsByPeriod.get(e.periodId);
			if (list == null) { list = new ArrayList<ExamData>(); examsByPeriod.put(e.periodId, list); }
			list.add(e);
		}
		Set<Long> studentIds = new HashSet<Long>();
		for (ExamData e: examById.values()) studentIds.addAll(e.studentIds);
		loadStudents(hibSession, studentIds);
		for (ExamSeatLayout l: hibSession.createQuery("from ExamSeatLayout where sessionId = :s", ExamSeatLayout.class).setParameter("s", sessionId).list()) {
			layouts.put(l.getLocationId(), l);
			location(l.getLocationId());
		}
		for (ExamSpecialArrangement a: hibSession.createQuery("from ExamSpecialArrangement where sessionId = :s", ExamSpecialArrangement.class).setParameter("s", sessionId).list()) {
			arrangements.put(a.getStudentId(), a);
			if (a.getLocationId() != null) location(a.getLocationId());
		}
	}

	public void loadStudents(org.hibernate.Session hibSession, Collection<Long> ids) {
		List<Long> missing = new ArrayList<Long>();
		for (Long id: ids) if (!students.containsKey(id)) missing.add(id);
		for (int i = 0; i < missing.size(); i += 500) {
			List<Long> chunk = missing.subList(i, Math.min(missing.size(), i + 500));
			for (Student s: hibSession.createQuery("from Student where uniqueId in :ids", Student.class).setParameterList("ids", chunk).list()) {
				StudentData d = new StudentData();
				d.id = s.getUniqueId(); d.externalId = s.getExternalUniqueId(); d.firstName = s.getFirstName(); d.lastName = s.getLastName(); d.email = s.getEmail();
				students.put(d.id, d);
			}
		}
	}

	public Location location(Long id) {
		if (id == null) return null;
		Location l = locations.get(id);
		if (l == null) {
			l = LocationDAO.getInstance().get(id);
			if (l != null) locations.put(id, l);
		}
		return l;
	}

	public List<ExamData> exams(Long periodId) {
		List<ExamData> ret = examsByPeriod.get(periodId);
		return ret == null ? new ArrayList<ExamData>() : ret;
	}

	/** All students with an exam in the period, with the exam(s) they take. */
	public Map<Long, List<ExamData>> studentsOf(Long periodId) {
		Map<Long, List<ExamData>> ret = new HashMap<Long, List<ExamData>>();
		for (ExamData e: exams(periodId))
			for (Long s: e.studentIds) {
				List<ExamData> l = ret.get(s);
				if (l == null) { l = new ArrayList<ExamData>(); ret.put(s, l); }
				l.add(e);
			}
		return ret;
	}

	/** Rooms used in the period: exam rooms plus separate rooms of special arrangements of its students. */
	public List<Location> rooms(Long periodId) {
		TreeSet<Location> ret = new TreeSet<Location>();
		Set<Long> studentIds = new HashSet<Long>();
		for (ExamData e: exams(periodId)) { ret.addAll(e.rooms); studentIds.addAll(e.studentIds); }
		for (Long s: studentIds) {
			ExamSpecialArrangement a = arrangements.get(s);
			if (a != null && Boolean.TRUE.equals(a.getSeparateRoom()) && a.getLocationId() != null) {
				Location l = location(a.getLocationId());
				if (l != null) ret.add(l);
			}
		}
		return new ArrayList<Location>(ret);
	}

	public String periodName(Long periodId) {
		ExamPeriod p = periodById.get(periodId);
		if (p == null) return "";
		return p.getStartDateLabel() + " " + p.getStartTimeLabel() + " - " + p.getEndTimeLabel();
	}

	public static Exam exam(Long id) { return ExamDAO.getInstance().get(id); }
}
