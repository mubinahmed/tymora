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
 * Read-only DTO for the Application Configuration listing (Angular migration of
 * the legacy applicationConfig.action). Carries the persisted ApplicationConfig
 * rows (key / value / description). Additive: introduces no changes to existing
 * behavior and is not used by any legacy code path.
 *
 * @author Angular migration
 */
public class ApplicationConfigListInterface implements IsSerializable {

	public static class ApplicationConfigListRequest implements GwtRpcRequest<ApplicationConfigListResponse> {
		public ApplicationConfigListRequest() {}

		@Override
		public String toString() { return "ApplicationConfigList[]"; }
	}

	public static class ApplicationConfigListResponse implements GwtRpcResponse {
		private List<ApplicationConfigItem> iItems = new ArrayList<ApplicationConfigItem>();

		public ApplicationConfigListResponse() {}

		public List<ApplicationConfigItem> getItems() { return iItems; }
		public void addItem(ApplicationConfigItem item) { iItems.add(item); }
	}

	public static class ApplicationConfigItem implements IsSerializable {
		private String iKey;
		private String iValue;
		private String iDescription;

		public ApplicationConfigItem() {}

		public String getKey() { return iKey; }
		public void setKey(String key) { iKey = key; }

		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }

		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
	}
}
