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
import org.unitime.timetable.gwt.shared.ExamTimetableGridInterface.ExamTimetableGridRequest;
import org.unitime.timetable.gwt.shared.ExamTimetableGridInterface.ExamTimetableGridResponse;
import org.unitime.timetable.gwt.shared.ExamTimetableGridInterface.ExamTypeInfo;
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
 * Read-only backing bean for the legacy examGrid.action (Examination Timetable)
 * Struts page. That page renders a pixel time-grid of examination periods by
 * resource (room / instructor / subject) whose cells come either from the
 * in-memory examination solver (a transient, uncommitted solution) or, when no
 * matching solver is loaded, from the persisted committed assignment. This bean
 * surfaces only the persisted assignment ({@link Exam#getAssignedPeriod()} /
 * {@link Exam#getAssignedRooms()}) as a period-by-room table: one row per
 * (exam, assigned room) cell, ordered by examination period then room. Returns
 * the applicable examination types (to populate a selector) and the projected
 * rows for the selected type. Permission-gated by {@link Right#Examinations}
 * and {@link Right#ExaminationTimetable} (both Session qualified), mirroring the
 * legacy action's access check. Additive: introduces no changes to existing
 * behavior. The colored pixel grid, alternate resources/backgrounds, and PDF
 * export remain on the legacy page (deferred; solver-driven output is not
 * reproduced here).
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamTimetableGridRequest.class)
public class ExamTimetableGridBackend implements GwtRpcImplementation<ExamTimetableGridRequest, ExamTimetableGridResponse> {

	@Override
	public ExamTimetableGridResponse execute(ExamTimetableGridRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.Examinations);
		context.checkPermission(sessionId, "Session", Right.ExaminationTimetable);

		ExamTimetableGridResponse response = new ExamTimetableGridResponse();
		response.setTitle("Examination Timetable");

		// Applicable exam types (mirror the legacy examGrid.action selector).
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

		for (String c : new String[] { "Period", "Date", "Time", "Room", "Examination", "Size", "Instructor" })
			response.addColumn(c);

		if (examTypeId == null)
			return response;

		// Only exams with a persisted (committed) period assignment populate the grid.
		List<Exam> exams = new ArrayList<Exam>();
		for (Exam exam : Exam.findAll(sessionId, examTypeId)) {
			if (exam.getAssignedPeriod() != null)
				exams.add(exam);
		}
		Collections.sort(exams, new Comparator<Exam>() {
			@Override
			public int compare(Exam a, Exam b) {
				ExamPeriod pa = a.getAssignedPeriod(), pb = b.getAssignedPeriod();
				int cmp = pa.compareTo(pb);
				if (cmp != 0) return cmp;
				return a.compareTo(b);
			}
		});

		for (Exam exam : exams) {
			ExamPeriod period = exam.getAssignedPeriod();

			String periodAbbv = "", periodDate = "", periodTime = "";
			try { periodAbbv = period.getAbbreviation(); } catch (Exception e) {}
			try { periodDate = period.getStartDateLabel(); } catch (Exception e) {}
			try { periodTime = period.getStartTimeLabel() + " - " + period.getEndTimeLabel(); } catch (Exception e) {}

			String examLabel;
			try {
				StringBuilder owners = new StringBuilder();
				for (ExamOwner owner : new TreeSet<ExamOwner>(exam.getOwners())) {
					if (owners.length() > 0) owners.append(", ");
					owners.append(owner.getLabel());
				}
				examLabel = owners.length() > 0 ? owners.toString() : exam.getLabel();
			} catch (Exception e) {
				examLabel = "";
			}

			String size;
			try { size = String.valueOf(exam.getSize()); } catch (Exception e) { size = ""; }

			String instructors;
			try {
				StringBuilder sb = new StringBuilder();
				if (exam.getInstructors() != null)
					for (DepartmentalInstructor di : new TreeSet<DepartmentalInstructor>(exam.getInstructors())) {
						if (sb.length() > 0) sb.append(", ");
						sb.append(di.getNameLastFirst());
					}
				instructors = sb.toString();
			} catch (Exception e) {
				instructors = "";
			}

			// One row per (exam, assigned room) cell of the period-by-room grid.
			// An exam with no assigned room still yields a single row (blank room).
			List<String> rooms = new ArrayList<String>();
			try {
				if (exam.getAssignedRooms() != null)
					for (Location room : exam.getAssignedRooms())
						rooms.add(room.getLabel());
			} catch (Exception e) {}
			Collections.sort(rooms);
			if (rooms.isEmpty()) rooms.add("");

			for (String room : rooms) {
				Row r = response.addRow(exam.getUniqueId());
				r.add(periodAbbv);
				r.add(periodDate);
				r.add(periodTime);
				r.add(room);
				r.add(examLabel);
				r.add(size);
				r.add(instructors);
			}
		}

		return response;
	}
}
