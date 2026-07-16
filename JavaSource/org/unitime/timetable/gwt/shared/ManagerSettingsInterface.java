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
 * Personal (per signed-in user) settings protocol for the legacy Struts
 * managerSettings page. One request drives LOAD (return every defined
 * {@link org.unitime.timetable.model.Settings Settings} key together with the
 * current user's chosen value + the allowed value/label pairs) and SAVE (persist
 * one value against the signed-in user only, via
 * UserContext.setProperty(key, value)). The backend
 * (ManagerSettingsBackend) gates every operation with Right.SettingsUser and
 * returns the refreshed record list.
 *
 * @author Angular migration
 */
public class ManagerSettingsInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE
	}

	/** A single allowed value together with its display label. */
	public static class SettingOption implements IsSerializable {
		private String iValue;
		private String iLabel;

		public SettingOption() {}
		public SettingOption(String value, String label) { iValue = value; iLabel = label; }

		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/** One Settings key with the current user's value and the choices for it. */
	public static class SettingRecord implements IsSerializable {
		private Long iId;
		private String iKey;
		private String iName;
		private String iValue;
		private String iDefaultValue;
		private List<SettingOption> iOptions = new ArrayList<SettingOption>();

		public SettingRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getKey() { return iKey; }
		public void setKey(String key) { iKey = key; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }

		public String getDefaultValue() { return iDefaultValue; }
		public void setDefaultValue(String defaultValue) { iDefaultValue = defaultValue; }

		public List<SettingOption> getOptions() { return iOptions; }
		public void addOption(SettingOption option) { iOptions.add(option); }
	}

	public static class ManagerSettingsRequest implements GwtRpcRequest<ManagerSettingsResponse> {
		private Operation iOperation = Operation.LOAD;
		private String iKey;
		private String iValue;

		public ManagerSettingsRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public String getKey() { return iKey; }
		public void setKey(String key) { iKey = key; }

		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }

		@Override
		public String toString() {
			return "ManagerSettings[" + iOperation + (iKey == null ? "" : "," + iKey + "=" + iValue) + "]";
		}
	}

	public static class ManagerSettingsResponse implements GwtRpcResponse {
		private boolean iEditable = false;
		private List<SettingRecord> iRecords = new ArrayList<SettingRecord>();

		public ManagerSettingsResponse() {}

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public List<SettingRecord> getRecords() { return iRecords; }
		public void addRecord(SettingRecord record) { iRecords.add(record); }
	}
}
