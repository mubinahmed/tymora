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
 * Create/Edit protocol for {@link org.unitime.timetable.model.SolverGroup}s of the
 * current academic session (migration of the legacy solverGroupEdit.action Struts
 * page). One request with an operation enum drives the screen:
 * <ul>
 *   <li>LOAD   &mdash; return all solver groups of the current session (editable fields + flags),
 *              plus the pool of departments that may be assigned;</li>
 *   <li>SAVE   &mdash; create (uniqueId == null) or merge-update name/abbv and department
 *              membership of an existing group;</li>
 *   <li>DELETE &mdash; remove a group (only when it has no solutions).</li>
 * </ul>
 * Name, abbreviation and department membership are edited here. Timetable managers
 * and solutions are intentionally NOT managed by this bean (deferred); on SAVE-update
 * those relations are left untouched.
 *
 * @author Angular migration
 */
public class SolverGroupEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SAVE, DELETE
	}

	public static class SolverGroupEditRequest implements GwtRpcRequest<SolverGroupEditResponse> {
		private Operation iOperation;
		private Long iUniqueId;
		private String iName;
		private String iAbbv;
		private List<Long> iDepartmentIds = new ArrayList<Long>();

		public SolverGroupEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getAbbv() { return iAbbv; }
		public void setAbbv(String abbv) { iAbbv = abbv; }

		/** Department ids the user selected for this group (SAVE only). */
		public List<Long> getDepartmentIds() { return iDepartmentIds; }
		public void setDepartmentIds(List<Long> departmentIds) { iDepartmentIds = departmentIds; }

		@Override
		public String toString() {
			return "SolverGroupEdit[" + iOperation + "," + iUniqueId + "]";
		}
	}

	public static class SolverGroupEditResponse implements GwtRpcResponse {
		private boolean iCanAdd = false;
		private List<SolverGroupInfo> iGroups = new ArrayList<SolverGroupInfo>();
		private List<DepartmentInfo> iDepartments = new ArrayList<DepartmentInfo>();

		public SolverGroupEditResponse() {}

		public boolean isCanAdd() { return iCanAdd; }
		public void setCanAdd(boolean canAdd) { iCanAdd = canAdd; }

		public List<SolverGroupInfo> getGroups() { return iGroups; }
		public void addGroup(SolverGroupInfo group) { iGroups.add(group); }

		/** Pool of departments that may be assigned to a solver group in this session. */
		public List<DepartmentInfo> getDepartments() { return iDepartments; }
		public void addDepartment(DepartmentInfo department) { iDepartments.add(department); }
	}

	/**
	 * A department in the current session that can be a member of a solver group.
	 * {@link #getSolverGroupId()} is the id of the solver group it currently belongs
	 * to (null when unassigned); the client uses it to decide which departments are
	 * available for a given group (unassigned + the group's own).
	 */
	public static class DepartmentInfo implements IsSerializable {
		private Long iUniqueId;
		private String iLabel;
		private Long iSolverGroupId;

		public DepartmentInfo() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }

		public Long getSolverGroupId() { return iSolverGroupId; }
		public void setSolverGroupId(Long solverGroupId) { iSolverGroupId = solverGroupId; }
	}

	public static class SolverGroupInfo implements IsSerializable {
		private Long iUniqueId;
		private String iName;
		private String iAbbv;
		private String iDepartments;
		private List<Long> iDepartmentIds = new ArrayList<Long>();
		private boolean iDepartmentsEditable = true;
		private boolean iCommitted = false;
		private boolean iCanEdit = false;
		private boolean iCanDelete = false;

		public SolverGroupInfo() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getAbbv() { return iAbbv; }
		public void setAbbv(String abbv) { iAbbv = abbv; }

		public String getDepartments() { return iDepartments; }
		public void setDepartments(String departments) { iDepartments = departments; }

		/** Ids of the departments currently assigned to this group. */
		public List<Long> getDepartmentIds() { return iDepartmentIds; }
		public void addDepartmentId(Long departmentId) { iDepartmentIds.add(departmentId); }

		/**
		 * Whether department membership may be changed. The legacy screen forbids
		 * editing departments once the group has committed/other solutions.
		 */
		public boolean isDepartmentsEditable() { return iDepartmentsEditable; }
		public void setDepartmentsEditable(boolean departmentsEditable) { iDepartmentsEditable = departmentsEditable; }

		public boolean isCommitted() { return iCommitted; }
		public void setCommitted(boolean committed) { iCommitted = committed; }

		public boolean isCanEdit() { return iCanEdit; }
		public void setCanEdit(boolean canEdit) { iCanEdit = canEdit; }

		public boolean isCanDelete() { return iCanDelete; }
		public void setCanDelete(boolean canDelete) { iCanDelete = canDelete; }
	}
}
