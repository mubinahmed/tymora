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
 * Instructor Add / Edit (legacy instructorAdd.action + instructorInfoEdit.action)
 * migrated to GwtRpc command beans — the instructor identity fields only
 * (name, external id, title, email, position, account, note, ignore-too-far).
 * Time/room/assignment preferences and bulk list update keep their own screens.
 *
 * Requests:
 * <ul>
 *   <li>{@code InstructorEditRequest} — load one instructor for editing.</li>
 *   <li>{@code InstructorAddInitRequest} — load the add form (department + position options).</li>
 *   <li>{@code InstructorSaveRequest} — create (departmentId set) or update (instructorId set).</li>
 *   <li>{@code InstructorDeleteRequest} — delete an instructor.</li>
 * </ul>
 * Additive.
 *
 * @author Angular migration
 */
public class InstructorEditInterface implements IsSerializable {

	public static class InstructorEditRequest implements GwtRpcRequest<InstructorEditResponse> {
		private Long iInstructorId;
		public InstructorEditRequest() {}
		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }
		@Override public String toString() { return "InstructorEdit[" + iInstructorId + "]"; }
	}

	public static class InstructorAddInitRequest implements GwtRpcRequest<InstructorEditResponse> {
		public InstructorAddInitRequest() {}
		@Override public String toString() { return "InstructorAddInit[]"; }
	}

	public static class InstructorDeleteRequest implements GwtRpcRequest<InstructorEditResponse> {
		private Long iInstructorId;
		public InstructorDeleteRequest() {}
		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }
		@Override public String toString() { return "InstructorDelete[" + iInstructorId + "]"; }
	}

	public static class InstructorSaveRequest implements GwtRpcRequest<InstructorEditResponse> {
		private Long iInstructorId;
		private Long iDepartmentId;
		private String iFname;
		private String iMname;
		private String iLname;
		private String iTitle;
		private String iExternalId;
		private String iCareerAcct;
		private String iEmail;
		private Long iPositionTypeId;
		private String iNote;
		private boolean iIgnoreTooFar;

		public InstructorSaveRequest() {}

		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }
		public Long getDepartmentId() { return iDepartmentId; }
		public void setDepartmentId(Long departmentId) { iDepartmentId = departmentId; }
		public String getFname() { return iFname; }
		public void setFname(String fname) { iFname = fname; }
		public String getMname() { return iMname; }
		public void setMname(String mname) { iMname = mname; }
		public String getLname() { return iLname; }
		public void setLname(String lname) { iLname = lname; }
		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }
		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		public String getCareerAcct() { return iCareerAcct; }
		public void setCareerAcct(String careerAcct) { iCareerAcct = careerAcct; }
		public String getEmail() { return iEmail; }
		public void setEmail(String email) { iEmail = email; }
		public Long getPositionTypeId() { return iPositionTypeId; }
		public void setPositionTypeId(Long positionTypeId) { iPositionTypeId = positionTypeId; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public boolean isIgnoreTooFar() { return iIgnoreTooFar; }
		public void setIgnoreTooFar(boolean ignoreTooFar) { iIgnoreTooFar = ignoreTooFar; }

		@Override public String toString() { return "InstructorSave[" + (iInstructorId != null ? "edit " + iInstructorId : "add dept " + iDepartmentId) + "]"; }
	}

	public static class InstructorEditResponse implements GwtRpcResponse {
		private Long iInstructorId;
		private Long iDepartmentId;
		private String iDeptName;
		private String iDeptCode;
		private String iFname;
		private String iMname;
		private String iLname;
		private String iTitle;
		private String iExternalId;
		private String iCareerAcct;
		private String iEmail;
		private Long iPositionTypeId;
		private String iNote;
		private boolean iIgnoreTooFar;
		private boolean iSaved;
		private boolean iDeleted;
		private List<IdName> iPositionTypes = new ArrayList<IdName>();
		private List<IdName> iDepartments = new ArrayList<IdName>();

		public InstructorEditResponse() {}

		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }
		public Long getDepartmentId() { return iDepartmentId; }
		public void setDepartmentId(Long departmentId) { iDepartmentId = departmentId; }
		public String getDeptName() { return iDeptName; }
		public void setDeptName(String deptName) { iDeptName = deptName; }
		public String getDeptCode() { return iDeptCode; }
		public void setDeptCode(String deptCode) { iDeptCode = deptCode; }
		public String getFname() { return iFname; }
		public void setFname(String fname) { iFname = fname; }
		public String getMname() { return iMname; }
		public void setMname(String mname) { iMname = mname; }
		public String getLname() { return iLname; }
		public void setLname(String lname) { iLname = lname; }
		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }
		public String getExternalId() { return iExternalId; }
		public void setExternalId(String externalId) { iExternalId = externalId; }
		public String getCareerAcct() { return iCareerAcct; }
		public void setCareerAcct(String careerAcct) { iCareerAcct = careerAcct; }
		public String getEmail() { return iEmail; }
		public void setEmail(String email) { iEmail = email; }
		public Long getPositionTypeId() { return iPositionTypeId; }
		public void setPositionTypeId(Long positionTypeId) { iPositionTypeId = positionTypeId; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public boolean isIgnoreTooFar() { return iIgnoreTooFar; }
		public void setIgnoreTooFar(boolean ignoreTooFar) { iIgnoreTooFar = ignoreTooFar; }
		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }
		public boolean isDeleted() { return iDeleted; }
		public void setDeleted(boolean deleted) { iDeleted = deleted; }
		public List<IdName> getPositionTypes() { return iPositionTypes; }
		public void addPositionType(IdName pt) { iPositionTypes.add(pt); }
		public List<IdName> getDepartments() { return iDepartments; }
		public void addDepartment(IdName d) { iDepartments.add(d); }
	}

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
}
