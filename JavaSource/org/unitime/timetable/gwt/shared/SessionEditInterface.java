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
 * Edit protocol for an academic Session (SessionEditBackend). One request carries
 * an operation (LOAD / SAVE / DELETE) and the editable fields; the response
 * returns the loaded fields plus the option lists (status types, default date
 * patterns, class-duration types, instructional methods, default student
 * sectioning statuses).
 *
 * <p>Aligned with the legacy Struts sessionEdit page: identity (initiative /
 * year / term), the date boundaries (session begin, classes end, exam begin,
 * session end, event begin/end), status, default date pattern, default class
 * duration type, default instructional method, the online-student-scheduling
 * week boundaries (enroll / change / drop), the default sectioning status and the
 * notification dates. The interactive HOLIDAYS calendar, the DATE/TIME pattern
 * editors and ROLL-FORWARD remain dedicated legacy screens and are left
 * untouched on save.</p>
 *
 * @author Angular migration
 */
public class SessionEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, SAVE, DELETE
	}

	/** Generic id + label option (status types, date patterns, duration types, …). */
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
		private String iExamBeginDate;
		private String iEventBeginDate;
		private String iEventEndDate;
		private Long iStatusTypeId;
		private Long iDefaultDatePatternId;
		private Long iDurationTypeId;
		private Long iInstructionalMethodId;
		private Integer iWkEnroll;
		private Integer iWkChange;
		private Integer iWkDrop;
		private Long iSectStatusId;
		private String iNotificationsBegin;
		private String iNotificationsEnd;

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
		public void setSessionBeginDateTime(String v) { iSessionBeginDateTime = v; }

		public String getClassesEndDateTime() { return iClassesEndDateTime; }
		public void setClassesEndDateTime(String v) { iClassesEndDateTime = v; }

		public String getSessionEndDateTime() { return iSessionEndDateTime; }
		public void setSessionEndDateTime(String v) { iSessionEndDateTime = v; }

		public String getExamBeginDate() { return iExamBeginDate; }
		public void setExamBeginDate(String v) { iExamBeginDate = v; }

		public String getEventBeginDate() { return iEventBeginDate; }
		public void setEventBeginDate(String v) { iEventBeginDate = v; }

		public String getEventEndDate() { return iEventEndDate; }
		public void setEventEndDate(String v) { iEventEndDate = v; }

		public Long getStatusTypeId() { return iStatusTypeId; }
		public void setStatusTypeId(Long v) { iStatusTypeId = v; }

		public Long getDefaultDatePatternId() { return iDefaultDatePatternId; }
		public void setDefaultDatePatternId(Long v) { iDefaultDatePatternId = v; }

		public Long getDurationTypeId() { return iDurationTypeId; }
		public void setDurationTypeId(Long v) { iDurationTypeId = v; }

		public Long getInstructionalMethodId() { return iInstructionalMethodId; }
		public void setInstructionalMethodId(Long v) { iInstructionalMethodId = v; }

		public Integer getWkEnroll() { return iWkEnroll; }
		public void setWkEnroll(Integer v) { iWkEnroll = v; }

		public Integer getWkChange() { return iWkChange; }
		public void setWkChange(Integer v) { iWkChange = v; }

		public Integer getWkDrop() { return iWkDrop; }
		public void setWkDrop(Integer v) { iWkDrop = v; }

		public Long getSectStatusId() { return iSectStatusId; }
		public void setSectStatusId(Long v) { iSectStatusId = v; }

		public String getNotificationsBegin() { return iNotificationsBegin; }
		public void setNotificationsBegin(String v) { iNotificationsBegin = v; }

		public String getNotificationsEnd() { return iNotificationsEnd; }
		public void setNotificationsEnd(String v) { iNotificationsEnd = v; }

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
		private String iExamBeginDate;
		private String iEventBeginDate;
		private String iEventEndDate;
		private Long iStatusTypeId;
		private Long iDefaultDatePatternId;
		private Long iDurationTypeId;
		private Long iInstructionalMethodId;
		private Integer iWkEnroll;
		private Integer iWkChange;
		private Integer iWkDrop;
		private Long iSectStatusId;
		private String iNotificationsBegin;
		private String iNotificationsEnd;
		private String iLabel;
		private String iDateFormat;
		private boolean iCanEdit = false;
		private boolean iCanDelete = false;
		private List<StatusOption> iStatuses = new ArrayList<StatusOption>();
		private List<StatusOption> iDatePatterns = new ArrayList<StatusOption>();
		private List<StatusOption> iDurationTypes = new ArrayList<StatusOption>();
		private List<StatusOption> iInstructionalMethods = new ArrayList<StatusOption>();
		private List<StatusOption> iSectStatuses = new ArrayList<StatusOption>();

		public SessionEditResponse() {}

		public Long getUniqueId() { return iUniqueId; }
		public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

		public String getAcademicInitiative() { return iAcademicInitiative; }
		public void setAcademicInitiative(String v) { iAcademicInitiative = v; }

		public String getAcademicYear() { return iAcademicYear; }
		public void setAcademicYear(String v) { iAcademicYear = v; }

		public String getAcademicTerm() { return iAcademicTerm; }
		public void setAcademicTerm(String v) { iAcademicTerm = v; }

		public String getSessionBeginDateTime() { return iSessionBeginDateTime; }
		public void setSessionBeginDateTime(String v) { iSessionBeginDateTime = v; }

		public String getClassesEndDateTime() { return iClassesEndDateTime; }
		public void setClassesEndDateTime(String v) { iClassesEndDateTime = v; }

		public String getSessionEndDateTime() { return iSessionEndDateTime; }
		public void setSessionEndDateTime(String v) { iSessionEndDateTime = v; }

		public String getExamBeginDate() { return iExamBeginDate; }
		public void setExamBeginDate(String v) { iExamBeginDate = v; }

		public String getEventBeginDate() { return iEventBeginDate; }
		public void setEventBeginDate(String v) { iEventBeginDate = v; }

		public String getEventEndDate() { return iEventEndDate; }
		public void setEventEndDate(String v) { iEventEndDate = v; }

		public Long getStatusTypeId() { return iStatusTypeId; }
		public void setStatusTypeId(Long v) { iStatusTypeId = v; }

		public Long getDefaultDatePatternId() { return iDefaultDatePatternId; }
		public void setDefaultDatePatternId(Long v) { iDefaultDatePatternId = v; }

		public Long getDurationTypeId() { return iDurationTypeId; }
		public void setDurationTypeId(Long v) { iDurationTypeId = v; }

		public Long getInstructionalMethodId() { return iInstructionalMethodId; }
		public void setInstructionalMethodId(Long v) { iInstructionalMethodId = v; }

		public Integer getWkEnroll() { return iWkEnroll; }
		public void setWkEnroll(Integer v) { iWkEnroll = v; }

		public Integer getWkChange() { return iWkChange; }
		public void setWkChange(Integer v) { iWkChange = v; }

		public Integer getWkDrop() { return iWkDrop; }
		public void setWkDrop(Integer v) { iWkDrop = v; }

		public Long getSectStatusId() { return iSectStatusId; }
		public void setSectStatusId(Long v) { iSectStatusId = v; }

		public String getNotificationsBegin() { return iNotificationsBegin; }
		public void setNotificationsBegin(String v) { iNotificationsBegin = v; }

		public String getNotificationsEnd() { return iNotificationsEnd; }
		public void setNotificationsEnd(String v) { iNotificationsEnd = v; }

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

		public List<StatusOption> getDatePatterns() { return iDatePatterns; }
		public void addDatePattern(StatusOption o) { iDatePatterns.add(o); }

		public List<StatusOption> getDurationTypes() { return iDurationTypes; }
		public void addDurationType(StatusOption o) { iDurationTypes.add(o); }

		public List<StatusOption> getInstructionalMethods() { return iInstructionalMethods; }
		public void addInstructionalMethod(StatusOption o) { iInstructionalMethods.add(o); }

		public List<StatusOption> getSectStatuses() { return iSectStatuses; }
		public void addSectStatus(StatusOption o) { iSectStatuses.add(o); }
	}
}
