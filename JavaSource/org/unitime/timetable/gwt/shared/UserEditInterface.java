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
 * Users / Database Authentication (legacy userEdit.action) migrated to GwtRpc command beans.
 * These edit the {@link org.unitime.timetable.model.User} rows used by database authentication:
 * external id (PUID), user name and MD5 password, with a read-only timetable-manager name and an
 * optional API token per user. {@code UserListRequest} loads the table; {@code UserEditLoadRequest}
 * loads one user for editing; {@code UserSaveRequest}/{@code UserDeleteRequest} mutate and return
 * the refreshed list. Additive — mirrors {@code UserEditAction}/{@code UserEditForm}.
 *
 * @author Angular migration
 */
public class UserEditInterface implements IsSerializable {

	public static class UserInfo implements IsSerializable {
		private String iExternalId;
		private String iName;
		private String iManagerName;
		private String iToken;

		public UserInfo() {}

		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getManagerName() { return iManagerName; }
		public void setManagerName(String managerName) { iManagerName = managerName; }
		public String getToken() { return iToken; }
		public void setToken(String token) { iToken = token; }
	}

	public static class UserListResponse implements GwtRpcResponse {
		private boolean iShowTokens = false;
		private List<UserInfo> iUsers = new ArrayList<UserInfo>();

		public UserListResponse() {}

		public boolean isShowTokens() { return iShowTokens; }
		public void setShowTokens(boolean showTokens) { iShowTokens = showTokens; }
		public List<UserInfo> getUsers() { return iUsers; }
		public void setUsers(List<UserInfo> users) { iUsers = users; }
		public void addUser(UserInfo user) { iUsers.add(user); }
	}

	public static class UserEditData implements GwtRpcResponse {
		private boolean iNewUser = false;
		private boolean iShowTokens = false;
		private String iExternalId;
		private String iName;
		private String iToken;

		public UserEditData() {}

		public boolean isNewUser() { return iNewUser; }
		public void setNewUser(boolean newUser) { iNewUser = newUser; }
		public boolean isShowTokens() { return iShowTokens; }
		public void setShowTokens(boolean showTokens) { iShowTokens = showTokens; }
		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getToken() { return iToken; }
		public void setToken(String token) { iToken = token; }
	}

	public static class UserListRequest implements GwtRpcRequest<UserListResponse> {
		public UserListRequest() {}
		@Override public String toString() { return "UserList[]"; }
	}

	/** Load one existing user for editing (by external id). Returns a blank add form if null. */
	public static class UserEditLoadRequest implements GwtRpcRequest<UserEditData> {
		private String iExternalId;
		public UserEditLoadRequest() {}
		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		@Override public String toString() { return "UserEditLoad[" + iExternalId + "]"; }
	}

	/** Add (newUser=true) or update (newUser=false) a user. Empty password on update = keep. */
	public static class UserSaveRequest implements GwtRpcRequest<UserListResponse> {
		private boolean iNewUser = false;
		private String iExternalId;
		private String iName;
		private String iPassword;

		public UserSaveRequest() {}

		public boolean isNewUser() { return iNewUser; }
		public void setNewUser(boolean newUser) { iNewUser = newUser; }
		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getPassword() { return iPassword; }
		public void setPassword(String password) { iPassword = password; }
		@Override public String toString() { return "UserSave[" + iExternalId + "]"; }
	}

	public static class UserDeleteRequest implements GwtRpcRequest<UserListResponse> {
		private String iExternalId;
		public UserDeleteRequest() {}
		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		@Override public String toString() { return "UserDelete[" + iExternalId + "]"; }
	}
}
