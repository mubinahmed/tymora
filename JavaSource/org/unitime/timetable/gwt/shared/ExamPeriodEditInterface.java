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
 * Create / Edit protocol for the legacy examPeriodEdit.action (Examination
 * Periods) Struts page. This is the editable sibling of the read-only
 * {@link ExamPeriodListInterface}; a single request drives LOAD (return every
 * examination period of the current academic session together with the exam
 * type and preference-level selectors and the permission flags), SAVE (create
 * or update one examination period) and DELETE (remove one period).
 *
 * A period is modelled with the exact fields the legacy Struts form renders:
 * a date (in DATE_ENTRY_FORMAT), a start time encoded as an HHMM integer, an
 * exam length in minutes, event start / stop offsets in minutes, an exam type
 * and a preference level. The backend translates these to / from the persisted
 * date offset, start slot and slot-based lengths exactly as the legacy
 * ExamPeriodEditForm did. The multi-period "auto-setup" wizard from the legacy
 * page is intentionally NOT part of this protocol (deferred).
 *
 * @author Angular migration
 */
public class ExamPeriodEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE,
		DELETE
	}

	/** One selectable examination type. */
	public static class ExamTypeInfo implements IsSerializable {
		private Long iId;
		private String iLabel;

		public ExamTypeInfo() {}
		public ExamTypeInfo(Long id, String label) { iId = id; iLabel = label; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/** One selectable preference level. */
	public static class PrefLevelInfo implements IsSerializable {
		private Long iId;
		private String iName;
		private String iProlog;

		public PrefLevelInfo() {}
		public PrefLevelInfo(Long id, String name, String prolog) { iId = id; iName = name; iProlog = prolog; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getProlog() { return iProlog; }
		public void setProlog(String prolog) { iProlog = prolog; }
	}

	/**
	 * One examination period. Numeric fields (date/start/length/offsets) are the
	 * editable values; the *Label fields are read-only display projections.
	 */
	public static class ExamPeriodRecord implements IsSerializable {
		private Long iId;
		private Long iExamTypeId;
		private String iExamTypeLabel;
		private String iDate;            // editable, DATE_ENTRY_FORMAT
		private String iDateLabel;       // display
		private Integer iStart;          // editable, HHMM (e.g. 1830)
		private String iStartLabel;      // display
		private String iEndLabel;        // display
		private Integer iLength;         // editable, minutes
		private Integer iStartOffset;    // editable, minutes
		private Integer iStopOffset;     // editable, minutes
		private Long iPrefLevelId;       // editable
		private String iPrefName;        // display
		private boolean iUsed = false;
		private boolean iEditable = true;

		public ExamPeriodRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public String getExamTypeLabel() { return iExamTypeLabel; }
		public void setExamTypeLabel(String examTypeLabel) { iExamTypeLabel = examTypeLabel; }

		public String getDate() { return iDate; }
		public void setDate(String date) { iDate = date; }

		public String getDateLabel() { return iDateLabel; }
		public void setDateLabel(String dateLabel) { iDateLabel = dateLabel; }

		public Integer getStart() { return iStart; }
		public void setStart(Integer start) { iStart = start; }

		public String getStartLabel() { return iStartLabel; }
		public void setStartLabel(String startLabel) { iStartLabel = startLabel; }

		public String getEndLabel() { return iEndLabel; }
		public void setEndLabel(String endLabel) { iEndLabel = endLabel; }

		public Integer getLength() { return iLength; }
		public void setLength(Integer length) { iLength = length; }

		public Integer getStartOffset() { return iStartOffset; }
		public void setStartOffset(Integer startOffset) { iStartOffset = startOffset; }

		public Integer getStopOffset() { return iStopOffset; }
		public void setStopOffset(Integer stopOffset) { iStopOffset = stopOffset; }

		public Long getPrefLevelId() { return iPrefLevelId; }
		public void setPrefLevelId(Long prefLevelId) { iPrefLevelId = prefLevelId; }

		public String getPrefName() { return iPrefName; }
		public void setPrefName(String prefName) { iPrefName = prefName; }

		public boolean isUsed() { return iUsed; }
		public void setUsed(boolean used) { iUsed = used; }

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }
	}

	public static class ExamPeriodEditRequest implements GwtRpcRequest<ExamPeriodEditResponse> {
		private Operation iOperation = Operation.LOAD;
		private Long iSessionId;
		private Long iExamTypeId;         // list filter (null == all types)
		private ExamPeriodRecord iRecord; // for SAVE / DELETE

		public ExamPeriodEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getSessionId() { return iSessionId; }
		public void setSessionId(Long sessionId) { iSessionId = sessionId; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public ExamPeriodRecord getRecord() { return iRecord; }
		public void setRecord(ExamPeriodRecord record) { iRecord = record; }

		@Override
		public String toString() { return "ExamPeriodEdit[" + iOperation + ", type=" + iExamTypeId + "]"; }
	}

	public static class ExamPeriodEditResponse implements GwtRpcResponse {
		private String iTitle;
		private boolean iEditable = false;
		private boolean iAddable = false;
		private boolean iDeletable = false;
		private Long iExamTypeId;
		private String iDefaultDate;
		private Integer iDefaultLength = 120;
		private List<ExamTypeInfo> iExamTypes = new ArrayList<ExamTypeInfo>();
		private List<PrefLevelInfo> iPrefLevels = new ArrayList<PrefLevelInfo>();
		private List<ExamPeriodRecord> iRecords = new ArrayList<ExamPeriodRecord>();

		public ExamPeriodEditResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public boolean isAddable() { return iAddable; }
		public void setAddable(boolean addable) { iAddable = addable; }

		public boolean isDeletable() { return iDeletable; }
		public void setDeletable(boolean deletable) { iDeletable = deletable; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public String getDefaultDate() { return iDefaultDate; }
		public void setDefaultDate(String defaultDate) { iDefaultDate = defaultDate; }

		public Integer getDefaultLength() { return iDefaultLength; }
		public void setDefaultLength(Integer defaultLength) { iDefaultLength = defaultLength; }

		public List<ExamTypeInfo> getExamTypes() { return iExamTypes; }
		public void addExamType(ExamTypeInfo type) { iExamTypes.add(type); }

		public List<PrefLevelInfo> getPrefLevels() { return iPrefLevels; }
		public void addPrefLevel(PrefLevelInfo level) { iPrefLevels.add(level); }

		public List<ExamPeriodRecord> getRecords() { return iRecords; }
		public void addRecord(ExamPeriodRecord record) { iRecords.add(record); }
	}
}
