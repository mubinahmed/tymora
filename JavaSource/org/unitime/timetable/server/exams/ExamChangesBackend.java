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

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamChangesInterface.ExamChangeRow;
import org.unitime.timetable.gwt.shared.ExamChangesInterface.ExamChangesRequest;
import org.unitime.timetable.gwt.shared.ExamChangesInterface.ExamChangesResponse;
import org.unitime.timetable.gwt.shared.ExamChangesInterface.Mode;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.dao.ExamTypeDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo;
import org.unitime.timetable.solver.service.ExaminationSolverService;

/**
 * Read-only backing bean for the legacy examChanges.action (Examination
 * Assignment Changes) Struts page. The list of changed examinations is read
 * <b>only</b> from the in-memory examination solver held by the
 * {@link ExaminationSolverService} (autowired, mirroring the solver-service
 * pattern in {@code ManageSolversBackend}); no persisted state is modified.
 *
 * <p>For each variable whose current assignment differs from the reference
 * solution the solver returns a pair of {@link ExamAssignmentInfo}: index 0 is
 * the reference ("from") assignment (the initial input or the best solution,
 * per {@link Mode}) and index 1 is the current ("to") assignment. Those are
 * projected here to a flat {@link ExamChangeRow} (period from&rarr;to, room(s)
 * from&rarr;to, seating, students, instructor and current conflict counters).</p>
 *
 * <p>When no exam solver is loaded in memory the response is returned with
 * {@code solverLoaded=false} and an informational message (never an exception),
 * so the Angular screen can show a "solver not loaded" banner, mirroring the
 * existing course-solver screens.</p>
 *
 * <p>Deferred (kept on the legacy page): the interactive per-exam suggestions
 * dialog, the delta / distance-conflict decorations on the conflict counters,
 * and PDF/CSV export. Additive: introduces no changes to existing behavior.</p>
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamChangesRequest.class)
public class ExamChangesBackend implements GwtRpcImplementation<ExamChangesRequest, ExamChangesResponse> {
	private static Logger sLog = LogManager.getLogger(ExamChangesBackend.class);

	@Autowired ExaminationSolverService examinationSolverService;

	@Override
	public ExamChangesResponse execute(ExamChangesRequest request, SessionContext context) {
		// Session-scoped permission, exactly as the legacy ExamChangesAction.
		context.checkPermission(Right.ExaminationAssignmentChanges);

		ExamChangesResponse response = new ExamChangesResponse();
		response.setTitle("Examination Assignment Changes");
		response.setMode(request.getMode() == null ? Mode.Initial : request.getMode());

		ExamSolverProxy solver = null;
		try {
			solver = examinationSolverService.getSolver();
		} catch (Exception e) {
			sLog.warn("Unable to obtain the examination solver: " + e.getMessage(), e);
		}

		if (solver == null) {
			// No exam solver loaded: return a typed, empty response with a flag/message.
			response.setSolverLoaded(false);
			response.setMessage("Examination solver is not loaded in memory.");
			return response;
		}

		response.setSolverLoaded(true);

		// Report the exam type currently held by the loaded solver.
		Long examTypeId = null;
		try {
			examTypeId = solver.getExamTypeId();
		} catch (Exception e) {
			sLog.warn("Unable to read the loaded exam type: " + e.getMessage(), e);
		}
		response.setExamTypeId(examTypeId);
		if (examTypeId != null) {
			try {
				ExamType type = ExamTypeDAO.getInstance().get(examTypeId);
				if (type != null) response.setExamTypeLabel(type.getLabel());
			} catch (Exception e) {}
		}

		// Optional subject-area filter; a negative value (the default) means all
		// subject areas, matching the legacy solver contract.
		Long subjectAreaId = request.getSubjectAreaId();
		if (subjectAreaId == null) subjectAreaId = -1L;

		Collection<ExamAssignmentInfo[]> changes = null;
		try {
			if (Mode.Best.equals(response.getMode()))
				changes = solver.getChangesToBest(subjectAreaId);
			else
				changes = solver.getChangesToInitial(subjectAreaId);
		} catch (Exception e) {
			sLog.warn("Failed to read examination assignment changes: " + e.getMessage(), e);
			response.setMessage("Unable to read examination assignment changes: " + e.getMessage());
			return response;
		}

		if (changes == null) return response;

		final String notAssigned = "Not Assigned";
		for (ExamAssignmentInfo[] change : changes) {
			if (change == null || change.length < 2) continue;
			ExamAssignmentInfo from = change[0]; // reference (initial/best)
			ExamAssignmentInfo to = change[1];   // current
			if (to == null) continue;

			ExamChangeRow row = new ExamChangeRow();

			try { row.setExamId(to.getExamId()); } catch (Exception e) {}
			try { row.setExam(to.getExamName()); } catch (Exception e) {}

			// Period from -> to (not-assigned rendered as a marker).
			try {
				row.setFromPeriod(from == null || from.getPeriodId() == null ? notAssigned : from.getPeriodAbbreviation());
			} catch (Exception e) { row.setFromPeriod(notAssigned); }
			try {
				row.setToPeriod(to.getPeriodId() == null ? notAssigned : to.getPeriodAbbreviation());
			} catch (Exception e) { row.setToPeriod(notAssigned); }

			// Room(s) from -> to.
			try {
				row.setFromRoom(from == null || from.getPeriodId() == null ? notAssigned : from.getRoomsName(", "));
			} catch (Exception e) { row.setFromRoom(notAssigned); }
			try {
				row.setToRoom(to.getPeriodId() == null ? notAssigned : to.getRoomsName(", "));
			} catch (Exception e) { row.setToRoom(notAssigned); }

			try { row.setSeatingType(to.getSeatingTypeLabel()); } catch (Exception e) {}
			try { row.setStudents(to.getNrStudents()); } catch (Exception e) {}
			try { row.setInstructor(to.getInstructorName(", ")); } catch (Exception e) {}

			try { row.setDirectConflicts(to.getNrDirectConflicts()); } catch (Exception e) {}
			try { row.setMoreThanTwoADayConflicts(to.getNrMoreThanTwoConflicts()); } catch (Exception e) {}
			try { row.setBackToBackConflicts(to.getNrBackToBackConflicts()); } catch (Exception e) {}

			response.addRow(row);
		}

		return response;
	}
}
