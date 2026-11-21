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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Transaction;
import org.unitime.commons.Email;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.BoardCell;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Candidate;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Duty;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.IdName;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.InvigilationConfig;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.InvigilationRequest;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.InvigilationResponse;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.InvigilatorInfo;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Issue;
import org.unitime.timetable.gwt.shared.ExamSeatingInterface.Period;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamSeat;
import org.unitime.timetable.model.InvigilationAssignment;
import org.unitime.timetable.model.Invigilator;
import org.unitime.timetable.model.InvigilatorAvailability;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.dao.InvigilatorDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.server.seating.ExamAllocationSupport;
import org.unitime.timetable.server.seating.ExamPlanData;
import org.unitime.timetable.server.seating.ExamPlanData.ExamData;

/**
 * Backend of the Invigilation page (InvigilationRequest). Viewing needs Right.Invigilation, changes
 * Right.InvigilationEdit; approval and publication an approver (Right.AssignmentChangeApprove).
 */
@GwtRpcImplements(InvigilationRequest.class)
public class InvigilationBackend implements GwtRpcImplementation<InvigilationRequest, InvigilationResponse> {
	private static Logger sLog = LogManager.getLogger(InvigilationBackend.class);
	private static final String KIND = ExamAllocationSupport.INVIGILATION;

	@Override
	public InvigilationResponse execute(InvigilationRequest request, SessionContext context) {
		context.checkPermission(Right.Invigilation);
		String op = (request.operation == null ? "INIT" : request.operation);
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		org.hibernate.Session hibSession = InvigilatorDAO.getInstance().getSession();
		InvigilationResponse response = new InvigilationResponse();
		response.examTypes = ExamAllocationSupport.examTypes(context);
		Long examTypeId = ExamAllocationSupport.examType(context, request.examTypeId, response.examTypes);
		response.examTypeId = examTypeId;
		response.canEdit = context.hasPermission(Right.InvigilationEdit);
		if (examTypeId == null) { response.message = "No examination type is available."; return response; }
		context.setAttribute("Exam.Type", examTypeId);
		String who = context.getUser().getName();

		// session-level edits that do not need the plan
		switch (op) {
		case "SAVE_CONFIG": {
			edit(context);
			InvigilationConfig c = request.config;
			if (c == null || c.studentsPerInvigilator < 1 || c.minPerRoom < 0 || c.defaultMaxPerDay < 1 || c.defaultMaxTotal < 1)
				throw new GwtRpcException("Students per invigilator, daily and total limits must be at least 1.");
			Transaction tx = hibSession.beginTransaction();
			try {
				org.unitime.timetable.model.InvigilationConfig x = config(hibSession, sessionId);
				if (x == null) { x = new org.unitime.timetable.model.InvigilationConfig(); x.setSessionId(sessionId); }
				x.setStudentsPerInvigilator(c.studentsPerInvigilator); x.setMinPerRoom(c.minPerRoom); x.setChiefPerRoom(c.chiefPerRoom);
				x.setExtraSpecialRoom(Math.max(0, c.extraSpecialRoom)); x.setReservesPerPeriod(Math.max(0, c.reservesPerPeriod));
				x.setDefaultMaxPerDay(c.defaultMaxPerDay); x.setDefaultMaxTotal(c.defaultMaxTotal); x.setAvoidOwnExam(c.avoidOwnExam);
				if (x.getUniqueId() == null) hibSession.persist(x); else hibSession.merge(x);
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, "RULES", null,
						"1 per " + c.studentsPerInvigilator + " students, min " + c.minPerRoom + " per room" + (c.chiefPerRoom ? ", chief per room" : "") + ", +" + c.extraSpecialRoom + " in separate rooms, " + c.reservesPerPeriod + " reserve(s), max " + c.defaultMaxPerDay + "/day " + c.defaultMaxTotal + " total" + (c.avoidOwnExam ? ", not own paper" : ""), true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			op = "CONFIG"; break;
		}
		case "SAVE_INVIGILATOR": case "DELETE_INVIGILATOR": {
			edit(context);
			Transaction tx = hibSession.beginTransaction();
			try {
				if ("DELETE_INVIGILATOR".equals(op)) {
					Invigilator i = hibSession.get(Invigilator.class, request.invigilatorId);
					if (i != null) {
						hibSession.createMutationQuery("delete from InvigilationAssignment where invigilatorId = :i").setParameter("i", i.getUniqueId()).executeUpdate();
						hibSession.createMutationQuery("delete from InvigilatorAvailability where invigilatorId = :i").setParameter("i", i.getUniqueId()).executeUpdate();
						ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, op, null, i.getName(), true);
						hibSession.remove(i);
					}
				} else {
					Invigilator i = save(hibSession, sessionId, request.invigilator);
					ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, op, null, i.getName() + " (" + i.getDutyTypes() + ")", true);
				}
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			op = "INVIGILATORS"; break;
		}
		case "IMPORT": {
			edit(context);
			if (request.csv == null || request.csv.trim().isEmpty()) throw new GwtRpcException("Paste CSV lines: name, email, external id, department code, duty types, max per day, max total.");
			int added = 0, updated = 0; List<String> errors = new ArrayList<String>();
			Transaction tx = hibSession.beginTransaction();
			try {
				int line = 0;
				for (String row: request.csv.split("\\r?\\n")) {
					line++;
					if (row.trim().isEmpty() || (line == 1 && row.toLowerCase().startsWith("name"))) continue;
					String[] f = row.split(",", -1);
					InvigilatorInfo x = new InvigilatorInfo();
					x.name = f[0].trim(); x.email = f.length > 1 ? f[1].trim() : null; x.externalId = f.length > 2 ? f[2].trim() : null;
					String dept = f.length > 3 ? f[3].trim() : null;
					x.dutyTypes = f.length > 4 && !f[4].trim().isEmpty() ? f[4].trim().replace(';', ',').replace(' ', ',') : InvigilationPlanner.INVIGILATOR;
					try {
						x.maxPerDay = f.length > 5 && !f[5].trim().isEmpty() ? Integer.valueOf(f[5].trim()) : null;
						x.maxTotal = f.length > 6 && !f[6].trim().isEmpty() ? Integer.valueOf(f[6].trim()) : null;
					} catch (NumberFormatException e) { errors.add("line " + line + ": limits must be numbers"); continue; }
					if (x.name.isEmpty()) { errors.add("line " + line + ": no name"); continue; }
					if (dept != null && !dept.isEmpty()) {
						Department d = Department.findByDeptCode(dept, sessionId);
						if (d == null) errors.add("line " + line + ": unknown department " + dept + " (ignored)"); else x.departmentId = d.getUniqueId();
					}
					x.active = true;
					Invigilator existing = (x.externalId == null || x.externalId.isEmpty() ? null : hibSession.createQuery("from Invigilator where sessionId = :s and externalId = :e", Invigilator.class)
							.setParameter("s", sessionId).setParameter("e", x.externalId).setMaxResults(1).uniqueResult());
					if (existing != null) { x.id = existing.getUniqueId(); updated++; } else added++;
					save(hibSession, sessionId, x);
				}
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, "IMPORT", null, added + " added, " + updated + " updated" + (errors.isEmpty() ? "" : "; " + errors), true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			response.message = added + " invigilator(s) added, " + updated + " updated." + (errors.isEmpty() ? "" : " Problems: " + String.join("; ", errors));
			op = "INVIGILATORS"; break;
		}
		case "SET_AVAILABILITY": {
			edit(context);
			setAvailability(hibSession, request.invigilatorId, request.periodId, request.availability, "ADMIN", request.note);
			Transaction tx = hibSession.beginTransaction();
			try {
				Invigilator i = hibSession.get(Invigilator.class, request.invigilatorId);
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, "AVAILABILITY", request.periodId, (i == null ? "" : i.getName()) + ": " + request.availability, true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			op = "INVIGILATORS"; break;
		}
		default: break;
		}

		ExamPlanData data = new ExamPlanData(hibSession, sessionId, examTypeId);
		// duties left behind by examinations that moved after allocation (editors: removed and recorded; others: hidden by duties())
		int stale = (response.canEdit ? ExamAllocationSupport.removeStale(hibSession, context, data, KIND) : 0);
		InvigilationConfig cfg = toConfig(config(hibSession, sessionId));
		List<Invigilator> invigilators = hibSession.createQuery("from Invigilator where sessionId = :s order by name", Invigilator.class).setParameter("s", sessionId).list();
		List<InvigilatorAvailability> availability = hibSession.createQuery("select a from InvigilatorAvailability a, Invigilator i where a.invigilatorId = i.uniqueId and i.sessionId = :s", InvigilatorAvailability.class).setParameter("s", sessionId).list();
		List<StaffAbsence> absences = hibSession.createQuery("from StaffAbsence where session.uniqueId = :s", StaffAbsence.class).setParameter("s", sessionId).list();
		InvigilationPlanner planner = new InvigilationPlanner(data, cfg, invigilators, availability, absences);
		Map<Long, List<ExamSeat>> seats = seatsByPeriod(hibSession, data);
		List<InvigilationPlanner.Requirement> requirements = planner.requirements(seats);

		switch (op) {
		case "ALLOCATE": {
			edit(context);
			boolean keepLocked = (request.keepLocked == null || request.keepLocked);
			Transaction tx = hibSession.beginTransaction();
			try {
				List<InvigilationAssignment> existing = duties(hibSession, data);
				List<InvigilationAssignment> kept = new ArrayList<InvigilationAssignment>();
				for (InvigilationAssignment a: existing) {
					if (keepLocked && Boolean.TRUE.equals(a.getLocked())) kept.add(a); else hibSession.remove(a);
				}
				hibSession.flush();
				List<InvigilationAssignment> all = new ArrayList<InvigilationAssignment>(kept);
				List<InvigilationPlanner.Slot> unfilled = planner.allocate(requirements, all, who);
				int created = 0;
				for (InvigilationAssignment a: all) if (a.getUniqueId() == null) { hibSession.persist(a); created++; }
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, "AUTO_ALLOCATE", null,
						created + " duties assigned, " + kept.size() + " locked kept, " + unfilled.size() + " unfilled", true);
				hibSession.flush(); tx.commit();
				response.message = created + " duties assigned" + (kept.isEmpty() ? "" : ", " + kept.size() + " locked kept") + (unfilled.isEmpty() ? "." : "; " + unfilled.size() + " could not be filled (see conflicts).");
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			break;
		}
		case "ASSIGN": {
			edit(context);
			Invigilator i = planner.invigilator(request.invigilatorId);
			if (i == null) throw new GwtRpcException("Unknown invigilator.");
			String dutyType = (request.dutyType == null ? (request.roomId == null ? InvigilationPlanner.RESERVE : InvigilationPlanner.INVIGILATOR) : request.dutyType);
			List<InvigilationAssignment> all = duties(hibSession, data);
			InvigilationAssignment replaced = (request.dutyId == null ? null : find(all, request.dutyId));
			InvigilationPlanner.Slot slot = new InvigilationPlanner.Slot(request.periodId, request.roomId, dutyType);
			String why = planner.reason(i, slot, all, request.dutyId);
			if (why != null && why.startsWith("already on duty")) throw new GwtRpcException(i.getName() + " is " + why + ".");
			if (why != null && !Boolean.TRUE.equals(request.override)) throw new GwtRpcException(i.getName() + ": " + why + ". Assign anyway with override.");
			Transaction tx = hibSession.beginTransaction();
			try {
				InvigilationAssignment a = (replaced != null ? replaced : planner.newDuty(slot, i.getUniqueId(), who));
				String before = (replaced == null ? null : name(planner, replaced.getInvigilatorId()));
				a.setInvigilatorId(i.getUniqueId()); a.setDutyType(dutyType); a.setSource("MANUAL"); a.setLocked(true); a.setLastModified(new Date()); a.setModifiedBy(who);
				if (a.getUniqueId() == null) hibSession.persist(a); else hibSession.merge(a);
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, before == null ? "ASSIGN" : "REPLACE", request.periodId,
						(before == null ? "" : before + " -> ") + i.getName() + " (" + dutyType.toLowerCase() + ", " + roomName(data, request.roomId) + ")" + (why == null ? "" : " [override: " + why + "]"), true);
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			response.message = "Assigned.";
			break;
		}
		case "UNASSIGN": case "LOCK": case "UNLOCK": {
			edit(context);
			InvigilationAssignment a = find(duties(hibSession, data), request.dutyId);
			if (a == null) throw new GwtRpcException("Unknown duty.");
			Transaction tx = hibSession.beginTransaction();
			try {
				if ("UNASSIGN".equals(op)) hibSession.remove(a);
				else { a.setLocked("LOCK".equals(op)); a.setLastModified(new Date()); a.setModifiedBy(who); hibSession.merge(a); }
				ExamAllocationSupport.log(hibSession, context, sessionId, examTypeId, KIND, op, a.getExamPeriodId(),
						name(planner, a.getInvigilatorId()) + " (" + a.getDutyType().toLowerCase() + ", " + roomName(data, a.getLocationId()) + ")", "UNASSIGN".equals(op));
				hibSession.flush(); tx.commit();
			} catch (RuntimeException e) { tx.rollback(); throw e; }
			break;
		}
		case "APPROVE": case "PUBLISH": case "REOPEN": {
			int errors = 0;
			for (Issue x: planner.validate(requirements, duties(hibSession, data))) if ("ERROR".equals(x.severity)) errors++;
			response.message = ExamAllocationSupport.transition(hibSession, context, sessionId, examTypeId, KIND, op, errors, Boolean.TRUE.equals(request.override), request.note);
			if ("PUBLISH".equals(op)) response.message += " " + notifyInvigilators(data, planner, duties(hibSession, data), invigilators);
			break;
		}
		default: break;
		}

		// ---- response
		List<InvigilationAssignment> all = duties(hibSession, data);
		response.status = ExamAllocationSupport.toStatus(hibSession, context, sessionId, examTypeId, KIND, Right.InvigilationEdit);
		response.config = cfg;
		for (ExamPeriod p: data.periods) {
			List<ExamData> exams = data.exams(p.getUniqueId());
			if (exams.isEmpty()) continue;
			Period x = new Period();
			x.id = p.getUniqueId(); x.name = data.periodName(p.getUniqueId()); x.date = p.getStartDateLabel(); x.nrExams = exams.size();
			x.nrRooms = data.rooms(p.getUniqueId()).size(); x.nrStudents = data.studentsOf(p.getUniqueId()).size();
			response.periods.add(x);
		}
		for (InvigilationPlanner.Requirement r: requirements) response.required += r.required;
		response.assigned = all.size();
		List<Issue> issues = planner.validate(requirements, all);
		for (Issue x: issues) if ("ERROR".equals(x.severity)) response.nrErrors++; else response.nrWarnings++;
		if ("INIT".equals(op) || "BOARD".equals(op) || "ALLOCATE".equals(op) || "ASSIGN".equals(op) || "UNASSIGN".equals(op) || "LOCK".equals(op) || "UNLOCK".equals(op) || "VALIDATE".equals(op) || "APPROVE".equals(op) || "PUBLISH".equals(op)) {
			response.board = board(data, planner, requirements, all);
			response.issues = issues;
		}
		if ("INIT".equals(op) || "INVIGILATORS".equals(op)) {
			response.invigilators = invigilators(hibSession, data, planner, invigilators, all);
			response.departments = departments(hibSession, sessionId);
		}
		if ("CANDIDATES".equals(op)) response.candidates = candidates(planner, invigilators, all, request);
		if ("DUTIES".equals(op)) response.duties = toDuties(data, planner, all);
		if ("LOG".equals(op)) response.log = ExamAllocationSupport.logEntries(hibSession, data, KIND, 300);
		if (stale > 0) {
			String note = stale + " duty(ies) in examination rooms or periods that changed after allocation were removed. Allocate the affected periods again.";
			response.message = (response.message == null ? note : response.message + " " + note);
		}
		return response;
	}

	private static void edit(SessionContext context) { context.checkPermission(Right.InvigilationEdit); }

	static org.unitime.timetable.model.InvigilationConfig config(org.hibernate.Session hibSession, Long sessionId) {
		return hibSession.createQuery("from InvigilationConfig where sessionId = :s", org.unitime.timetable.model.InvigilationConfig.class).setParameter("s", sessionId).setMaxResults(1).uniqueResult();
	}

	static InvigilationConfig toConfig(org.unitime.timetable.model.InvigilationConfig x) {
		InvigilationConfig c = new InvigilationConfig();
		if (x != null) {
			c.studentsPerInvigilator = x.getStudentsPerInvigilator(); c.minPerRoom = x.getMinPerRoom(); c.chiefPerRoom = Boolean.TRUE.equals(x.getChiefPerRoom());
			c.extraSpecialRoom = x.getExtraSpecialRoom(); c.reservesPerPeriod = x.getReservesPerPeriod();
			c.defaultMaxPerDay = x.getDefaultMaxPerDay(); c.defaultMaxTotal = x.getDefaultMaxTotal(); c.avoidOwnExam = Boolean.TRUE.equals(x.getAvoidOwnExam());
		}
		return c;
	}

	private static Invigilator save(org.hibernate.Session hibSession, Long sessionId, InvigilatorInfo x) {
		if (x == null || x.name == null || x.name.trim().isEmpty()) throw new GwtRpcException("The invigilator needs a name.");
		Invigilator i = (x.id == null ? new Invigilator() : hibSession.get(Invigilator.class, x.id));
		if (i == null) throw new GwtRpcException("Unknown invigilator.");
		i.setSessionId(sessionId); i.setName(x.name.trim()); i.setEmail(blank(x.email)); i.setExternalId(blank(x.externalId));
		i.setDepartmentId(x.departmentId);
		String types = (x.dutyTypes == null || x.dutyTypes.trim().isEmpty() ? InvigilationPlanner.INVIGILATOR : x.dutyTypes.toUpperCase().replaceAll("[^A-Z,]", ""));
		i.setDutyTypes(types); i.setMaxPerDay(x.maxPerDay); i.setMaxTotal(x.maxTotal); i.setActive(x.active); i.setNote(blank(x.note));
		i.setInstructorId(null);
		if (i.getExternalId() != null) {
			List<DepartmentalInstructor> ins = hibSession.createQuery("from DepartmentalInstructor where externalUniqueId = :e and department.session.uniqueId = :s", DepartmentalInstructor.class)
					.setParameter("e", i.getExternalId()).setParameter("s", sessionId).setMaxResults(1).list();
			if (!ins.isEmpty()) i.setInstructorId(ins.get(0).getUniqueId());
		}
		if (i.getUniqueId() == null) hibSession.persist(i); else hibSession.merge(i);
		return i;
	}

	private static String blank(String s) { return s == null || s.trim().isEmpty() ? null : s.trim(); }

	public static void setAvailability(org.hibernate.Session hibSession, Long invigilatorId, Long periodId, String status, String source, String note) {
		if (invigilatorId == null || periodId == null) throw new GwtRpcException("Invigilator and period are required.");
		Transaction tx = hibSession.beginTransaction();
		try {
			InvigilatorAvailability a = hibSession.createQuery("from InvigilatorAvailability where invigilatorId = :i and examPeriodId = :p", InvigilatorAvailability.class)
					.setParameter("i", invigilatorId).setParameter("p", periodId).setMaxResults(1).uniqueResult();
			if (status == null || "AVAILABLE".equals(status)) { if (a != null) hibSession.remove(a); }
			else {
				if (!"UNAVAILABLE".equals(status) && !"PREFERRED".equals(status)) throw new GwtRpcException("Availability must be UNAVAILABLE, PREFERRED or AVAILABLE.");
				if (a == null) { a = new InvigilatorAvailability(); a.setInvigilatorId(invigilatorId); a.setExamPeriodId(periodId); }
				a.setStatus(status); a.setSource(source); a.setNote(note == null || note.length() <= 500 ? note : note.substring(0, 500));
				if (a.getUniqueId() == null) hibSession.persist(a); else hibSession.merge(a);
			}
			hibSession.flush(); tx.commit();
		} catch (RuntimeException e) { tx.rollback(); throw e; }
	}

	/** The duties of the examination type, without stale ones (see ExamAllocationSupport.isStale). */
	static List<InvigilationAssignment> duties(org.hibernate.Session hibSession, ExamPlanData data) {
		List<InvigilationAssignment> ret = new ArrayList<InvigilationAssignment>(
				hibSession.createQuery("from InvigilationAssignment where sessionId = :s and examTypeId = :t", InvigilationAssignment.class)
				.setParameter("s", data.sessionId).setParameter("t", data.examTypeId).list());
		Map<Long, Set<Long>> roomsByPeriod = new HashMap<Long, Set<Long>>();
		ret.removeIf(a -> ExamAllocationSupport.isStale(data, a, roomsByPeriod));
		return ret;
	}

	private static InvigilationAssignment find(List<InvigilationAssignment> all, Long id) {
		if (id == null) return null;
		for (InvigilationAssignment a: all) if (a.getUniqueId().equals(id)) return a;
		return null;
	}

	static Map<Long, List<ExamSeat>> seatsByPeriod(org.hibernate.Session hibSession, ExamPlanData data) {
		Map<Long, List<ExamSeat>> ret = new HashMap<Long, List<ExamSeat>>();
		for (ExamSeat s: hibSession.createQuery("from ExamSeat where sessionId = :s and examTypeId = :t", ExamSeat.class).setParameter("s", data.sessionId).setParameter("t", data.examTypeId).list()) {
			List<ExamSeat> l = ret.get(s.getExamPeriodId());
			if (l == null) { l = new ArrayList<ExamSeat>(); ret.put(s.getExamPeriodId(), l); }
			l.add(s);
		}
		return ret;
	}

	private static String name(InvigilationPlanner planner, Long id) {
		Invigilator i = planner.invigilator(id);
		return i == null ? "invigilator " + id : i.getName();
	}

	private static String roomName(ExamPlanData data, Long roomId) {
		return roomId == null ? "Reserve" : data.location(roomId) == null ? "room " + roomId : data.location(roomId).getLabel();
	}

	static Duty toDuty(ExamPlanData data, InvigilationPlanner planner, InvigilationAssignment a, List<InvigilationAssignment> all) {
		Duty d = new Duty();
		ExamPeriod p = data.periodById.get(a.getExamPeriodId());
		d.id = a.getUniqueId(); d.invigilatorId = a.getInvigilatorId(); d.invigilator = name(planner, a.getInvigilatorId());
		d.periodId = a.getExamPeriodId(); d.period = data.periodName(a.getExamPeriodId());
		if (p != null) { d.date = p.getStartDateLabel(); d.time = p.getStartTimeLabel() + " - " + p.getEndTimeLabel(); }
		d.roomId = a.getLocationId(); d.room = roomName(data, a.getLocationId()); d.dutyType = a.getDutyType();
		d.locked = Boolean.TRUE.equals(a.getLocked()); d.manual = "MANUAL".equals(a.getSource());
		if (all != null) d.problems.addAll(planner.problems(a, all));
		return d;
	}

	private static List<Duty> toDuties(ExamPlanData data, InvigilationPlanner planner, List<InvigilationAssignment> all) {
		List<Duty> ret = new ArrayList<Duty>();
		for (InvigilationAssignment a: all) ret.add(toDuty(data, planner, a, all));
		Collections.sort(ret, new Comparator<Duty>() {
			@Override
			public int compare(Duty a, Duty b) {
				int cmp = a.invigilator.compareTo(b.invigilator);
				return cmp != 0 ? cmp : Long.compare(start(data, a.periodId), start(data, b.periodId));
			}
		});
		return ret;
	}

	private static long start(ExamPlanData data, Long periodId) {
		ExamPeriod p = data.periodById.get(periodId);
		return p == null ? 0 : p.getStartTime().getTime();
	}

	private static List<BoardCell> board(ExamPlanData data, InvigilationPlanner planner, List<InvigilationPlanner.Requirement> requirements, List<InvigilationAssignment> all) {
		List<BoardCell> ret = new ArrayList<BoardCell>();
		for (InvigilationPlanner.Requirement r: requirements) {
			BoardCell c = new BoardCell();
			c.periodId = r.periodId; c.roomId = r.roomId; c.room = r.room; c.students = r.students; c.required = r.required; c.chiefRequired = r.chief; c.special = r.special;
			for (InvigilationAssignment a: all)
				if (a.getExamPeriodId().equals(r.periodId) && (r.roomId == null ? a.getLocationId() == null : r.roomId.equals(a.getLocationId())))
					c.duties.add(toDuty(data, planner, a, all));
			c.assigned = c.duties.size();
			ret.add(c);
		}
		return ret;
	}

	private static List<InvigilatorInfo> invigilators(org.hibernate.Session hibSession, ExamPlanData data, InvigilationPlanner planner, List<Invigilator> invigilators, List<InvigilationAssignment> all) {
		Map<Long, String> depts = new HashMap<Long, String>();
		for (IdName d: departments(hibSession, data.sessionId)) depts.put(d.id, d.name);
		List<InvigilatorInfo> ret = new ArrayList<InvigilatorInfo>();
		for (Invigilator i: invigilators) {
			InvigilatorInfo x = new InvigilatorInfo();
			x.id = i.getUniqueId(); x.externalId = i.getExternalId(); x.name = i.getName(); x.email = i.getEmail();
			x.departmentId = i.getDepartmentId(); x.department = depts.get(i.getDepartmentId()); x.instructorId = i.getInstructorId();
			x.dutyTypes = i.getDutyTypes(); x.maxPerDay = i.getMaxPerDay(); x.maxTotal = i.getMaxTotal(); x.active = Boolean.TRUE.equals(i.getActive()); x.note = i.getNote();
			x.nrDuties = planner.load(i, all);
			for (ExamPeriod p: data.periods) {
				String av = planner.availability(i.getUniqueId(), p.getUniqueId());
				if (av != null) x.availability.add(new IdName(p.getUniqueId(), av, data.periodName(p.getUniqueId())));
			}
			ret.add(x);
		}
		return ret;
	}

	static List<IdName> departments(org.hibernate.Session hibSession, Long sessionId) {
		List<IdName> ret = new ArrayList<IdName>();
		for (Department d: hibSession.createQuery("from Department where session.uniqueId = :s order by deptCode", Department.class).setParameter("s", sessionId).list())
			ret.add(new IdName(d.getUniqueId(), d.getDeptCode(), d.getName()));
		return ret;
	}

	private static List<Candidate> candidates(InvigilationPlanner planner, List<Invigilator> invigilators, List<InvigilationAssignment> all, InvigilationRequest request) {
		String dutyType = (request.dutyType == null ? (request.roomId == null ? InvigilationPlanner.RESERVE : InvigilationPlanner.INVIGILATOR) : request.dutyType);
		InvigilationPlanner.Slot slot = new InvigilationPlanner.Slot(request.periodId, request.roomId, dutyType);
		List<Candidate> ret = new ArrayList<Candidate>();
		for (Invigilator i: invigilators) {
			if (request.dutyId != null) {
				boolean current = false;
				for (InvigilationAssignment a: all) if (a.getUniqueId().equals(request.dutyId) && a.getInvigilatorId().equals(i.getUniqueId())) current = true;
				if (current) continue;
			}
			Candidate c = new Candidate();
			c.invigilatorId = i.getUniqueId(); c.name = i.getName();
			c.reason = planner.reason(i, slot, all, request.dutyId);
			c.suitable = (c.reason == null);
			c.load = planner.load(i, all); c.dutiesThatDay = planner.dutiesThatDay(i, request.periodId, all);
			c.preferred = "PREFERRED".equals(planner.availability(i.getUniqueId(), request.periodId));
			ret.add(c);
		}
		Collections.sort(ret, new Comparator<Candidate>() {
			@Override
			public int compare(Candidate a, Candidate b) {
				if (a.suitable != b.suitable) return a.suitable ? -1 : 1;
				if (a.preferred != b.preferred) return a.preferred ? -1 : 1;
				if (a.load != b.load) return Integer.compare(a.load, b.load);
				if (a.dutiesThatDay != b.dutiesThatDay) return Integer.compare(a.dutiesThatDay, b.dutiesThatDay);
				return a.name.compareTo(b.name);
			}
		});
		return ret;
	}

	private static String notifyInvigilators(ExamPlanData data, InvigilationPlanner planner, List<InvigilationAssignment> all, List<Invigilator> invigilators) {
		int sent = 0, noEmail = 0, withDuties = 0; String failure = null;
		for (Invigilator i: invigilators) {
			List<InvigilationAssignment> mine = new ArrayList<InvigilationAssignment>();
			for (InvigilationAssignment a: all) if (a.getInvigilatorId().equals(i.getUniqueId())) mine.add(a);
			if (mine.isEmpty()) continue;
			withDuties++;
			if (i.getEmail() == null) { noEmail++; continue; }
			if (failure != null) continue;
			try {
				Email mail = Email.createEmail();
				mail.addRecipient(i.getEmail(), i.getName());
				mail.setSubject("Your invigilation duties");
				StringBuffer text = new StringBuffer("Dear " + i.getName() + ",\n\nyour invigilation duties:\n\n");
				for (InvigilationAssignment a: mine) text.append(data.periodName(a.getExamPeriodId())).append(", ").append(roomName(data, a.getLocationId())).append(" (").append(a.getDutyType().toLowerCase()).append(")\n");
				mail.setText(text.toString());
				mail.send(); sent++;
			} catch (Throwable t) { failure = t.getMessage(); sLog.info("Invigilation notification not sent: " + t.getMessage()); }
		}
		return withDuties + " invigilator(s) can now see their duties under My Examinations" + (sent > 0 ? "; " + sent + " email(s) sent" : "") + (failure != null ? "; email not sent (" + failure + ")" : "") + (noEmail > 0 ? "; " + noEmail + " without an email address" : "") + ".";
	}
}
