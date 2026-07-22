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
 * Create protocol for a new academic {@link org.unitime.timetable.model.Session}
 * (SessionCreateBackend). The create half was intentionally deferred by
 * {@link SessionEditInterface}/SessionEditBackend which are edit-only; this
 * interface owns the CREATE path only.
 *
 * <p>The LOAD operation returns the pieces the create form needs (available
 * status-type options and the expected date entry format). The SAVE operation
 * carries every mandatory NOT-NULL descriptive field the model requires and
 * persists a brand new session. Optional setup (default date pattern, holidays,
 * sectioning status, class duration type, instructional method, notifications
 * and roll-forward) is deliberately left unset / null on create and remains the
 * responsibility of the subsequent Edit / legacy pages -- all of those columns
 * are nullable so a valid row is created without them.</p>
 *
 * @author Angular migration
 */
public class SessionCreateInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SAVE
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

	public static class SessionCreateRequest implements GwtRpcRequest<SessionCreateResponse> {
		private Operation iOperation;
		private String iAcademicInitiative;
		private String iAcademicYear;
		private String iAcademicTerm;
		private String iSessionBeginDateTime;
		private String iClassesEndDateTime;
		private String iSessionEndDateTime;
		private String iExamBeginDate;
		private String iEventBeginDate;
		private String iEventEndDate;
		private Long iStatusTypeId;

		public SessionCreateRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

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

		public String getExamBeginDate() { return iExamBeginDate; }
		public void setExamBeginDate(String examBeginDate) { iExamBeginDate = examBeginDate; }

		public String getEventBeginDate() { return iEventBeginDate; }
		public void setEventBeginDate(String eventBeginDate) { iEventBeginDate = eventBeginDate; }

		public String getEventEndDate() { return iEventEndDate; }
		public void setEventEndDate(String eventEndDate) { iEventEndDate = eventEndDate; }

		public Long getStatusTypeId() { return iStatusTypeId; }
		public void setStatusTypeId(Long statusTypeId) { iStatusTypeId = statusTypeId; }

		@Override
		public String toString() { return "SessionCreate[" + iOperation + "]"; }
	}

	public static class SessionCreateResponse implements GwtRpcResponse {
		private Long iUniqueId;
		private String iLabel;
		private String iDateFormat;
		private boolean iCanAdd = false;
		private List<StatusOption> iStatuses = new ArrayList<StatusOption>();

		public SessionCreateResponse() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }

		public String getDateFormat() { return iDateFormat; }
		public void setDateFormat(String dateFormat) { iDateFormat = dateFormat; }

		public boolean isCanAdd() { return iCanAdd; }
		public void setCanAdd(boolean canAdd) { iCanAdd = canAdd; }

		public List<StatusOption> getStatuses() { return iStatuses; }
		public void addStatus(StatusOption status) { iStatuses.add(status); }
	}
}
