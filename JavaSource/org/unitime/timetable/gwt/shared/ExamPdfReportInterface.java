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
 * Protocol for the legacy examPdfReport.action (Examination PDF Reports) Struts
 * page. The report generation is asynchronous: {@code GENERATE} builds an
 * {@link org.unitime.timetable.form.ExamPdfReportForm} from the request and
 * enqueues a {@code PdfExamReportQueueItem} on the existing solver-server queue
 * processor (the SAME infrastructure the legacy action uses); {@code LOAD}
 * returns the report / format / examination-type / subject-area selectors, the
 * saved per-user defaults, and the current queue rows (name, status, progress,
 * output download link); {@code REMOVE} deletes a queue item. The generated
 * output is downloaded through the queue item's existing output link.
 *
 * Gated by {@link org.unitime.timetable.security.rights.Right#ExaminationPdfReports}
 * (Session qualified). E-mail delivery of the reports (address / cc / bcc /
 * deputies / instructors / students) remains on the legacy page. Additive:
 * introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class ExamPdfReportInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, GENERATE, REMOVE
	}

	/** Id + label pair (examination types, subject areas). */
	public static class IdLabel implements IsSerializable {
		private Long iId;
		private String iLabel;

		public IdLabel() {}
		public IdLabel(Long id, String label) { iId = id; iLabel = label; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/** Value + label pair for string-keyed selectors (reports, output formats). */
	public static class ComboItem implements IsSerializable {
		private String iValue;
		private String iLabel;

		public ComboItem() {}
		public ComboItem(String value, String label) { iValue = value; iLabel = label; }

		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/** One row of the report-generation queue. */
	public static class QueueRow implements IsSerializable {
		private String iId;
		private String iName;
		private String iStatus;
		private String iProgress;
		private String iOwner;
		private String iSession;
		private String iCreated;
		private String iStarted;
		private String iFinished;
		private String iOutput;
		private String iOutputLink;
		private String iLog;
		private boolean iCanDelete;

		public QueueRow() {}

		public String getId() { return iId; }
		public void setId(String id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getStatus() { return iStatus; }
		public void setStatus(String status) { iStatus = status; }

		public String getProgress() { return iProgress; }
		public void setProgress(String progress) { iProgress = progress; }

		public String getOwner() { return iOwner; }
		public void setOwner(String owner) { iOwner = owner; }

		public String getSession() { return iSession; }
		public void setSession(String session) { iSession = session; }

		public String getCreated() { return iCreated; }
		public void setCreated(String created) { iCreated = created; }

		public String getStarted() { return iStarted; }
		public void setStarted(String started) { iStarted = started; }

		public String getFinished() { return iFinished; }
		public void setFinished(String finished) { iFinished = finished; }

		public String getOutput() { return iOutput; }
		public void setOutput(String output) { iOutput = output; }

		public String getOutputLink() { return iOutputLink; }
		public void setOutputLink(String outputLink) { iOutputLink = outputLink; }

		public String getLog() { return iLog; }
		public void setLog(String log) { iLog = log; }

		public boolean isCanDelete() { return iCanDelete; }
		public void setCanDelete(boolean canDelete) { iCanDelete = canDelete; }
	}

	public static class ExamPdfReportRequest implements GwtRpcRequest<ExamPdfReportResponse> {
		private Operation iOperation = Operation.LOAD;
		private Long iExamTypeId;
		private List<String> iReports = new ArrayList<String>();
		private String iMode;
		private boolean iAll = true;
		private List<Long> iSubjects = new ArrayList<Long>();
		private String iRemoveId;

		// Report options (mirror ExamPdfReportForm scalar flags).
		private boolean iDispRooms;
		private boolean iDispLimit;
		private boolean iTotals;
		private boolean iDirect;
		private boolean iM2d;
		private boolean iBtb;
		private boolean iItype;
		private boolean iClassSchedule;
		private boolean iIgnoreEmptyExams;
		private boolean iDispNote;
		private boolean iCompact;
		private boolean iRoomDispNames;
		private String iLimit;
		private String iRoomCodes;
		private String iNoRoom;
		private String iSince;

		public ExamPdfReportRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public List<String> getReports() { return iReports; }
		public void setReports(List<String> reports) { iReports = reports; }

		public String getMode() { return iMode; }
		public void setMode(String mode) { iMode = mode; }

		public boolean isAll() { return iAll; }
		public void setAll(boolean all) { iAll = all; }

		public List<Long> getSubjects() { return iSubjects; }
		public void setSubjects(List<Long> subjects) { iSubjects = subjects; }

		public String getRemoveId() { return iRemoveId; }
		public void setRemoveId(String removeId) { iRemoveId = removeId; }

		public boolean isDispRooms() { return iDispRooms; }
		public void setDispRooms(boolean v) { iDispRooms = v; }
		public boolean isDispLimit() { return iDispLimit; }
		public void setDispLimit(boolean v) { iDispLimit = v; }
		public boolean isTotals() { return iTotals; }
		public void setTotals(boolean v) { iTotals = v; }
		public boolean isDirect() { return iDirect; }
		public void setDirect(boolean v) { iDirect = v; }
		public boolean isM2d() { return iM2d; }
		public void setM2d(boolean v) { iM2d = v; }
		public boolean isBtb() { return iBtb; }
		public void setBtb(boolean v) { iBtb = v; }
		public boolean isItype() { return iItype; }
		public void setItype(boolean v) { iItype = v; }
		public boolean isClassSchedule() { return iClassSchedule; }
		public void setClassSchedule(boolean v) { iClassSchedule = v; }
		public boolean isIgnoreEmptyExams() { return iIgnoreEmptyExams; }
		public void setIgnoreEmptyExams(boolean v) { iIgnoreEmptyExams = v; }
		public boolean isDispNote() { return iDispNote; }
		public void setDispNote(boolean v) { iDispNote = v; }
		public boolean isCompact() { return iCompact; }
		public void setCompact(boolean v) { iCompact = v; }
		public boolean isRoomDispNames() { return iRoomDispNames; }
		public void setRoomDispNames(boolean v) { iRoomDispNames = v; }
		public String getLimit() { return iLimit; }
		public void setLimit(String v) { iLimit = v; }
		public String getRoomCodes() { return iRoomCodes; }
		public void setRoomCodes(String v) { iRoomCodes = v; }
		public String getNoRoom() { return iNoRoom; }
		public void setNoRoom(String v) { iNoRoom = v; }
		public String getSince() { return iSince; }
		public void setSince(String v) { iSince = v; }

		@Override
		public String toString() { return "ExamPdfReport[" + iOperation + ",type=" + iExamTypeId + "]"; }
	}

	public static class ExamPdfReportResponse implements GwtRpcResponse {
		private String iTitle;
		private String iWarning;
		private Long iExamTypeId;
		private String iMode;
		private boolean iAll = true;
		private List<IdLabel> iExamTypes = new ArrayList<IdLabel>();
		private List<ComboItem> iReports = new ArrayList<ComboItem>();
		private List<ComboItem> iModes = new ArrayList<ComboItem>();
		private List<IdLabel> iSubjectAreas = new ArrayList<IdLabel>();
		private List<QueueRow> iQueue = new ArrayList<QueueRow>();

		// Saved per-user defaults (mirror ExamPdfReportForm.load).
		private boolean iDispRooms;
		private boolean iDispLimit;
		private boolean iTotals;
		private boolean iDirect;
		private boolean iM2d;
		private boolean iBtb;
		private boolean iItype;
		private boolean iClassSchedule;
		private boolean iIgnoreEmptyExams;
		private boolean iDispNote;
		private boolean iCompact;
		private boolean iRoomDispNames;
		private String iLimit;
		private String iRoomCodes;
		private String iNoRoom;
		private String iSince;

		public ExamPdfReportResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public String getWarning() { return iWarning; }
		public void setWarning(String warning) { iWarning = warning; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public String getMode() { return iMode; }
		public void setMode(String mode) { iMode = mode; }

		public boolean isAll() { return iAll; }
		public void setAll(boolean all) { iAll = all; }

		public List<IdLabel> getExamTypes() { return iExamTypes; }
		public void addExamType(IdLabel type) { iExamTypes.add(type); }

		public List<ComboItem> getReports() { return iReports; }
		public void addReport(ComboItem report) { iReports.add(report); }

		public List<ComboItem> getModes() { return iModes; }
		public void addMode(ComboItem mode) { iModes.add(mode); }

		public List<IdLabel> getSubjectAreas() { return iSubjectAreas; }
		public void addSubjectArea(IdLabel area) { iSubjectAreas.add(area); }

		public List<QueueRow> getQueue() { return iQueue; }
		public void addQueueRow(QueueRow row) { iQueue.add(row); }

		public boolean isDispRooms() { return iDispRooms; }
		public void setDispRooms(boolean v) { iDispRooms = v; }
		public boolean isDispLimit() { return iDispLimit; }
		public void setDispLimit(boolean v) { iDispLimit = v; }
		public boolean isTotals() { return iTotals; }
		public void setTotals(boolean v) { iTotals = v; }
		public boolean isDirect() { return iDirect; }
		public void setDirect(boolean v) { iDirect = v; }
		public boolean isM2d() { return iM2d; }
		public void setM2d(boolean v) { iM2d = v; }
		public boolean isBtb() { return iBtb; }
		public void setBtb(boolean v) { iBtb = v; }
		public boolean isItype() { return iItype; }
		public void setItype(boolean v) { iItype = v; }
		public boolean isClassSchedule() { return iClassSchedule; }
		public void setClassSchedule(boolean v) { iClassSchedule = v; }
		public boolean isIgnoreEmptyExams() { return iIgnoreEmptyExams; }
		public void setIgnoreEmptyExams(boolean v) { iIgnoreEmptyExams = v; }
		public boolean isDispNote() { return iDispNote; }
		public void setDispNote(boolean v) { iDispNote = v; }
		public boolean isCompact() { return iCompact; }
		public void setCompact(boolean v) { iCompact = v; }
		public boolean isRoomDispNames() { return iRoomDispNames; }
		public void setRoomDispNames(boolean v) { iRoomDispNames = v; }
		public String getLimit() { return iLimit; }
		public void setLimit(String v) { iLimit = v; }
		public String getRoomCodes() { return iRoomCodes; }
		public void setRoomCodes(String v) { iRoomCodes = v; }
		public String getNoRoom() { return iNoRoom; }
		public void setNoRoom(String v) { iNoRoom = v; }
		public String getSince() { return iSince; }
		public void setSince(String v) { iSince = v; }
	}
}
