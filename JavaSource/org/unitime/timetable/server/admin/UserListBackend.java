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
package org.unitime.timetable.server.admin;

import java.util.List;

import org.unitime.timetable.api.ApiToken;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserInfo;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserListRequest;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserListResponse;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.User;
import org.unitime.timetable.model.dao.UserDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.spring.SpringApplicationContextHolder;

/**
 * List backend for Users / Database Authentication (legacy userEdit.action list). Loads every
 * {@link User} with the read-only timetable-manager name and, when API tokens are enabled, the
 * user's token. Gated by {@link Right#Users}. Additive.
 *
 * @author Angular migration
 */
@GwtRpcImplements(UserListRequest.class)
public class UserListBackend implements GwtRpcImplementation<UserListRequest, UserListResponse> {

	@Override
	public UserListResponse execute(UserListRequest request, SessionContext context) {
		context.checkPermission(Right.Users);
		UserListResponse response = new UserListResponse();
		fill(response);
		return response;
	}

	static ApiToken apiToken() {
		return (ApiToken) SpringApplicationContextHolder.getBean("apiToken");
	}

	@SuppressWarnings("unchecked")
	static void fill(UserListResponse response) {
		boolean showTokens = ApplicationProperty.ApiCanUseAPIToken.isTrue();
		response.setShowTokens(showTokens);
		List<User> users = UserDAO.getInstance().findAll();
		for (User user : users) {
			UserInfo info = new UserInfo();
			info.setExternalId(user.getExternalUniqueId());
			info.setName(user.getUsername());
			TimetableManager mgr = TimetableManager.findByExternalId(user.getExternalUniqueId());
			info.setManagerName(mgr == null ? "" : mgr.getName());
			if (showTokens)
				info.setToken(apiToken().getToken(user.getExternalUniqueId(), user.getPassword()));
			response.addUser(info);
		}
	}
}
