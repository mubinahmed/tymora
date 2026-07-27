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
 * Protocol for the Relief Planning "Staff Absences" screen and the teacher
 * self-service "My Leave" screen. Administrators (right {@code StaffAbsences}) see
 * and manage every staff member's absences; a teacher (right {@code MyLeaveRequests})
 * sees and submits only their own. Dates are transmitted as {@code yyyy-MM-dd}
 * strings. Additive — introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
public class StaffAbsenceInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SAVE, DELETE, APPROVE, REJECT
	}

	/** A single staff-absence record. */
	public static class AbsenceInfo implements IsSerializable {
		private Long iId;
		private String iUid;
		private String iName;
		private Long iReasonId;
		private String iReasonLabel;
		private String iStartDate;
		private String iEndDate;
		private String iNote;
		private int iStatus;
		private String iStatusLabel;
		private boolean iCanEdit;

		public AbsenceInfo() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getUid() { return iUid; }
		public void setUid(String uid) { iUid = uid; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public Long getReasonId() { return iReasonId; }
		public void setReasonId(Long reasonId) { iReasonId = reasonId; }
		public String getReasonLabel() { return iReasonLabel; }
		public void setReasonLabel(String reasonLabel) { iReasonLabel = reasonLabel; }
		public String getStartDate() { return iStartDate; }
		public void setStartDate(String startDate) { iStartDate = startDate; }
		public String getEndDate() { return iEndDate; }
		public void setEndDate(String endDate) { iEndDate = endDate; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public int getStatus() { return iStatus; }
		public void setStatus(int status) { iStatus = status; }
		public String getStatusLabel() { return iStatusLabel; }
		public void setStatusLabel(String statusLabel) { iStatusLabel = statusLabel; }
		public boolean isCanEdit() { return iCanEdit; }
		public void setCanEdit(boolean canEdit) { iCanEdit = canEdit; }
	}

	/** A selectable option (absence reason or staff member). */
	public static class Option implements IsSerializable {
		private String iId;
		private String iLabel;
		public Option() {}
		public Option(String id, String label) { iId = id; iLabel = label; }
		public String getId() { return iId; }
		public void setId(String id) { iId = id; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	public static class StaffAbsenceRequest implements GwtRpcRequest<StaffAbsenceResponse> {
		private Operation iOperation = Operation.LOAD;
		private String iFrom;
		private String iTo;
		private boolean iMineOnly;
		private Long iId;
		private AbsenceInfo iAbsence;

		public StaffAbsenceRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public String getFrom() { return iFrom; }
		public void setFrom(String from) { iFrom = from; }
		public String getTo() { return iTo; }
		public void setTo(String to) { iTo = to; }
		public boolean isMineOnly() { return iMineOnly; }
		public void setMineOnly(boolean mineOnly) { iMineOnly = mineOnly; }
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public AbsenceInfo getAbsence() { return iAbsence; }
		public void setAbsence(AbsenceInfo absence) { iAbsence = absence; }

		@Override
		public String toString() { return "StaffAbsence[" + iOperation + "]"; }
	}

	public static class StaffAbsenceResponse implements GwtRpcResponse {
		private List<AbsenceInfo> iAbsences = new ArrayList<AbsenceInfo>();
		private List<Option> iReasons = new ArrayList<Option>();
		private List<Option> iStaff = new ArrayList<Option>();
		private boolean iCanManage;
		private String iMyUid;

		public StaffAbsenceResponse() {}

		public List<AbsenceInfo> getAbsences() { return iAbsences; }
		public void addAbsence(AbsenceInfo a) { iAbsences.add(a); }
		public List<Option> getReasons() { return iReasons; }
		public void addReason(Option o) { iReasons.add(o); }
		public List<Option> getStaff() { return iStaff; }
		public void addStaff(Option o) { iStaff.add(o); }
		public boolean isCanManage() { return iCanManage; }
		public void setCanManage(boolean canManage) { iCanManage = canManage; }
		public String getMyUid() { return iMyUid; }
		public void setMyUid(String myUid) { iMyUid = myUid; }
	}
}
