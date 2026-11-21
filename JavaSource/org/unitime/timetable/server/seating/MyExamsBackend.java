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
import java.util.List;
import java.util.Map;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Duty;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.IdName;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.MyExamsRequest;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.MyExamsResponse;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Period;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.SeatRow;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamSeat;
import org.unitime.timetable.model.ExamSpecialArrangement;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.InvigilationAssignment;
import org.unitime.timetable.model.Invigilator;
import org.unitime.timetable.model.InvigilatorAvailability;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.dao.ExamSeatDAO;
import org.unitime.timetable.model.dao.LocationDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.invigilation.InvigilationBackend;

/**
 * My Examinations (MyExamsRequest): a student's published examination seats, and an invigilator's
 * published duties with their availability (which they can set for future periods). The user is matched
 * by external id with a student or an invigilator of the current academic session.
 */
@GwtRpcImplements(MyExamsRequest.class)
public class MyExamsBackend implements GwtRpcImplementation<MyExamsRequest, MyExamsResponse> {

	@Override
	public MyExamsResponse execute(MyExamsRequest request, SessionContext context) {
		boolean canSeats = context.hasPermission(Right.MyExamSeats), canDuties = context.hasPermission(Right.MyInvigilationDuties);
		if (!canSeats && !canDuties) throw new GwtRpcException("You are not allowed to see examination seats or invigilation duties.");
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		String me = context.getUser().getExternalUserId();
		org.hibernate.Session hibSession = ExamSeatDAO.getInstance().getSession();
		MyExamsResponse response = new MyExamsResponse();
		response.name = context.getUser().getName();
		Map<Long, ExamPeriod> periods = new HashMap<Long, ExamPeriod>();
		for (ExamPeriod p: hibSession.createQuery("from ExamPeriod where session.uniqueId = :s", ExamPeriod.class).setParameter("s", sessionId).list()) periods.put(p.getUniqueId(), p);

		Invigilator invigilator = (canDuties && me != null ? hibSession.createQuery("from Invigilator where sessionId = :s and externalId = :e", Invigilator.class)
				.setParameter("s", sessionId).setParameter("e", me).setMaxResults(1).uniqueResult() : null);
		if ("SET_AVAILABILITY".equals(request.operation)) {
			if (invigilator == null) throw new GwtRpcException("You are not registered as an invigilator.");
			ExamPeriod p = periods.get(request.periodId);
			if (p == null) throw new GwtRpcException("Unknown period.");
			InvigilationBackend.setAvailability(hibSession, invigilator.getUniqueId(), request.periodId, request.availability, "SELF", request.note);
			org.hibernate.Transaction tx = hibSession.beginTransaction();
			try {
				ExamAllocationSupport.log(hibSession, context, sessionId, p.getExamType().getUniqueId(), ExamAllocationSupport.INVIGILATION, "AVAILABILITY", p.getUniqueId(),
						invigilator.getName() + " (self-service): " + request.availability, false);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
		}

		Student student = (canSeats && me != null ? Student.findByExternalId(sessionId, me) : null);
		if (student != null) {
			response.student = true;
			response.seats = new ArrayList<SeatRow>();
			ExamSpecialArrangement sa = hibSession.createQuery("from ExamSpecialArrangement where sessionId = :s and studentId = :st", ExamSpecialArrangement.class)
					.setParameter("s", sessionId).setParameter("st", student.getUniqueId()).setMaxResults(1).uniqueResult();
			for (ExamSeat s: hibSession.createQuery("from ExamSeat where sessionId = :s and studentId = :st", ExamSeat.class).setParameter("s", sessionId).setParameter("st", student.getUniqueId()).list()) {
				if (!ExamAllocationSupport.isPublished(hibSession, sessionId, s.getExamTypeId(), ExamAllocationSupport.SEATS)) continue;
				org.unitime.timetable.model.Exam e = ExamPlanData.exam(s.getExamId());
				if (!isCurrent(e, s, sa)) continue; // the examination moved after seats were allocated: the seat is no longer valid
				response.seatsPublished = true;
				SeatRow r = new SeatRow();
				ExamPeriod p = periods.get(s.getExamPeriodId());
				Location l = LocationDAO.getInstance().get(s.getLocationId());
				r.examId = s.getExamId(); r.exam = (e == null ? null : e.getLabel()); r.periodId = s.getExamPeriodId();
				if (p != null) { r.date = p.getStartDateLabel(); r.time = p.getStartTimeLabel() + " - " + p.getEndTimeLabel(); }
				r.room = (l == null ? null : l.getLabel()); r.seat = s.getSeatLabel();
				r.special = (sa != null); r.extraTimePct = (sa == null ? null : sa.getExtraTimePct());
				response.seats.add(r);
			}
			sortRows(response.seats, periods);
		}

		if (invigilator != null) {
			response.invigilator = true;
			response.duties = new ArrayList<Duty>();
			for (InvigilationAssignment a: hibSession.createQuery("from InvigilationAssignment where invigilatorId = :i", InvigilationAssignment.class).setParameter("i", invigilator.getUniqueId()).list()) {
				if (!ExamAllocationSupport.isPublished(hibSession, sessionId, a.getExamTypeId(), ExamAllocationSupport.INVIGILATION)) continue;
				if (!isCurrent(hibSession, sessionId, a)) continue; // its examinations moved after allocation
				response.dutiesPublished = true;
				Duty d = new Duty();
				ExamPeriod p = periods.get(a.getExamPeriodId());
				d.id = a.getUniqueId(); d.periodId = a.getExamPeriodId(); d.dutyType = a.getDutyType();
				if (p != null) { d.date = p.getStartDateLabel(); d.time = p.getStartTimeLabel() + " - " + p.getEndTimeLabel(); }
				Location l = (a.getLocationId() == null ? null : LocationDAO.getInstance().get(a.getLocationId()));
				d.room = (l == null ? "Reserve" : l.getLabel());
				response.duties.add(d);
			}
			Collections.sort(response.duties, new Comparator<Duty>() {
				@Override
				public int compare(Duty a, Duty b) { return Long.compare(start(periods, a.periodId), start(periods, b.periodId)); }
			});
			// availability of every examination period of the session
			Map<Long, String> av = new HashMap<Long, String>();
			for (InvigilatorAvailability a: hibSession.createQuery("from InvigilatorAvailability where invigilatorId = :i", InvigilatorAvailability.class).setParameter("i", invigilator.getUniqueId()).list())
				av.put(a.getExamPeriodId(), a.getStatus());
			if (invigilator.getExternalId() != null)
				for (StaffAbsence a: hibSession.createQuery("from StaffAbsence where session.uniqueId = :s and externalUniqueId = :e", StaffAbsence.class).setParameter("s", sessionId).setParameter("e", invigilator.getExternalId()).list())
					if (a.isApproved())
						for (ExamPeriod p: periods.values())
							if (!a.getStartDate().after(p.getStartDate()) && !a.getEndDate().before(dayOnly(p.getStartDate()))) av.put(p.getUniqueId(), "ABSENT");
			List<ExamPeriod> sorted = new ArrayList<ExamPeriod>(periods.values());
			Collections.sort(sorted);
			response.periods = new ArrayList<Period>();
			response.availability = new ArrayList<IdName>();
			for (ExamPeriod p: sorted) {
				Period x = new Period();
				ExamType t = p.getExamType();
				x.id = p.getUniqueId(); x.date = p.getStartDateLabel(); x.name = p.getStartDateLabel() + " " + p.getStartTimeLabel() + " - " + p.getEndTimeLabel() + (t == null ? "" : " (" + t.getLabel() + ")");
				response.periods.add(x);
				if (av.containsKey(p.getUniqueId())) response.availability.add(new IdName(p.getUniqueId(), av.get(p.getUniqueId()), null));
			}
		}
		if (!response.student && !response.invigilator)
			response.message = "No examination seats or invigilation duties are recorded for you in this academic session.";
		return response;
	}

	private static Date dayOnly(Date d) {
		java.util.Calendar c = java.util.Calendar.getInstance(); c.setTime(d);
		c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0);
		return c.getTime();
	}

	private static long start(Map<Long, ExamPeriod> periods, Long id) {
		ExamPeriod p = periods.get(id);
		return p == null ? 0 : p.getStartTime().getTime();
	}

	private static void sortRows(List<SeatRow> rows, final Map<Long, ExamPeriod> periods) {
		Collections.sort(rows, new Comparator<SeatRow>() {
			@Override
			public int compare(SeatRow a, SeatRow b) { return Long.compare(start(periods, a.periodId), start(periods, b.periodId)); }
		});
	}

	/** The seat still matches the examination's assignment: same period, and an exam room or the student's separate room. */
	static boolean isCurrent(org.unitime.timetable.model.Exam e, ExamSeat s, ExamSpecialArrangement sa) {
		if (e == null || e.getAssignedPeriod() == null || !e.getAssignedPeriod().getUniqueId().equals(s.getExamPeriodId())) return false;
		if (sa != null && s.getLocationId() != null && s.getLocationId().equals(sa.getLocationId())) return true;
		if (e.getAssignedRooms() != null)
			for (Location l: e.getAssignedRooms()) if (l.getUniqueId().equals(s.getLocationId())) return true;
		return false;
	}

	/** The duty's period still has an examination of its type in the duty's room (or any examination, for a reserve). */
	static boolean isCurrent(org.hibernate.Session hibSession, Long sessionId, InvigilationAssignment a) {
		if (a.getLocationId() == null) {
			Long n = hibSession.createQuery("select count(e) from Exam e where e.assignedPeriod.uniqueId = :p and e.examType.uniqueId = :t", Long.class)
					.setParameter("p", a.getExamPeriodId()).setParameter("t", a.getExamTypeId()).uniqueResult();
			return n != null && n > 0;
		}
		Long n = hibSession.createQuery("select count(e) from Exam e inner join e.assignedRooms r where e.assignedPeriod.uniqueId = :p and e.examType.uniqueId = :t and r.uniqueId = :l", Long.class)
				.setParameter("p", a.getExamPeriodId()).setParameter("t", a.getExamTypeId()).setParameter("l", a.getLocationId()).uniqueResult();
		if (n != null && n > 0) return true;
		// a separate room of a special arrangement, used while the period still has examinations
		Long sep = hibSession.createQuery("select count(x) from ExamSpecialArrangement x where x.sessionId = :s and x.locationId = :l", Long.class)
				.setParameter("s", sessionId).setParameter("l", a.getLocationId()).uniqueResult();
		if (sep == null || sep == 0) return false;
		Long any = hibSession.createQuery("select count(e) from Exam e where e.assignedPeriod.uniqueId = :p and e.examType.uniqueId = :t", Long.class)
				.setParameter("p", a.getExamPeriodId()).setParameter("t", a.getExamTypeId()).uniqueResult();
		return any != null && any > 0;
	}
}
