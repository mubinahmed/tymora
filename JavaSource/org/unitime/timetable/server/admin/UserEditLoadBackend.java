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

import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserEditData;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserEditLoadRequest;
import org.unitime.timetable.model.User;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Load backend for editing one user (legacy userEdit.action op=Edit / Add User). A null/blank
 * external id yields a blank add form; otherwise the user is loaded by external id. The password
 * is never returned to the client (blank on the form means "keep existing" on save). Gated by
 * {@link Right#Users}. Additive.
 *
 * @author Angular migration
 */
@GwtRpcImplements(UserEditLoadRequest.class)
public class UserEditLoadBackend implements GwtRpcImplementation<UserEditLoadRequest, UserEditData> {

	@Override
	public UserEditData execute(UserEditLoadRequest request, SessionContext context) {
		context.checkPermission(Right.Users);
		boolean showTokens = ApplicationProperty.ApiCanUseAPIToken.isTrue();
		UserEditData data = new UserEditData();
		data.setShowTokens(showTokens);

		String externalId = request.getExternalId();
		if (externalId == null || externalId.trim().isEmpty()) {
			data.setNewUser(true);
			return data;
		}

		User u = User.findByExternalId(externalId.trim());
		if (u == null)
			throw new GwtRpcException("User " + externalId + " does not exist.");

		data.setNewUser(false);
		data.setExternalId(u.getExternalUniqueId());
		data.setName(u.getUsername());
		if (showTokens)
			data.setToken(UserListBackend.apiToken().getToken(u.getExternalUniqueId(), u.getPassword()));
		return data;
	}
}
