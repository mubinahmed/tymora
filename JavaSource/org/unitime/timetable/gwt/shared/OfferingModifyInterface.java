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
 * Modify Instructional Offering (legacy instructionalOfferingModify.action) migrated
 * to GwtRpc command beans, scoped to the SAFE configuration-level edits:
 * <ul>
 *   <li>{@code OfferingModifyRequest} — read the offering's configurations and their
 *       subpart structure.</li>
 *   <li>{@code OfferingModifyUpdateRequest} — save each configuration's name and limit
 *       (direct scalar setters, no structural side effects).</li>
 * </ul>
 * Adding/removing configurations, subparts and classes (the full class-setup surgery)
 * stays on the existing GWT config editors ({@code InstrOfferingConfig}/{@code ClassSetup}).
 * Additive.
 *
 * @author Angular migration
 */
public class OfferingModifyInterface implements IsSerializable {

	public static class OfferingModifyRequest implements GwtRpcRequest<OfferingModifyResponse> {
		private Long iOfferingId;

		public OfferingModifyRequest() {}
		public OfferingModifyRequest(Long offeringId) { iOfferingId = offeringId; }

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		@Override
		public String toString() { return "OfferingModify[" + iOfferingId + "]"; }
	}

	public static class OfferingModifyUpdateRequest implements GwtRpcRequest<OfferingModifyResponse> {
		private Long iOfferingId;
		private List<ConfigEdit> iConfigs = new ArrayList<ConfigEdit>();

		public OfferingModifyUpdateRequest() {}

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public List<ConfigEdit> getConfigs() { return iConfigs; }
		public void addConfig(ConfigEdit config) { iConfigs.add(config); }

		@Override
		public String toString() { return "OfferingModifyUpdate[" + iOfferingId + "]"; }
	}

	public static class ConfigEdit implements IsSerializable {
		private Long iConfigId;
		private String iName;
		private Integer iLimit;

		public ConfigEdit() {}

		public Long getConfigId() { return iConfigId; }
		public void setConfigId(Long configId) { iConfigId = configId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public Integer getLimit() { return iLimit; }
		public void setLimit(Integer limit) { iLimit = limit; }
	}

	public static class OfferingModifyResponse implements GwtRpcResponse {
		private Long iOfferingId;
		private String iOfferingName;
		private boolean iSaved;
		private List<ConfigInfo> iConfigs = new ArrayList<ConfigInfo>();

		public OfferingModifyResponse() {}

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public String getOfferingName() { return iOfferingName; }
		public void setOfferingName(String offeringName) { iOfferingName = offeringName; }

		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }

		public List<ConfigInfo> getConfigs() { return iConfigs; }
		public void addConfig(ConfigInfo config) { iConfigs.add(config); }
	}

	public static class ConfigInfo implements IsSerializable {
		private Long iConfigId;
		private String iName;
		private Integer iLimit;
		private boolean iUnlimited;
		private List<String> iSubparts = new ArrayList<String>();

		public ConfigInfo() {}

		public Long getConfigId() { return iConfigId; }
		public void setConfigId(Long configId) { iConfigId = configId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public Integer getLimit() { return iLimit; }
		public void setLimit(Integer limit) { iLimit = limit; }

		public boolean isUnlimited() { return iUnlimited; }
		public void setUnlimited(boolean unlimited) { iUnlimited = unlimited; }

		public List<String> getSubparts() { return iSubparts; }
		public void addSubpart(String subpart) { iSubparts.add(subpart); }
	}
}
