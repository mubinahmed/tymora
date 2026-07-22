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

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserDeleteRequest;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserListResponse;
import org.unitime.timetable.model.User;
import org.unitime.timetable.model.dao.UserDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Delete backend for a database-authentication user (legacy userEdit.action op=Delete User).
 * Gated by {@link Right#Users}. Returns the refreshed list. Additive.
 *
 * @author Angular migration
 */
@GwtRpcImplements(UserDeleteRequest.class)
public class UserDeleteBackend implements GwtRpcImplementation<UserDeleteRequest, UserListResponse> {

	@Override
	public UserListResponse execute(UserDeleteRequest request, SessionContext context) {
		context.checkPermission(Right.Users);
		String externalId = request.getExternalId() == null ? null : request.getExternalId().trim();
		if (externalId == null || externalId.isEmpty())
			throw new GwtRpcException("No user was specified.");

		org.hibernate.Session hibSession = UserDAO.getInstance().getSession();
		Transaction tx = hibSession.beginTransaction();
		try {
			User u = User.findByExternalId(externalId);
			if (u != null) hibSession.remove(u);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to delete user: " + e.getMessage(), e);
		}

		UserListResponse response = new UserListResponse();
		UserListBackend.fill(response);
		return response;
	}
}
