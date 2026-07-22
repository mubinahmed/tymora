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
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Read-only projection for the legacy examChanges.action (Examination
 * Assignment Changes) Struts page. The data is served entirely from the
 * in-memory examination solver ({@code ExamSolverProxy.getChangesToInitial /
 * getChangesToBest}); no persisted or mutating operation is exposed. The
 * request carries the comparison mode (Initial / Best) and an optional subject
 * area filter; when no exam solver is loaded in memory the response is returned
 * with {@code solverLoaded=false} and a message (never an error) so the UI can
 * render a "solver not loaded" banner, mirroring the existing course-solver
 * screens. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class ExamChangesInterface implements IsSerializable {

	/** Comparison mode: current assignment vs. the initial (input) or best solution. */
	public static enum Mode implements IsSerializable {
		Initial, Best
	}

	/** One selectable examination type (informational; the solver holds a single loaded type). */
	public static class ExamChangeTypeInfo implements IsSerializable {
		private Long iId;
		private String iLabel;

		public ExamChangeTypeInfo() {}
		public ExamChangeTypeInfo(Long id, String label) { iId = id; iLabel = label; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/**
	 * One changed examination: its "from" (initial/best) and "to" (current)
	 * period and room(s), plus current conflict counters. Not-assigned states
	 * are rendered by the backend as a textual marker.
	 */
	public static class ExamChangeRow implements IsSerializable {
		private Long iExamId;
		private String iExam;
		private String iFromPeriod;
		private String iToPeriod;
		private String iFromRoom;
		private String iToRoom;
		private String iSeatingType;
		private int iStudents;
		private String iInstructor;
		private int iDirectConflicts;
		private int iMoreThanTwoADayConflicts;
		private int iBackToBackConflicts;

		public ExamChangeRow() {}

		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }

		public String getExam() { return iExam; }
		public void setExam(String exam) { iExam = exam; }

		public String getFromPeriod() { return iFromPeriod; }
		public void setFromPeriod(String fromPeriod) { iFromPeriod = fromPeriod; }

		public String getToPeriod() { return iToPeriod; }
		public void setToPeriod(String toPeriod) { iToPeriod = toPeriod; }

		public String getFromRoom() { return iFromRoom; }
		public void setFromRoom(String fromRoom) { iFromRoom = fromRoom; }

		public String getToRoom() { return iToRoom; }
		public void setToRoom(String toRoom) { iToRoom = toRoom; }

		public String getSeatingType() { return iSeatingType; }
		public void setSeatingType(String seatingType) { iSeatingType = seatingType; }

		public int getStudents() { return iStudents; }
		public void setStudents(int students) { iStudents = students; }

		public String getInstructor() { return iInstructor; }
		public void setInstructor(String instructor) { iInstructor = instructor; }

		public int getDirectConflicts() { return iDirectConflicts; }
		public void setDirectConflicts(int directConflicts) { iDirectConflicts = directConflicts; }

		public int getMoreThanTwoADayConflicts() { return iMoreThanTwoADayConflicts; }
		public void setMoreThanTwoADayConflicts(int c) { iMoreThanTwoADayConflicts = c; }

		public int getBackToBackConflicts() { return iBackToBackConflicts; }
		public void setBackToBackConflicts(int c) { iBackToBackConflicts = c; }
	}

	public static class ExamChangesRequest implements GwtRpcRequest<ExamChangesResponse> {
		private Long iExamTypeId;
		private Mode iMode = Mode.Initial;
		private Long iSubjectAreaId;

		public ExamChangesRequest() {}

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public Mode getMode() { return iMode; }
		public void setMode(Mode mode) { iMode = mode; }

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }

		@Override
		public String toString() {
			return "ExamChanges[type=" + iExamTypeId + ", mode=" + iMode + ", subjectArea=" + iSubjectAreaId + "]";
		}
	}

	public static class ExamChangesResponse implements GwtRpcResponse {
		private boolean iSolverLoaded = false;
		private String iMessage;
		private String iTitle;
		private Long iExamTypeId;
		private String iExamTypeLabel;
		private Mode iMode = Mode.Initial;
		private List<ExamChangeRow> iRows = new ArrayList<ExamChangeRow>();

		public ExamChangesResponse() {}

		public boolean isSolverLoaded() { return iSolverLoaded; }
		public void setSolverLoaded(boolean solverLoaded) { iSolverLoaded = solverLoaded; }

		public String getMessage() { return iMessage; }
		public void setMessage(String message) { iMessage = message; }

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public String getExamTypeLabel() { return iExamTypeLabel; }
		public void setExamTypeLabel(String examTypeLabel) { iExamTypeLabel = examTypeLabel; }

		public Mode getMode() { return iMode; }
		public void setMode(Mode mode) { iMode = mode; }

		public List<ExamChangeRow> getRows() { return iRows; }
		public void addRow(ExamChangeRow row) { iRows.add(row); }
	}
}
