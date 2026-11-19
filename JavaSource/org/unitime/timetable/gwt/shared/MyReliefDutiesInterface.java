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
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.HandoverNoteInfo;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Teacher self-service "My Relief Duties" screen: the read-only counterpart to the
 * Relief Planning oversight board, scoped to lessons where the current user is the
 * assigned relief teacher, together with the absent teacher's shared handover
 * (instructions/link/attachment). Requires right {@code MyReliefDuties}. Dates are
 * {@code yyyy-MM-dd}. Additive — introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
public class MyReliefDutiesInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD
	}

	/** One lesson the current user is covering. */
	public static class DutyInfo implements IsSerializable {
		private Long iId;
		private String iMeetingDate;
		private String iAbsentName;
		private String iReasonLabel;
		private String iClassName;
		private String iTimeText;
		private String iRoomName;
		private List<HandoverNoteInfo> iHandoverNotes = new ArrayList<HandoverNoteInfo>();

		public DutyInfo() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getMeetingDate() { return iMeetingDate; }
		public void setMeetingDate(String meetingDate) { iMeetingDate = meetingDate; }
		public String getAbsentName() { return iAbsentName; }
		public void setAbsentName(String absentName) { iAbsentName = absentName; }
		public String getReasonLabel() { return iReasonLabel; }
		public void setReasonLabel(String reasonLabel) { iReasonLabel = reasonLabel; }
		public String getClassName() { return iClassName; }
		public void setClassName(String className) { iClassName = className; }
		public String getTimeText() { return iTimeText; }
		public void setTimeText(String timeText) { iTimeText = timeText; }
		public String getRoomName() { return iRoomName; }
		public void setRoomName(String roomName) { iRoomName = roomName; }
		public List<HandoverNoteInfo> getHandoverNotes() { return iHandoverNotes; }
		public void addHandoverNote(HandoverNoteInfo n) { iHandoverNotes.add(n); }
	}

	public static class MyReliefDutiesRequest implements GwtRpcRequest<MyReliefDutiesResponse> {
		private Operation iOperation = Operation.LOAD;
		private String iFrom;
		private String iTo;

		public MyReliefDutiesRequest() {}
		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public String getFrom() { return iFrom; }
		public void setFrom(String from) { iFrom = from; }
		public String getTo() { return iTo; }
		public void setTo(String to) { iTo = to; }

		@Override
		public String toString() { return "MyReliefDuties[" + iFrom + "," + iTo + "]"; }
	}

	public static class MyReliefDutiesResponse implements GwtRpcResponse {
		private List<DutyInfo> iDuties = new ArrayList<DutyInfo>();

		public MyReliefDutiesResponse() {}
		public List<DutyInfo> getDuties() { return iDuties; }
		public void addDuty(DutyInfo d) { iDuties.add(d); }
	}
}
