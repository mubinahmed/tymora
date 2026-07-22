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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ExamAssignmentReportRequest;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ExamAssignmentReportResponse;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backing bean for the legacy examAssignmentReport.action (Examination
 * Reports) Struts page. Returns the applicable examination types for the current
 * academic session (to populate a selector) and the committed/persisted exam
 * assignments of the selected type projected to string rows. Permission-gated by
 * {@link Right#ExaminationReports} (Session qualified), matching the legacy action.
 *
 * <p>Only exams with a persisted assigned period are reported (the legacy report also
 * considers only assigned exams). Each row projects the persisted assignment details:
 * Examination, Enrollment, Seating Type, Date, Time, Room, Room Capacity, Instructor.
 * The legacy conflict columns and conflict/statistics sub-reports depend on the
 * in-memory examination solver (or heavy conflict recomputation) and are intentionally
 * out of scope here. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamAssignmentReportRequest.class)
public class ExamAssignmentReportBackend implements GwtRpcImplementation<ExamAssignmentReportRequest, ExamAssignmentReportResponse> {

	@Override
	public ExamAssignmentReportResponse execute(ExamAssignmentReportRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.Examinations);
		context.checkPermission(sessionId, "Session", Right.ExaminationReports);

		ExamAssignmentReportResponse response = new ExamAssignmentReportResponse();
		response.setTitle("Examination Assignment Report");

		// Applicable exam types (mirror the legacy exam-type selector).
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

		for (String c : new String[] { "Examination", "Enrollment", "Seating Type", "Date", "Time",
				"Room", "Room Capacity", "Instructor" })
			response.addColumn(c);

		if (examTypeId == null)
			return response;

		List<Exam> exams = new ArrayList<Exam>(Exam.findAll(sessionId, examTypeId));
		Collections.sort(exams, new Comparator<Exam>() {
			@Override
			public int compare(Exam a, Exam b) {
				return a.compareTo(b);
			}
		});

		for (Exam exam : exams) {
			// Only committed/persisted assignments (mirror the legacy assigned-exam report).
			ExamPeriod period = null;
			try { period = exam.getAssignedPeriod(); } catch (Exception e) {}
			if (period == null)
				continue;

			Row r = response.addRow(exam.getUniqueId());

			// Examination label (owner names, falling back to the exam label).
			try {
				StringBuilder owners = new StringBuilder();
				for (ExamOwner owner : new TreeSet<ExamOwner>(exam.getOwners())) {
					if (owners.length() > 0) owners.append(", ");
					owners.append(owner.getLabel());
				}
				r.add(owners.length() > 0 ? owners.toString() : exam.getLabel());
			} catch (Exception e) { r.add(""); }

			// Enrollment (number of students across owners).
			try { r.add(String.valueOf(exam.countStudents())); } catch (Exception e) { r.add(""); }

			// Seating type.
			try {
				r.add(Exam.getSeatingTypeLabel(exam.getSeatingType() == null ? Exam.sSeatingTypeNormal : exam.getSeatingType()));
			} catch (Exception e) { r.add(""); }

			// Date + Time from the persisted assigned period.
			try { r.add(period.getStartDateLabel()); } catch (Exception e) { r.add(""); }
			try { r.add(period.getStartTimeLabel() + " - " + period.getEndTimeLabel()); } catch (Exception e) { r.add(""); }

			// Assigned rooms + their (exam) capacities.
			try {
				StringBuilder rooms = new StringBuilder();
				StringBuilder caps = new StringBuilder();
				if (exam.getAssignedRooms() != null)
					for (Location room : exam.getAssignedRooms()) {
						if (rooms.length() > 0) rooms.append(", ");
						rooms.append(room.getLabel());
						if (caps.length() > 0) caps.append(", ");
						Integer cap = room.getExamCapacity();
						if (cap == null || cap.intValue() <= 0) cap = room.getCapacity();
						caps.append(cap == null ? "" : cap.toString());
					}
				r.add(rooms.toString());
				r.add(caps.toString());
			} catch (Exception e) { r.add(""); r.add(""); }

			// Instructors.
			try {
				StringBuilder instructors = new StringBuilder();
				if (exam.getInstructors() != null)
					for (DepartmentalInstructor di : new TreeSet<DepartmentalInstructor>(exam.getInstructors())) {
						if (instructors.length() > 0) instructors.append(", ");
						instructors.append(di.getNameLastFirst());
					}
				r.add(instructors.toString());
			} catch (Exception e) { r.add(""); }
		}

		return response;
	}
}
