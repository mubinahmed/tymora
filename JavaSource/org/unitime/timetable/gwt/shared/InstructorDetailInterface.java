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

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Read-only detail of a single DepartmentalInstructor, keyed by its uniqueId
 * (the route param on the Angular instructor-detail screen). Mirrors the subset
 * of the legacy Struts InstructorDetailAction that is safely projectable without
 * a solver proxy: identity, department, position, contact and note fields plus a
 * count of assigned classes. Backend: InstructorDetailBackend, gated by
 * Right.InstructorDetail. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class InstructorDetailInterface implements IsSerializable {

	public static class InstructorDetailRequest implements GwtRpcRequest<InstructorDetailResponse> {
		private Long iInstructorId;

		public InstructorDetailRequest() {}
		public InstructorDetailRequest(Long instructorId) { iInstructorId = instructorId; }

		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }

		@Override
		public String toString() { return "InstructorDetail[" + iInstructorId + "]"; }
	}

	public static class InstructorDetailResponse implements GwtRpcResponse {
		private Long iId;
		private String iName;
		private String iEmail;
		private String iExternalId;
		private String iAccountName;
		private String iPosition;
		private String iAcademicTitle;
		private String iDeptCode;
		private String iDeptName;
		private String iNote;
		private int iAssignedClasses;

		public InstructorDetailResponse() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getEmail() { return iEmail; }
		public void setEmail(String email) { iEmail = email; }

		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }

		public String getAccountName() { return iAccountName; }
		public void setAccountName(String accountName) { iAccountName = accountName; }

		public String getPosition() { return iPosition; }
		public void setPosition(String position) { iPosition = position; }

		public String getAcademicTitle() { return iAcademicTitle; }
		public void setAcademicTitle(String academicTitle) { iAcademicTitle = academicTitle; }

		public String getDeptCode() { return iDeptCode; }
		public void setDeptCode(String deptCode) { iDeptCode = deptCode; }

		public String getDeptName() { return iDeptName; }
		public void setDeptName(String deptName) { iDeptName = deptName; }

		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }

		public int getAssignedClasses() { return iAssignedClasses; }
		public void setAssignedClasses(int assignedClasses) { iAssignedClasses = assignedClasses; }
	}
}
