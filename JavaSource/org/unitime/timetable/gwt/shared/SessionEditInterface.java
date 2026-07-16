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
 * Edit protocol for the core descriptive fields of an academic Session
 * (SessionEditBackend). One request carries an operation (LOAD / SAVE / DELETE)
 * and the editable fields; the response returns the loaded fields plus the
 * available status-type options. Introduced for the Angular migration of the
 * legacy Struts sessionEdit page; only the descriptive fields are handled --
 * roll-forward, holidays, exam/event periods and date/time pattern setup are
 * intentionally out of scope and left untouched on save.
 *
 * @author Angular migration
 */
public class SessionEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SAVE, DELETE
	}

	public static class StatusOption implements IsSerializable {
		private Long iId;
		private String iLabel;

		public StatusOption() {}
		public StatusOption(Long id, String label) { iId = id; iLabel = label; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	public static class SessionEditRequest implements GwtRpcRequest<SessionEditResponse> {
		private Operation iOperation;
		private Long iUniqueId;
		private String iAcademicInitiative;
		private String iAcademicYear;
		private String iAcademicTerm;
		private String iSessionBeginDateTime;
		private String iClassesEndDateTime;
		private String iSessionEndDateTime;
		private Long iStatusTypeId;

		public SessionEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getAcademicInitiative() { return iAcademicInitiative; }
		public void setAcademicInitiative(String academicInitiative) { iAcademicInitiative = academicInitiative; }

		public String getAcademicYear() { return iAcademicYear; }
		public void setAcademicYear(String academicYear) { iAcademicYear = academicYear; }

		public String getAcademicTerm() { return iAcademicTerm; }
		public void setAcademicTerm(String academicTerm) { iAcademicTerm = academicTerm; }

		public String getSessionBeginDateTime() { return iSessionBeginDateTime; }
		public void setSessionBeginDateTime(String sessionBeginDateTime) { iSessionBeginDateTime = sessionBeginDateTime; }

		public String getClassesEndDateTime() { return iClassesEndDateTime; }
		public void setClassesEndDateTime(String classesEndDateTime) { iClassesEndDateTime = classesEndDateTime; }

		public String getSessionEndDateTime() { return iSessionEndDateTime; }
		public void setSessionEndDateTime(String sessionEndDateTime) { iSessionEndDateTime = sessionEndDateTime; }

		public Long getStatusTypeId() { return iStatusTypeId; }
		public void setStatusTypeId(Long statusTypeId) { iStatusTypeId = statusTypeId; }

		@Override
		public String toString() { return "SessionEdit[" + iOperation + "," + iUniqueId + "]"; }
	}

	public static class SessionEditResponse implements GwtRpcResponse {
		private Long iUniqueId;
		private String iAcademicInitiative;
		private String iAcademicYear;
		private String iAcademicTerm;
		private String iSessionBeginDateTime;
		private String iClassesEndDateTime;
		private String iSessionEndDateTime;
		private Long iStatusTypeId;
		private String iLabel;
		private String iDateFormat;
		private boolean iCanEdit = false;
		private boolean iCanDelete = false;
		private List<StatusOption> iStatuses = new ArrayList<StatusOption>();

		public SessionEditResponse() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getAcademicInitiative() { return iAcademicInitiative; }
		public void setAcademicInitiative(String academicInitiative) { iAcademicInitiative = academicInitiative; }

		public String getAcademicYear() { return iAcademicYear; }
		public void setAcademicYear(String academicYear) { iAcademicYear = academicYear; }

		public String getAcademicTerm() { return iAcademicTerm; }
		public void setAcademicTerm(String academicTerm) { iAcademicTerm = academicTerm; }

		public String getSessionBeginDateTime() { return iSessionBeginDateTime; }
		public void setSessionBeginDateTime(String sessionBeginDateTime) { iSessionBeginDateTime = sessionBeginDateTime; }

		public String getClassesEndDateTime() { return iClassesEndDateTime; }
		public void setClassesEndDateTime(String classesEndDateTime) { iClassesEndDateTime = classesEndDateTime; }

		public String getSessionEndDateTime() { return iSessionEndDateTime; }
		public void setSessionEndDateTime(String sessionEndDateTime) { iSessionEndDateTime = sessionEndDateTime; }

		public Long getStatusTypeId() { return iStatusTypeId; }
		public void setStatusTypeId(Long statusTypeId) { iStatusTypeId = statusTypeId; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }

		public String getDateFormat() { return iDateFormat; }
		public void setDateFormat(String dateFormat) { iDateFormat = dateFormat; }

		public boolean isCanEdit() { return iCanEdit; }
		public void setCanEdit(boolean canEdit) { iCanEdit = canEdit; }

		public boolean isCanDelete() { return iCanDelete; }
		public void setCanDelete(boolean canDelete) { iCanDelete = canDelete; }

		public List<StatusOption> getStatuses() { return iStatuses; }
		public void addStatus(StatusOption status) { iStatuses.add(status); }
	}
}
