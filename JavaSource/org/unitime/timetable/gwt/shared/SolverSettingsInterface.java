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
 * Solver Configurations (legacy solverSettings.action) migrated to GwtRpc command beans,
 * scoped to list / edit / delete of existing {@code SolverPredefinedSetting}s:
 * {@code SolverSettingListRequest} lists them; {@code SolverSettingEditRequest} loads one with
 * its visible parameters (grouped by the appearance's solver type, each with value + use-default);
 * {@code SolverSettingUpdateRequest} saves name/description and the parameter overrides;
 * {@code SolverSettingDeleteRequest} deletes. Add-new and export stay on the legacy screen.
 * Additive.
 *
 * @author Angular migration
 */
public class SolverSettingsInterface implements IsSerializable {

	public static class SolverSettingListRequest implements GwtRpcRequest<SolverSettingListResponse> {
		public SolverSettingListRequest() {}
		@Override public String toString() { return "SolverSettingList[]"; }
	}

	public static class SolverSettingEditRequest implements GwtRpcRequest<SolverSettingEditResponse> {
		private Long iSettingId;
		public SolverSettingEditRequest() {}
		public Long getSettingId() { return iSettingId; }
		public void setSettingId(Long settingId) { iSettingId = settingId; }
		@Override public String toString() { return "SolverSettingEdit[" + iSettingId + "]"; }
	}

	public static class SolverSettingDeleteRequest implements GwtRpcRequest<SolverSettingListResponse> {
		private Long iSettingId;
		public SolverSettingDeleteRequest() {}
		public Long getSettingId() { return iSettingId; }
		public void setSettingId(Long settingId) { iSettingId = settingId; }
		@Override public String toString() { return "SolverSettingDelete[" + iSettingId + "]"; }
	}

	public static class SolverSettingUpdateRequest implements GwtRpcRequest<SolverSettingEditResponse> {
		private Long iSettingId;
		private String iName;
		private String iDescription;
		private List<ParamValue> iParams = new ArrayList<ParamValue>();

		public SolverSettingUpdateRequest() {}

		public Long getSettingId() { return iSettingId; }
		public void setSettingId(Long settingId) { iSettingId = settingId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
		public List<ParamValue> getParams() { return iParams; }

		@Override public String toString() { return "SolverSettingUpdate[" + iSettingId + "]"; }
	}

	public static class ParamValue implements IsSerializable {
		private Long iDefId;
		private String iValue;
		private boolean iUseDefault;
		public ParamValue() {}
		public Long getDefId() { return iDefId; }
		public void setDefId(Long defId) { iDefId = defId; }
		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }
		public boolean isUseDefault() { return iUseDefault; }
		public void setUseDefault(boolean useDefault) { iUseDefault = useDefault; }
	}

	public static class SolverSettingListResponse implements GwtRpcResponse {
		private List<SettingInfo> iSettings = new ArrayList<SettingInfo>();
		public SolverSettingListResponse() {}
		public List<SettingInfo> getSettings() { return iSettings; }
		public void addSetting(SettingInfo setting) { iSettings.add(setting); }
	}

	public static class SettingInfo implements IsSerializable {
		private Long iId;
		private String iName;
		private String iDescription;
		private String iAppearanceLabel;
		public SettingInfo() {}
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
		public String getAppearanceLabel() { return iAppearanceLabel; }
		public void setAppearanceLabel(String appearanceLabel) { iAppearanceLabel = appearanceLabel; }
	}

	public static class SolverSettingEditResponse implements GwtRpcResponse {
		private Long iId;
		private String iName;
		private String iDescription;
		private String iAppearanceLabel;
		private boolean iSaved;
		private List<GroupInfo> iGroups = new ArrayList<GroupInfo>();

		public SolverSettingEditResponse() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
		public String getAppearanceLabel() { return iAppearanceLabel; }
		public void setAppearanceLabel(String appearanceLabel) { iAppearanceLabel = appearanceLabel; }
		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }
		public List<GroupInfo> getGroups() { return iGroups; }
		public void addGroup(GroupInfo group) { iGroups.add(group); }
	}

	public static class GroupInfo implements IsSerializable {
		private String iName;
		private String iDescription;
		private List<ParamInfo> iParams = new ArrayList<ParamInfo>();
		public GroupInfo() {}
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
		public List<ParamInfo> getParams() { return iParams; }
		public void addParam(ParamInfo param) { iParams.add(param); }
	}

	public static class ParamInfo implements IsSerializable {
		private Long iDefId;
		private String iName;
		private String iDescription;
		private String iType;
		private String iDefaultValue;
		private String iValue;
		private boolean iUseDefault;
		public ParamInfo() {}
		public Long getDefId() { return iDefId; }
		public void setDefId(Long defId) { iDefId = defId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
		public String getType() { return iType; }
		public void setType(String type) { iType = type; }
		public String getDefaultValue() { return iDefaultValue; }
		public void setDefaultValue(String defaultValue) { iDefaultValue = defaultValue; }
		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }
		public boolean isUseDefault() { return iUseDefault; }
		public void setUseDefault(boolean useDefault) { iUseDefault = useDefault; }
	}
}
