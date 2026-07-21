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
 * Read-only report for the legacy roomAvailability.action (Examination Room
 * Availability) Struts page. Given an examination type (and optional room-name
 * filter / display-examinations / compare toggles) the response returns the
 * applicable examination types (to drive a selector) plus the report rows for
 * the selected type, projected to string cells. Two report shapes are produced,
 * mirroring the legacy action:
 * <ul>
 *   <li><b>availability</b> (compare = false): the external room-availability
 *       time blocks that overlap the examination periods — 8 columns.</li>
 *   <li><b>comparison</b> (compare = true): external time blocks matched against
 *       the committed examination assignments to surface mismatches — 9
 *       columns.</li>
 * </ul>
 * Reuses {@link SimpleListInterface.Row} for the tabular rows. The room
 * availability data comes from the configured {@code RoomAvailability} service;
 * when none is configured {@link ExamRoomAvailabilityResponse#isServiceAvailable()}
 * is {@code false} and no rows are returned (matching the legacy "nothing to
 * display" state). Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class ExamRoomAvailabilityInterface implements IsSerializable {

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

	public static class ExamRoomAvailabilityRequest implements GwtRpcRequest<ExamRoomAvailabilityResponse> {
		private Long iExamTypeId;
		private Long iSessionId;
		private String iFilter;
		private boolean iIncludeExams = false;
		private boolean iCompare = false;
		private boolean iRefresh = false;

		public ExamRoomAvailabilityRequest() {}

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public Long getSessionId() { return iSessionId; }
		public void setSessionId(Long sessionId) { iSessionId = sessionId; }

		public String getFilter() { return iFilter; }
		public void setFilter(String filter) { iFilter = filter; }

		public boolean isIncludeExams() { return iIncludeExams; }
		public void setIncludeExams(boolean includeExams) { iIncludeExams = includeExams; }

		public boolean isCompare() { return iCompare; }
		public void setCompare(boolean compare) { iCompare = compare; }

		public boolean isRefresh() { return iRefresh; }
		public void setRefresh(boolean refresh) { iRefresh = refresh; }

		@Override
		public String toString() {
			return "ExamRoomAvailability[type=" + iExamTypeId + ", compare=" + iCompare + ", filter=" + iFilter + "]";
		}
	}

	public static class ExamRoomAvailabilityResponse implements GwtRpcResponse {
		private String iTitle;
		private Long iExamTypeId;
		private boolean iCompare = false;
		private boolean iServiceAvailable = true;
		private String iTimestamp;
		private String iWarning;
		private List<ExamTypeInfo> iExamTypes = new ArrayList<ExamTypeInfo>();
		private List<String> iColumns = new ArrayList<String>();
		private List<Row> iRows = new ArrayList<Row>();

		public ExamRoomAvailabilityResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public boolean isCompare() { return iCompare; }
		public void setCompare(boolean compare) { iCompare = compare; }

		public boolean isServiceAvailable() { return iServiceAvailable; }
		public void setServiceAvailable(boolean serviceAvailable) { iServiceAvailable = serviceAvailable; }

		public String getTimestamp() { return iTimestamp; }
		public void setTimestamp(String timestamp) { iTimestamp = timestamp; }

		public String getWarning() { return iWarning; }
		public void setWarning(String warning) { iWarning = warning; }

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
