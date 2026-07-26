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
package org.unitime.timetable.server;

import org.unitime.timetable.defaults.SessionAttribute;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SelectPrimaryRoleInterface.AuthorityInfo;
import org.unitime.timetable.gwt.shared.SelectPrimaryRoleInterface.Operation;
import org.unitime.timetable.gwt.shared.SelectPrimaryRoleInterface.SelectPrimaryRoleRequest;
import org.unitime.timetable.gwt.shared.SelectPrimaryRoleInterface.SelectPrimaryRoleResponse;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.UserAuthority;
import org.unitime.timetable.security.UserContext;

/**
 * Backing bean for the legacy selectPrimaryRole.action ("Change Role") page.
 * {@code LOAD} lists the signed-in user's authorities (role × academic session);
 * {@code SELECT} makes the chosen one current — a faithful port of
 * {@code RoleListAction}: it calls {@code user.setCurrentAuthority(...)} and clears
 * the carried-over {@link SessionAttribute}s. Runs on the request thread, so the
 * mutated session principal persists exactly as the legacy action. No role gate
 * (any authenticated user may pick their role); additive. Introduces no changes to
 * existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SelectPrimaryRoleRequest.class)
public class SelectPrimaryRoleBackend implements GwtRpcImplementation<SelectPrimaryRoleRequest, SelectPrimaryRoleResponse> {

	@Override
	public SelectPrimaryRoleResponse execute(SelectPrimaryRoleRequest request, SessionContext context) {
		UserContext user = context.getUser();
		if (user == null)
			throw new GwtRpcException("Not signed in.");

		if (request.getOperation() == Operation.SELECT && request.getAuthorityId() != null
				&& !request.getAuthorityId().isEmpty()) {
			UserAuthority authority = user.getAuthority(request.getAuthorityId());
			if (authority == null)
				throw new GwtRpcException("The selected role is not available for this user.");
			user.setCurrentAuthority(authority);
			// Drop per-identity session state carried over from the previous role.
			for (SessionAttribute a : SessionAttribute.values())
				context.removeAttribute(a);
		}

		SelectPrimaryRoleResponse response = new SelectPrimaryRoleResponse();
		response.setName(user.getName() == null ? user.getUsername() : user.getName());
		UserAuthority current = user.getCurrentAuthority();
		response.setCurrentId(current == null ? null : current.getAuthority());

		for (UserAuthority authority : user.getAuthorities()) {
			if (authority.getAcademicSession() == null) continue;
			Session session = SessionDAO.getInstance().get((Long) authority.getAcademicSession().getQualifierId());
			if (session == null) continue;

			AuthorityInfo info = new AuthorityInfo();
			info.setId(authority.getAuthority());
			info.setRole(authority.getLabel());
			info.setSession(session.getAcademicYear() + " " + session.getAcademicTerm());
			info.setInitiative(session.getAcademicInitiative());
			info.setStatus(session.getStatusType() == null ? "" : session.getStatusType().getLabel());
			info.setCurrent(authority.equals(current));
			response.addAuthority(info);
		}

		return response;
	}
}
