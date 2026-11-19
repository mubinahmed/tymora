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
 * Typed contract for the Angular Examination Reports page (legacy examAssignmentReport.action).
 *
 * The backend runs the legacy report generators unchanged, so every report of the legacy page is
 * available with the same content: assignment, room, statistics, period usage, exams a day, room
 * splits, violated distributions, and the student and instructor clash reports (direct, back-to-back,
 * more than two a day, individual), plus individual student and instructor schedules. Tables come
 * back as plain-text cells with a per-column sort rank computed by the legacy comparators and,
 * where the legacy row links to an examination, its id.
 */
public class ExamAssignmentReportInterface implements IsSerializable {

	/** An id + label pair (examination types, subject areas). */
	public static class ExamTypeInfo implements IsSerializable {
		private Long iId;
		private String iLabel;

		public ExamTypeInfo() {}
		public ExamTypeInfo(Long id, String label) { iId = id; iLabel = label; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/** One selectable report (ExamAssignmentReportForm.ExamReport). */
	public static class ReportInfo implements IsSerializable {
		private String iName, iLabel, iGroup;

		public ReportInfo() {}
		public ReportInfo(String name, String label, String group) { iName = name; iLabel = label; iGroup = group; }
		public String getName() { return iName; }
		public String getLabel() { return iLabel; }
		public String getGroup() { return iGroup; }
	}

	public static class ExamAssignmentReportRequest implements GwtRpcRequest<ExamAssignmentReportResponse> {
		private Long iExamTypeId;
		private Long iSessionId;
		/** ExamAssignmentReportForm.ExamReport name; default ExamAssignmentReport */
		private String iReport;
		/** subject area id; -1 = all (needs Right.DepartmentIndependent); null = remembered / only one */
		private Long iSubjectAreaId;
		/** show classes / courses instead of examination names */
		private Boolean iShowSections;
		/** legacy text filter (exam name, room, student / instructor name depending on the report) */
		private String iFilter;

		public ExamAssignmentReportRequest() {}

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }
		public Long getSessionId() { return iSessionId; }
		public void setSessionId(Long sessionId) { iSessionId = sessionId; }
		public String getReport() { return iReport; }
		public void setReport(String report) { iReport = report; }
		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }
		public Boolean getShowSections() { return iShowSections; }
		public void setShowSections(Boolean showSections) { iShowSections = showSections; }
		public String getFilter() { return iFilter; }
		public void setFilter(String filter) { iFilter = filter; }

		@Override
		public String toString() { return "ExamAssignmentReport[type=" + iExamTypeId + ", report=" + iReport + ", subject=" + iSubjectAreaId + "]"; }
	}

	/** One table row: plain-text cells, a sort rank per column, and the linked examination (if any). */
	public static class ReportRow implements IsSerializable {
		private Long iExamId;
		private List<String> iCells = new ArrayList<String>();
		private List<Integer> iRanks = new ArrayList<Integer>();

		public ReportRow() {}
		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public List<String> getCells() { return iCells; }
		public List<Integer> getRanks() { return iRanks; }
	}

	public static class ExamAssignmentReportResponse implements GwtRpcResponse {
		private String iTitle;
		private Long iExamTypeId;
		private List<ExamTypeInfo> iExamTypes = new ArrayList<ExamTypeInfo>();
		private List<ReportInfo> iReports = new ArrayList<ReportInfo>();
		private String iReport;
		private List<ExamTypeInfo> iSubjectAreas = new ArrayList<ExamTypeInfo>();
		private Long iSubjectAreaId;
		private boolean iShowSections;
		private String iFilter;
		/** true when the data comes from a loaded examination solver, false for committed assignments */
		private boolean iSolverData;
		private int iNrExams;
		private String iMessage;
		private List<String> iColumns = new ArrayList<String>();
		/** per column: left, right or center */
		private List<String> iAlignments = new ArrayList<String>();
		private List<ReportRow> iRows = new ArrayList<ReportRow>();

		public ExamAssignmentReportResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }
		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }
		public List<ExamTypeInfo> getExamTypes() { return iExamTypes; }
		public void addExamType(ExamTypeInfo type) { iExamTypes.add(type); }
		public List<ReportInfo> getReports() { return iReports; }
		public void addReport(ReportInfo report) { iReports.add(report); }
		public String getReport() { return iReport; }
		public void setReport(String report) { iReport = report; }
		public List<ExamTypeInfo> getSubjectAreas() { return iSubjectAreas; }
		public void addSubjectArea(ExamTypeInfo subjectArea) { iSubjectAreas.add(subjectArea); }
		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }
		public boolean isShowSections() { return iShowSections; }
		public void setShowSections(boolean showSections) { iShowSections = showSections; }
		public String getFilter() { return iFilter; }
		public void setFilter(String filter) { iFilter = filter; }
		public boolean isSolverData() { return iSolverData; }
		public void setSolverData(boolean solverData) { iSolverData = solverData; }
		public int getNrExams() { return iNrExams; }
		public void setNrExams(int nrExams) { iNrExams = nrExams; }
		public String getMessage() { return iMessage; }
		public void setMessage(String message) { iMessage = message; }
		public List<String> getColumns() { return iColumns; }
		public void addColumn(String column) { iColumns.add(column); }
		public List<String> getAlignments() { return iAlignments; }
		public List<ReportRow> getRows() { return iRows; }
		public ReportRow addRow() {
			ReportRow row = new ReportRow();
			iRows.add(row);
			return row;
		}
	}
}
