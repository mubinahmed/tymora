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
 * Protocol for the Relief Planning report: absence and relief-coverage figures over a
 * date range, with summaries by reason and by relief teacher plus a tabulated detail
 * list (the on-screen equivalent of the ITQ daily absence report; CSV export is done
 * client-side). Requires {@code ReliefPlanning}. Additive — introduces no changes to
 * existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
public class ReliefReportInterface implements IsSerializable {

	public static class SummaryRow implements IsSerializable {
		private String iLabel;
		private int iCount;
		public SummaryRow() {}
		public SummaryRow(String label, int count) { iLabel = label; iCount = count; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
		public int getCount() { return iCount; }
		public void setCount(int count) { iCount = count; }
	}

	public static class DetailRow implements IsSerializable {
		private String iDate;
		private String iAbsentName;
		private String iReasonLabel;
		private String iTimeText;
		private String iClassName;
		private String iRoomName;
		private String iReliefName;
		private String iAssignedBy;
		private String iStatusLabel;

		public DetailRow() {}
		public String getDate() { return iDate; }
		public void setDate(String date) { iDate = date; }
		public String getAbsentName() { return iAbsentName; }
		public void setAbsentName(String absentName) { iAbsentName = absentName; }
		public String getReasonLabel() { return iReasonLabel; }
		public void setReasonLabel(String reasonLabel) { iReasonLabel = reasonLabel; }
		public String getTimeText() { return iTimeText; }
		public void setTimeText(String timeText) { iTimeText = timeText; }
		public String getClassName() { return iClassName; }
		public void setClassName(String className) { iClassName = className; }
		public String getRoomName() { return iRoomName; }
		public void setRoomName(String roomName) { iRoomName = roomName; }
		public String getReliefName() { return iReliefName; }
		public void setReliefName(String reliefName) { iReliefName = reliefName; }
		public String getAssignedBy() { return iAssignedBy; }
		public void setAssignedBy(String assignedBy) { iAssignedBy = assignedBy; }
		public String getStatusLabel() { return iStatusLabel; }
		public void setStatusLabel(String statusLabel) { iStatusLabel = statusLabel; }
	}

	public static class ReliefReportRequest implements GwtRpcRequest<ReliefReportResponse> {
		private String iFrom;
		private String iTo;

		public ReliefReportRequest() {}
		public String getFrom() { return iFrom; }
		public void setFrom(String from) { iFrom = from; }
		public String getTo() { return iTo; }
		public void setTo(String to) { iTo = to; }

		@Override
		public String toString() { return "ReliefReport[" + iFrom + ".." + iTo + "]"; }
	}

	public static class ReliefReportResponse implements GwtRpcResponse {
		private List<SummaryRow> iByReason = new ArrayList<SummaryRow>();
		private List<SummaryRow> iByRelief = new ArrayList<SummaryRow>();
		private List<DetailRow> iDetails = new ArrayList<DetailRow>();

		public ReliefReportResponse() {}
		public List<SummaryRow> getByReason() { return iByReason; }
		public void addByReason(SummaryRow s) { iByReason.add(s); }
		public List<SummaryRow> getByRelief() { return iByRelief; }
		public void addByRelief(SummaryRow s) { iByRelief.add(s); }
		public List<DetailRow> getDetails() { return iDetails; }
		public void addDetail(DetailRow d) { iDetails.add(d); }
	}
}
