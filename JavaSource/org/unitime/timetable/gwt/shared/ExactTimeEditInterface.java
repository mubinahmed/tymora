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
 * LOAD / SAVE protocol for the Exact Time Pattern editor (legacy Struts
 * exactTimeEdit page, ExactTimeEditAction). LOAD returns every ExactTimeMins row
 * with its (display-only) minutes-per-meeting range and the two editable fields
 * (number of time slots per meeting, break time in minutes); SAVE writes those
 * two editable fields back for every row. The backend (ExactTimeEditBackend)
 * gates every operation with Right.ExactTimes and always returns the refreshed
 * list. Matches the legacy page, which only edited nrSlots and breakTime and
 * never created or deleted rows.
 *
 * @author Angular migration
 */
public class ExactTimeEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE
	}

	public static class ExactTimeMinsRecord implements IsSerializable {
		private Long iId;
		private int iMinsPerMtgMin = 0;
		private int iMinsPerMtgMax = 0;
		private int iNrSlots = 0;
		private int iBreakTime = 0;

		public ExactTimeMinsRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public int getMinsPerMtgMin() { return iMinsPerMtgMin; }
		public void setMinsPerMtgMin(int minsPerMtgMin) { iMinsPerMtgMin = minsPerMtgMin; }

		public int getMinsPerMtgMax() { return iMinsPerMtgMax; }
		public void setMinsPerMtgMax(int minsPerMtgMax) { iMinsPerMtgMax = minsPerMtgMax; }

		public int getNrSlots() { return iNrSlots; }
		public void setNrSlots(int nrSlots) { iNrSlots = nrSlots; }

		public int getBreakTime() { return iBreakTime; }
		public void setBreakTime(int breakTime) { iBreakTime = breakTime; }
	}

	public static class ExactTimeEditRequest implements GwtRpcRequest<ExactTimeEditResponse> {
		private Operation iOperation = Operation.LOAD;
		private List<ExactTimeMinsRecord> iRecords = new ArrayList<ExactTimeMinsRecord>();

		public ExactTimeEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public List<ExactTimeMinsRecord> getRecords() { return iRecords; }
		public void setRecords(List<ExactTimeMinsRecord> records) { iRecords = records; }
		public void addRecord(ExactTimeMinsRecord record) { iRecords.add(record); }

		@Override
		public String toString() {
			return "ExactTimeEdit[" + iOperation + "," + (iRecords == null ? 0 : iRecords.size()) + "]";
		}
	}

	public static class ExactTimeEditResponse implements GwtRpcResponse {
		private boolean iEditable = false;
		private List<ExactTimeMinsRecord> iRecords = new ArrayList<ExactTimeMinsRecord>();

		public ExactTimeEditResponse() {}

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public List<ExactTimeMinsRecord> getRecords() { return iRecords; }
		public void addRecord(ExactTimeMinsRecord record) { iRecords.add(record); }
	}
}
