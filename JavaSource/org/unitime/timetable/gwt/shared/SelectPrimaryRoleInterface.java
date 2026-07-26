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
 * Protocol for the legacy selectPrimaryRole.action ("Change Role" / role picker)
 * Struts page. {@code LOAD} returns the signed-in user's authorities (each is a
 * role × academic-session pairing) and which one is currently active; {@code SELECT}
 * makes the chosen authority current (mirroring {@code RoleListAction}'s
 * {@code user.setCurrentAuthority}) and returns the refreshed list. Requires only an
 * authenticated user — no role/permission gate, matching the legacy page's
 * {@code checkRole=false}. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class SelectPrimaryRoleInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SELECT
	}

	/** One selectable authority = a role bound to an academic session. */
	public static class AuthorityInfo implements IsSerializable {
		private String iId;
		private String iRole;
		private String iSession;
		private String iInitiative;
		private String iStatus;
		private boolean iCurrent;

		public AuthorityInfo() {}

		public String getId() { return iId; }
		public void setId(String id) { iId = id; }

		public String getRole() { return iRole; }
		public void setRole(String role) { iRole = role; }

		public String getSession() { return iSession; }
		public void setSession(String session) { iSession = session; }

		public String getInitiative() { return iInitiative; }
		public void setInitiative(String initiative) { iInitiative = initiative; }

		public String getStatus() { return iStatus; }
		public void setStatus(String status) { iStatus = status; }

		public boolean isCurrent() { return iCurrent; }
		public void setCurrent(boolean current) { iCurrent = current; }
	}

	public static class SelectPrimaryRoleRequest implements GwtRpcRequest<SelectPrimaryRoleResponse> {
		private Operation iOperation = Operation.LOAD;
		private String iAuthorityId;

		public SelectPrimaryRoleRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public String getAuthorityId() { return iAuthorityId; }
		public void setAuthorityId(String authorityId) { iAuthorityId = authorityId; }

		@Override
		public String toString() { return "SelectPrimaryRole[" + iOperation + (iAuthorityId == null ? "" : "," + iAuthorityId) + "]"; }
	}

	public static class SelectPrimaryRoleResponse implements GwtRpcResponse {
		private String iName;
		private String iCurrentId;
		private List<AuthorityInfo> iAuthorities = new ArrayList<AuthorityInfo>();

		public SelectPrimaryRoleResponse() {}

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getCurrentId() { return iCurrentId; }
		public void setCurrentId(String currentId) { iCurrentId = currentId; }

		public List<AuthorityInfo> getAuthorities() { return iAuthorities; }
		public void addAuthority(AuthorityInfo authority) { iAuthorities.add(authority); }
		public boolean hasAuthorities() { return !iAuthorities.isEmpty(); }
	}
}
