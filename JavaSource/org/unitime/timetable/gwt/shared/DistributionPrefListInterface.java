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
 * Read-only listing for the legacy distributionPrefs.action (Distribution
 * Preferences) Struts page. The request carries an optional subject-area filter;
 * the response returns the subject areas visible to the current user (to drive a
 * selector) together with the distribution preferences of the current user's
 * departments (and their instructor preferences) projected to string rows.
 * Reuses {@link SimpleListInterface.Row} for the tabular rows. Additive:
 * introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class DistributionPrefListInterface implements IsSerializable {

	public static class SubjectAreaInfo implements IsSerializable {
		private Long iId;
		private String iLabel;

		public SubjectAreaInfo() {}
		public SubjectAreaInfo(Long id, String label) { iId = id; iLabel = label; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	public static class DistributionPrefListRequest implements GwtRpcRequest<DistributionPrefListResponse> {
		private Long iSubjectAreaId;

		public DistributionPrefListRequest() {}

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }

		@Override
		public String toString() { return "DistributionPrefList[subjectArea=" + iSubjectAreaId + "]"; }
	}

	public static class DistributionPrefListResponse implements GwtRpcResponse {
		private String iTitle;
		private Long iSubjectAreaId;
		private List<SubjectAreaInfo> iSubjectAreas = new ArrayList<SubjectAreaInfo>();
		private List<String> iColumns = new ArrayList<String>();
		private List<Row> iRows = new ArrayList<Row>();

		public DistributionPrefListResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }

		public List<SubjectAreaInfo> getSubjectAreas() { return iSubjectAreas; }
		public void addSubjectArea(SubjectAreaInfo area) { iSubjectAreas.add(area); }

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
