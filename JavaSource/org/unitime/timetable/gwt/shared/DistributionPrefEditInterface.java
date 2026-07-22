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
 * Create / Edit protocol for the legacy distributionPrefs.action (Distribution
 * Preferences) Struts page. Sibling to the read-only DistributionPrefListInterface.
 * One request drives LOAD (return the list of the current user's distribution
 * preferences, plus, when an id is given, the editable detail of a single
 * preference together with the applicable distribution types and preference
 * levels), SAVE (update the distribution type and preference level of an existing
 * preference) and DELETE (remove an existing preference).
 *
 * Functional core: SAVE only changes the distribution TYPE and preference LEVEL
 * of an existing preference (merge-on-update, owners and distribution objects are
 * left untouched). Creating a new preference and editing the owners
 * (classes / subparts) remain deferred. Every operation is gated by
 * Right.DistributionPreferences (plus the per-preference DistributionPreferenceEdit /
 * DistributionPreferenceDelete rights). Additive: introduces no changes to
 * existing behavior.
 *
 * @author Angular migration
 */
public class DistributionPrefEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE,
		DELETE
	}

	/** One selectable subject area (drives the list filter). */
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

	/** A selectable distribution type; iAllowed carries the allowed preference-level chars. */
	public static class DistributionTypeInfo implements IsSerializable {
		private Long iId;
		private String iLabel;
		private String iAllowed;

		public DistributionTypeInfo() {}
		public DistributionTypeInfo(Long id, String label, String allowed) { iId = id; iLabel = label; iAllowed = allowed; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }

		public String getAllowed() { return iAllowed; }
		public void setAllowed(String allowed) { iAllowed = allowed; }
	}

	/** A selectable preference level; iChar is the level's single-character prolog code. */
	public static class PrefLevelInfo implements IsSerializable {
		private int iId;
		private String iName;
		private String iChar;

		public PrefLevelInfo() {}
		public PrefLevelInfo(int id, String name, String ch) { iId = id; iName = name; iChar = ch; }

		public int getId() { return iId; }
		public void setId(int id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getChar() { return iChar; }
		public void setChar(String ch) { iChar = ch; }
	}

	/** The editable detail of a single distribution preference. */
	public static class DistributionPrefRecord implements IsSerializable {
		private Long iId;
		private Long iDistributionTypeId;
		private Integer iPrefLevelId;
		private String iTypeLabel;
		private String iOwnerLabel;
		private String iAppliesTo;

		public DistributionPrefRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public Long getDistributionTypeId() { return iDistributionTypeId; }
		public void setDistributionTypeId(Long id) { iDistributionTypeId = id; }

		public Integer getPrefLevelId() { return iPrefLevelId; }
		public void setPrefLevelId(Integer id) { iPrefLevelId = id; }

		public String getTypeLabel() { return iTypeLabel; }
		public void setTypeLabel(String label) { iTypeLabel = label; }

		public String getOwnerLabel() { return iOwnerLabel; }
		public void setOwnerLabel(String label) { iOwnerLabel = label; }

		public String getAppliesTo() { return iAppliesTo; }
		public void setAppliesTo(String appliesTo) { iAppliesTo = appliesTo; }
	}

	public static class DistributionPrefEditRequest implements GwtRpcRequest<DistributionPrefEditResponse> {
		private Operation iOperation = Operation.LOAD;
		private Long iSubjectAreaId;
		private Long iId;
		private DistributionPrefRecord iRecord;

		public DistributionPrefEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public DistributionPrefRecord getRecord() { return iRecord; }
		public void setRecord(DistributionPrefRecord record) { iRecord = record; }

		@Override
		public String toString() {
			return "DistributionPrefEdit[" + iOperation + (iId == null ? "" : ",id=" + iId) + "]";
		}
	}

	public static class DistributionPrefEditResponse implements GwtRpcResponse {
		private String iTitle;
		private boolean iEditable = false;
		private boolean iDeletable = false;
		private Long iSubjectAreaId;
		private List<SubjectAreaInfo> iSubjectAreas = new ArrayList<SubjectAreaInfo>();
		private List<String> iColumns = new ArrayList<String>();
		private List<Row> iRows = new ArrayList<Row>();
		private DistributionPrefRecord iRecord;
		private List<DistributionTypeInfo> iDistributionTypes = new ArrayList<DistributionTypeInfo>();
		private List<PrefLevelInfo> iPrefLevels = new ArrayList<PrefLevelInfo>();

		public DistributionPrefEditResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public boolean isDeletable() { return iDeletable; }
		public void setDeletable(boolean deletable) { iDeletable = deletable; }

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

		public DistributionPrefRecord getRecord() { return iRecord; }
		public void setRecord(DistributionPrefRecord record) { iRecord = record; }

		public List<DistributionTypeInfo> getDistributionTypes() { return iDistributionTypes; }
		public void addDistributionType(DistributionTypeInfo type) { iDistributionTypes.add(type); }

		public List<PrefLevelInfo> getPrefLevels() { return iPrefLevels; }
		public void addPrefLevel(PrefLevelInfo level) { iPrefLevels.add(level); }
	}
}
