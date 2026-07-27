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
 * Protocol for the Relief Planning rules-configuration screen: the per-session
 * weekly relief cap, continuity/non-teaching toggles and the set of exempt staff.
 * Requires {@code ReliefPlanning} to view and {@code ReliefPlanningEdit} to save.
 * Additive — introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
public class ReliefConfigInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SAVE
	}

	public static class StaffOption implements IsSerializable {
		private String iUid;
		private String iName;
		public StaffOption() {}
		public StaffOption(String uid, String name) { iUid = uid; iName = name; }
		public String getUid() { return iUid; }
		public void setUid(String uid) { iUid = uid; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
	}

	public static class ReliefConfigRequest implements GwtRpcRequest<ReliefConfigResponse> {
		private Operation iOperation = Operation.LOAD;
		private Integer iWeeklyCap;
		private boolean iPreferContinuity;
		private boolean iExcludeNonTeaching;
		private boolean iSameDeptFirst;
		private List<String> iExemptUids = new ArrayList<String>();

		public ReliefConfigRequest() {}
		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public Integer getWeeklyCap() { return iWeeklyCap; }
		public void setWeeklyCap(Integer weeklyCap) { iWeeklyCap = weeklyCap; }
		public boolean isPreferContinuity() { return iPreferContinuity; }
		public void setPreferContinuity(boolean preferContinuity) { iPreferContinuity = preferContinuity; }
		public boolean isExcludeNonTeaching() { return iExcludeNonTeaching; }
		public void setExcludeNonTeaching(boolean excludeNonTeaching) { iExcludeNonTeaching = excludeNonTeaching; }
		public boolean isSameDeptFirst() { return iSameDeptFirst; }
		public void setSameDeptFirst(boolean sameDeptFirst) { iSameDeptFirst = sameDeptFirst; }
		public List<String> getExemptUids() { return iExemptUids; }
		public void setExemptUids(List<String> exemptUids) { iExemptUids = exemptUids; }

		@Override
		public String toString() { return "ReliefConfig[" + iOperation + "]"; }
	}

	public static class ReliefConfigResponse implements GwtRpcResponse {
		private Integer iWeeklyCap;
		private boolean iPreferContinuity;
		private boolean iExcludeNonTeaching;
		private boolean iSameDeptFirst;
		private List<String> iExemptUids = new ArrayList<String>();
		private List<StaffOption> iStaff = new ArrayList<StaffOption>();
		private boolean iCanManage;

		public ReliefConfigResponse() {}
		public Integer getWeeklyCap() { return iWeeklyCap; }
		public void setWeeklyCap(Integer weeklyCap) { iWeeklyCap = weeklyCap; }
		public boolean isPreferContinuity() { return iPreferContinuity; }
		public void setPreferContinuity(boolean preferContinuity) { iPreferContinuity = preferContinuity; }
		public boolean isExcludeNonTeaching() { return iExcludeNonTeaching; }
		public void setExcludeNonTeaching(boolean excludeNonTeaching) { iExcludeNonTeaching = excludeNonTeaching; }
		public boolean isSameDeptFirst() { return iSameDeptFirst; }
		public void setSameDeptFirst(boolean sameDeptFirst) { iSameDeptFirst = sameDeptFirst; }
		public List<String> getExemptUids() { return iExemptUids; }
		public void addExemptUid(String uid) { iExemptUids.add(uid); }
		public List<StaffOption> getStaff() { return iStaff; }
		public void addStaff(StaffOption s) { iStaff.add(s); }
		public boolean isCanManage() { return iCanManage; }
		public void setCanManage(boolean canManage) { iCanManage = canManage; }
	}
}
