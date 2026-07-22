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
import org.unitime.timetable.form.UserEditForm;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserListResponse;
import org.unitime.timetable.gwt.shared.UserEditInterface.UserSaveRequest;
import org.unitime.timetable.model.User;
import org.unitime.timetable.model.dao.UserDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for adding/updating a database-authentication user (legacy userEdit.action
 * op=Save User / Update User). Mirrors {@code UserEditForm.validate} + {@code saveOrUpdate}:
 * validates uniqueness of external id (add) and user name, requires a password on add, and on
 * update either just re-encodes the password (name unchanged) or recreates the row keeping the
 * external id (name changed). Passwords are MD5-encoded via {@link UserEditForm#encodePassword}.
 * Empty password on update keeps the existing one. Gated by {@link Right#Users}, transactional.
 *
 * @author Angular migration
 */
@GwtRpcImplements(UserSaveRequest.class)
public class UserSaveBackend implements GwtRpcImplementation<UserSaveRequest, UserListResponse> {

	@Override
	public UserListResponse execute(UserSaveRequest request, SessionContext context) {
		context.checkPermission(Right.Users);

		String externalId = request.getExternalId() == null ? null : request.getExternalId().trim();
		String name = request.getName() == null ? null : request.getName().trim();
		String password = request.getPassword();
		boolean newUser = request.isNewUser();

		// Validation (mirrors UserEditForm.validate)
		if (externalId == null || externalId.isEmpty())
			throw new GwtRpcException("External Id is required.");
		if (newUser && User.findByExternalId(externalId) != null)
			throw new GwtRpcException("A user with external id " + externalId + " already exists.");
		if (name == null || name.isEmpty())
			throw new GwtRpcException("User Name is required.");
		User byName = User.findByUserName(name);
		if (byName != null && !byName.getExternalUniqueId().equals(externalId))
			throw new GwtRpcException("A user with user name " + name + " already exists.");
		if (newUser && (password == null || password.trim().isEmpty()))
			throw new GwtRpcException("Password is required.");

		org.hibernate.Session hibSession = UserDAO.getInstance().getSession();
		Transaction tx = hibSession.beginTransaction();
		try {
			if (newUser) {
				User u = new User();
				u.setExternalUniqueId(externalId);
				u.setUsername(name);
				u.setPassword(UserEditForm.encodePassword(password));
				hibSession.persist(u);
			} else {
				User u = User.findByExternalId(externalId);
				if (u == null)
					throw new GwtRpcException("User " + externalId + " does not exist.");
				boolean changePwd = password != null && !password.isEmpty();
				if (u.getUsername().equals(name)) {
					if (changePwd)
						u.setPassword(UserEditForm.encodePassword(password));
					hibSession.merge(u);
				} else {
					// User name is (part of) the key: recreate the row keeping the external id.
					User w = new User();
					w.setExternalUniqueId(u.getExternalUniqueId());
					w.setUsername(name);
					w.setPassword(changePwd ? UserEditForm.encodePassword(password) : u.getPassword());
					hibSession.remove(u);
					hibSession.persist(w);
				}
			}
			tx.commit();
		} catch (GwtRpcException e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw e;
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save user: " + e.getMessage(), e);
		}

		UserListResponse response = new UserListResponse();
		UserListBackend.fill(response);
		return response;
	}
}
