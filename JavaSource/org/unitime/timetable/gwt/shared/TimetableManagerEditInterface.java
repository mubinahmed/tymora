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
 * Create/Edit protocol for the legacy Struts Timetabling Manager list/edit page
 * (timetableManagerList.action). One request drives four operations:
 * <ul>
 *   <li>LIST  - enumerate all managers (id, external id, name, email) plus the
 *               caller's add permission and per-row edit/delete permission.</li>
 *   <li>LOAD  - fetch a single manager's editable core fields, its current roles
 *               (with primary flag) and current-session department assignment,
 *               plus the assignable roles and current-session departments.</li>
 *   <li>SAVE  - upsert the core fields (externalUniqueId, first/middle/last name,
 *               email) AND the manager's roles (add/remove ManagerRole, set exactly
 *               one primary) and current-session departments (add/remove) via merge.
 *               Settings and solver-group assignments are DEFERRED (never touched
 *               here); departments of OTHER sessions are never touched.</li>
 *   <li>DELETE- remove a manager, detaching department / solver-group joins first
 *               so the many-to-many links do not block the delete.</li>
 * </ul>
 * Additive: introduces no changes to existing behavior. Gated by
 * Right.TimetableManagers / TimetableManagerAdd / TimetableManagerEdit /
 * TimetableManagerDelete.
 *
 * @author Angular migration
 */
public class TimetableManagerEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LIST, LOAD, SAVE, DELETE
	}

	public static class TimetableManagerEditRequest implements GwtRpcRequest<TimetableManagerEditResponse> {
		private Operation iOperation;
		private Long iUniqueId;
		private String iExternalUniqueId;
		private String iFirstName;
		private String iMiddleName;
		private String iLastName;
		private String iAcademicTitle;
		private String iEmailAddress;
		private List<Long> iRoleIds = new ArrayList<Long>();
		private Long iPrimaryRoleId;
		private List<Long> iDepartmentIds = new ArrayList<Long>();

		public TimetableManagerEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getExternalUniqueId() { return iExternalUniqueId; }
		public void setExternalUniqueId(String externalUniqueId) { iExternalUniqueId = externalUniqueId; }

		public String getFirstName() { return iFirstName; }
		public void setFirstName(String firstName) { iFirstName = firstName; }

		public String getMiddleName() { return iMiddleName; }
		public void setMiddleName(String middleName) { iMiddleName = middleName; }

		public String getLastName() { return iLastName; }
		public void setLastName(String lastName) { iLastName = lastName; }

		public String getAcademicTitle() { return iAcademicTitle; }
		public void setAcademicTitle(String academicTitle) { iAcademicTitle = academicTitle; }

		public String getEmailAddress() { return iEmailAddress; }
		public void setEmailAddress(String emailAddress) { iEmailAddress = emailAddress; }

		public List<Long> getRoleIds() { return iRoleIds; }
		public void setRoleIds(List<Long> roleIds) { iRoleIds = roleIds; }
		public void addRoleId(Long roleId) { if (iRoleIds == null) iRoleIds = new ArrayList<Long>(); iRoleIds.add(roleId); }

		public Long getPrimaryRoleId() { return iPrimaryRoleId; }
		public void setPrimaryRoleId(Long primaryRoleId) { iPrimaryRoleId = primaryRoleId; }

		public List<Long> getDepartmentIds() { return iDepartmentIds; }
		public void setDepartmentIds(List<Long> departmentIds) { iDepartmentIds = departmentIds; }
		public void addDepartmentId(Long departmentId) { if (iDepartmentIds == null) iDepartmentIds = new ArrayList<Long>(); iDepartmentIds.add(departmentId); }

		@Override
		public String toString() { return "TimetableManagerEdit[" + iOperation + ", " + iUniqueId + "]"; }
	}

	public static class TimetableManagerEditResponse implements GwtRpcResponse {
		// Single-entity payload (LOAD / SAVE)
		private Long iUniqueId;
		private String iExternalUniqueId;
		private String iFirstName;
		private String iMiddleName;
		private String iLastName;
		private String iAcademicTitle;
		private String iEmailAddress;

		// Roles + departments payload (LOAD / SAVE)
		private List<Long> iRoleIds = new ArrayList<Long>();
		private Long iPrimaryRoleId;
		private List<Long> iDepartmentIds = new ArrayList<Long>();
		private List<IdName> iAvailableRoles = new ArrayList<IdName>();
		private List<IdName> iAvailableDepartments = new ArrayList<IdName>();

		// Listing payload (LIST)
		private List<ManagerLine> iManagers = new ArrayList<ManagerLine>();
		private boolean iCanAdd = false;

		public TimetableManagerEditResponse() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getExternalUniqueId() { return iExternalUniqueId; }
		public void setExternalUniqueId(String externalUniqueId) { iExternalUniqueId = externalUniqueId; }

		public String getFirstName() { return iFirstName; }
		public void setFirstName(String firstName) { iFirstName = firstName; }

		public String getMiddleName() { return iMiddleName; }
		public void setMiddleName(String middleName) { iMiddleName = middleName; }

		public String getLastName() { return iLastName; }
		public void setLastName(String lastName) { iLastName = lastName; }

		public String getAcademicTitle() { return iAcademicTitle; }
		public void setAcademicTitle(String academicTitle) { iAcademicTitle = academicTitle; }

		public String getEmailAddress() { return iEmailAddress; }
		public void setEmailAddress(String emailAddress) { iEmailAddress = emailAddress; }

		public List<Long> getRoleIds() { return iRoleIds; }
		public void setRoleIds(List<Long> roleIds) { iRoleIds = roleIds; }
		public void addRoleId(Long roleId) { if (iRoleIds == null) iRoleIds = new ArrayList<Long>(); iRoleIds.add(roleId); }

		public Long getPrimaryRoleId() { return iPrimaryRoleId; }
		public void setPrimaryRoleId(Long primaryRoleId) { iPrimaryRoleId = primaryRoleId; }

		public List<Long> getDepartmentIds() { return iDepartmentIds; }
		public void setDepartmentIds(List<Long> departmentIds) { iDepartmentIds = departmentIds; }
		public void addDepartmentId(Long departmentId) { if (iDepartmentIds == null) iDepartmentIds = new ArrayList<Long>(); iDepartmentIds.add(departmentId); }

		public List<IdName> getAvailableRoles() { return iAvailableRoles; }
		public void addAvailableRole(IdName role) { iAvailableRoles.add(role); }

		public List<IdName> getAvailableDepartments() { return iAvailableDepartments; }
		public void addAvailableDepartment(IdName dept) { iAvailableDepartments.add(dept); }

		public List<ManagerLine> getManagers() { return iManagers; }
		public void addManager(ManagerLine line) { iManagers.add(line); }

		public boolean isCanAdd() { return iCanAdd; }
		public void setCanAdd(boolean canAdd) { iCanAdd = canAdd; }
	}

	/** Simple id + display label option (assignable role / department). */
	public static class IdName implements IsSerializable {
		private Long iId;
		private String iName;

		public IdName() {}
		public IdName(Long id, String name) { iId = id; iName = name; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
	}

	public static class ManagerLine implements IsSerializable {
		private Long iUniqueId;
		private String iExternalUniqueId;
		private String iName;
		private String iEmail;
		private boolean iCanEdit = false;
		private boolean iCanDelete = false;

		public ManagerLine() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getExternalUniqueId() { return iExternalUniqueId; }
		public void setExternalUniqueId(String externalUniqueId) { iExternalUniqueId = externalUniqueId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getEmail() { return iEmail; }
		public void setEmail(String email) { iEmail = email; }

		public boolean isCanEdit() { return iCanEdit; }
		public void setCanEdit(boolean canEdit) { iCanEdit = canEdit; }

		public boolean isCanDelete() { return iCanDelete; }
		public void setCanDelete(boolean canDelete) { iCanDelete = canDelete; }
	}
}
