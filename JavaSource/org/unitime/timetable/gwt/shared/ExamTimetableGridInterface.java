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
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Read-only view of the legacy examGrid.action (Examination Timetable) Struts
 * page. The pixel time-grid rendered by that page is driven either from the
 * in-memory examination solver (an uncommitted, transient solution) or, when no
 * matching solver is loaded, from the persisted committed exam assignment
 * ({@code Exam.getAssignedPeriod()} / {@code Exam.getAssignedRooms()}). This
 * migration surfaces only the persisted assignment as a period-by-room table:
 * one row per (exam, assigned room) cell, grouped/sorted by examination period.
 * The request carries an exam type filter (and optionally an academic session);
 * the response returns the applicable exam types (to drive a selector) together
 * with the string rows. Reuses {@link SimpleListInterface.Row} for the tabular
 * rows. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class ExamTimetableGridInterface implements IsSerializable {

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

	public static class ExamTimetableGridRequest implements GwtRpcRequest<ExamTimetableGridResponse> {
		private Long iExamTypeId;
		private Long iSessionId;

		public ExamTimetableGridRequest() {}

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public Long getSessionId() { return iSessionId; }
		public void setSessionId(Long sessionId) { iSessionId = sessionId; }

		@Override
		public String toString() { return "ExamTimetableGrid[type=" + iExamTypeId + ", session=" + iSessionId + "]"; }
	}

	public static class ExamTimetableGridResponse implements GwtRpcResponse {
		private String iTitle;
		private Long iExamTypeId;
		private List<ExamTypeInfo> iExamTypes = new ArrayList<ExamTypeInfo>();
		private List<String> iColumns = new ArrayList<String>();
		private List<Row> iRows = new ArrayList<Row>();

		public ExamTimetableGridResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public List<ExamTypeInfo> getExamTypes() { return iExamTypes; }
		public void addExamType(ExamTypeInfo type) { iExamTypes.add(type); }

		public List<String> getColumns() { return iColumns; }
		public void addColumn(String column) { iColumns.add(column); }

		public List<Row> getRows() { return iRows; }
		public Row addRow(Long id) {
			Row row = new Row();
			row.setId(id);
			iRows.add(row);
			return row;
		}
	}
}
