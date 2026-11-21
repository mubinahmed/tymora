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
 * Application configuration editor (Angular migration of applicationConfig.action).
 * LOAD lists the global {@code ApplicationConfig} rows plus the {@code SessionConfig}
 * rows of the current academic session (session rows override global ones with the
 * same key). SAVE changes the value / description of an existing key, CREATE adds a
 * new key (global or current-session, like the legacy "Add Setting"), DELETE removes
 * a key (legacy "Delete").
 *
 * @author Angular migration
 */
public class ApplicationConfigEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable { LOAD, SAVE, CREATE, DELETE }

	public static class ApplicationConfigEditRequest implements GwtRpcRequest<ApplicationConfigEditResponse> {
		private Operation iOperation;
		private String iKey;
		private String iValue;
		private String iDescription;
		private Boolean iAllSessions;

		public ApplicationConfigEditRequest() {}

		/** CREATE / SAVE: true (default) = global ApplicationConfig, false = SessionConfig of the current session. */
		public Boolean getAllSessions() { return iAllSessions; }
		public void setAllSessions(Boolean allSessions) { iAllSessions = allSessions; }

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public String getKey() { return iKey; }
		public void setKey(String key) { iKey = key; }
		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }

		@Override
		public String toString() { return "ApplicationConfigEdit[" + iOperation + "," + iKey + "]"; }
	}

	public static class ApplicationConfigEditResponse implements GwtRpcResponse {
		private boolean iEditable;
		private List<ApplicationConfigItem> iItems = new ArrayList<ApplicationConfigItem>();

		public ApplicationConfigEditResponse() {}

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public List<ApplicationConfigItem> getItems() { return iItems; }
		public void addItem(ApplicationConfigItem item) { iItems.add(item); }
	}

	public static class ApplicationConfigItem implements IsSerializable {
		private String iKey;
		private String iValue;
		private String iDescription;
		private boolean iAllSessions = true;

		public ApplicationConfigItem() {}

		/** true = global ApplicationConfig row, false = SessionConfig of the current academic session. */
		public boolean isAllSessions() { return iAllSessions; }
		public void setAllSessions(boolean allSessions) { iAllSessions = allSessions; }

		public String getKey() { return iKey; }
		public void setKey(String key) { iKey = key; }
		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }
		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
	}
}
