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
 * Create / Edit protocol for DepartmentStatusType (legacy Struts
 * deptStatusTypeEdit page). One request drives LOAD (return all editable
 * records + permission flags), SAVE (upsert one record) and DELETE (remove
 * one record). The backend (StatusTypeEditBackend) gates every operation with
 * Right.StatusTypes and returns the refreshed record list.
 *
 * @author Angular migration
 */
public class StatusTypeEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE,
		DELETE
	}

	public static class StatusTypeRecord implements IsSerializable {
		private Long iId;
		private String iReference;
		private String iLabel;
		private int iApply = 0;
		private int iStatus = 0;
		private Integer iOrd;

		public StatusTypeRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getReference() { return iReference; }
		public void setReference(String reference) { iReference = reference; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }

		public int getApply() { return iApply; }
		public void setApply(int apply) { iApply = apply; }

		public int getStatus() { return iStatus; }
		public void setStatus(int status) { iStatus = status; }

		public Integer getOrd() { return iOrd; }
		public void setOrd(Integer ord) { iOrd = ord; }
	}

	public static class StatusTypeEditRequest implements GwtRpcRequest<StatusTypeEditResponse> {
		private Operation iOperation = Operation.LOAD;
		private StatusTypeRecord iRecord;

		public StatusTypeEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public StatusTypeRecord getRecord() { return iRecord; }
		public void setRecord(StatusTypeRecord record) { iRecord = record; }

		@Override
		public String toString() {
			return "StatusTypeEdit[" + iOperation + (iRecord == null ? "" : "," + iRecord.getReference()) + "]";
		}
	}

	public static class StatusTypeEditResponse implements GwtRpcResponse {
		private boolean iEditable = false;
		private boolean iAddable = false;
		private boolean iDeletable = false;
		private List<StatusTypeRecord> iRecords = new ArrayList<StatusTypeRecord>();

		public StatusTypeEditResponse() {}

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public boolean isAddable() { return iAddable; }
		public void setAddable(boolean addable) { iAddable = addable; }

		public boolean isDeletable() { return iDeletable; }
		public void setDeletable(boolean deletable) { iDeletable = deletable; }

		public List<StatusTypeRecord> getRecords() { return iRecords; }
		public void addRecord(StatusTypeRecord record) { iRecords.add(record); }
	}
}
