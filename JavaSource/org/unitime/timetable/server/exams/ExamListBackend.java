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
import org.unitime.timetable.gwt.shared.ExamListInterface.ExamListRequest;
import org.unitime.timetable.gwt.shared.ExamListInterface.ExamListResponse;
import org.unitime.timetable.gwt.shared.ExamListInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backing bean for the legacy examList.action (Examinations) Struts
 * page. Returns the applicable examination types for the current academic
 * session (to populate a selector) and the exams of the selected type projected
 * to string rows. Permission-gated by {@link Right#Examinations} (Session
 * qualified). Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamListRequest.class)
public class ExamListBackend implements GwtRpcImplementation<ExamListRequest, ExamListResponse> {

	@Override
	public ExamListResponse execute(ExamListRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.Examinations);

		ExamListResponse response = new ExamListResponse();
		response.setTitle("Examinations");

		// Applicable exam types (mirror the legacy examList.action selector).
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

		for (String c : new String[] { "Examination", "Length", "Seating Type", "Size",
				"Max Rooms", "Instructor", "Assigned Period", "Assigned Room" })
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
			Row r = response.addRow(exam.getUniqueId());

			// Examination label (owner names).
			StringBuilder owners = new StringBuilder();
			for (ExamOwner owner : new TreeSet<ExamOwner>(exam.getOwners())) {
				if (owners.length() > 0) owners.append(", ");
				owners.append(owner.getLabel());
			}
			r.add(owners.length() > 0 ? owners.toString() : exam.getLabel());

			r.add(exam.getLength() == null ? "" : exam.getLength().toString());
			r.add(Exam.getSeatingTypeLabel(exam.getSeatingType() == null ? Exam.sSeatingTypeNormal : exam.getSeatingType()));
			r.add(String.valueOf(exam.getSize()));
			r.add(exam.getMaxNbrRooms() == null ? "" : exam.getMaxNbrRooms().toString());

			StringBuilder instructors = new StringBuilder();
			if (exam.getInstructors() != null)
				for (DepartmentalInstructor di : new TreeSet<DepartmentalInstructor>(exam.getInstructors())) {
					if (instructors.length() > 0) instructors.append(", ");
					instructors.append(di.getNameLastFirst());
				}
			r.add(instructors.toString());

			r.add(exam.getAssignedPeriod() == null ? "" : exam.getAssignedPeriod().getAbbreviation());

			StringBuilder rooms = new StringBuilder();
			if (exam.getAssignedRooms() != null)
				for (Location room : exam.getAssignedRooms()) {
					if (rooms.length() > 0) rooms.append(", ");
					rooms.append(room.getLabel());
				}
			r.add(rooms.toString());
		}

		return response;
	}
}
