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
 * Examination Detail / Edit (legacy examDetail.action + examEdit.action) migrated to
 * GwtRpc command beans, scoped to the exam's scalar fields:
 * <ul>
 *   <li>{@code ExamDetailRequest} — read one exam (fields + owners + instructors + period).</li>
 *   <li>{@code ExamEditRequest} — load the editable scalar fields + seating options.</li>
 *   <li>{@code ExamEditUpdateRequest} — save name, note, length, seating, size, print offset,
 *       max rooms (the direct setters of {@code ExamEditAction}).</li>
 * </ul>
 * Exam owners (examined courses/classes), period/room/distribution preferences, instructor
 * assignment and add/clone keep their own screens. Additive.
 *
 * @author Angular migration
 */
public class ExamEditInterface implements IsSerializable {

	public static class ExamDetailRequest implements GwtRpcRequest<ExamEditResponse> {
		private Long iExamId;
		public ExamDetailRequest() {}
		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		@Override public String toString() { return "ExamDetail[" + iExamId + "]"; }
	}

	public static class ExamEditRequest implements GwtRpcRequest<ExamEditResponse> {
		private Long iExamId;
		public ExamEditRequest() {}
		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		@Override public String toString() { return "ExamEdit[" + iExamId + "]"; }
	}

	public static class ExamEditUpdateRequest implements GwtRpcRequest<ExamEditResponse> {
		private Long iExamId;
		private String iName;
		private String iNote;
		private Integer iLength;
		private int iSeatingType;
		private Integer iExamSize;
		private Integer iPrintOffset;
		private Integer iMaxNbrRooms;

		public ExamEditUpdateRequest() {}

		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public Integer getLength() { return iLength; }
		public void setLength(Integer length) { iLength = length; }
		public int getSeatingType() { return iSeatingType; }
		public void setSeatingType(int seatingType) { iSeatingType = seatingType; }
		public Integer getExamSize() { return iExamSize; }
		public void setExamSize(Integer examSize) { iExamSize = examSize; }
		public Integer getPrintOffset() { return iPrintOffset; }
		public void setPrintOffset(Integer printOffset) { iPrintOffset = printOffset; }
		public Integer getMaxNbrRooms() { return iMaxNbrRooms; }
		public void setMaxNbrRooms(Integer maxNbrRooms) { iMaxNbrRooms = maxNbrRooms; }

		@Override public String toString() { return "ExamEditUpdate[" + iExamId + "]"; }
	}

	public static class ExamEditResponse implements GwtRpcResponse {
		private Long iExamId;
		private String iLabel;
		private String iName;
		private String iNote;
		private Integer iLength;
		private Integer iExamSize;
		private String iSizeText;
		private Integer iPrintOffset;
		private int iSeatingType;
		private String iSeatingTypeLabel;
		private Integer iMaxNbrRooms;
		private Long iExamTypeId;
		private String iExamTypeLabel;
		private String iAssignedPeriod;
		private String iAssignedRoom;
		private String iAvgPeriod;
		private boolean iSaved;
		private List<String> iInstructors = new ArrayList<String>();
		private List<String> iOwners = new ArrayList<String>();
		private List<IdName> iSeatingOptions = new ArrayList<IdName>();

		public ExamEditResponse() {}

		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public Integer getLength() { return iLength; }
		public void setLength(Integer length) { iLength = length; }
		public Integer getExamSize() { return iExamSize; }
		public void setExamSize(Integer examSize) { iExamSize = examSize; }
		public String getSizeText() { return iSizeText; }
		public void setSizeText(String sizeText) { iSizeText = sizeText; }
		public Integer getPrintOffset() { return iPrintOffset; }
		public void setPrintOffset(Integer printOffset) { iPrintOffset = printOffset; }
		public int getSeatingType() { return iSeatingType; }
		public void setSeatingType(int seatingType) { iSeatingType = seatingType; }
		public String getSeatingTypeLabel() { return iSeatingTypeLabel; }
		public void setSeatingTypeLabel(String seatingTypeLabel) { iSeatingTypeLabel = seatingTypeLabel; }
		public Integer getMaxNbrRooms() { return iMaxNbrRooms; }
		public void setMaxNbrRooms(Integer maxNbrRooms) { iMaxNbrRooms = maxNbrRooms; }
		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }
		public String getExamTypeLabel() { return iExamTypeLabel; }
		public void setExamTypeLabel(String examTypeLabel) { iExamTypeLabel = examTypeLabel; }
		public String getAssignedPeriod() { return iAssignedPeriod; }
		public void setAssignedPeriod(String assignedPeriod) { iAssignedPeriod = assignedPeriod; }
		public String getAssignedRoom() { return iAssignedRoom; }
		public void setAssignedRoom(String assignedRoom) { iAssignedRoom = assignedRoom; }
		public String getAvgPeriod() { return iAvgPeriod; }
		public void setAvgPeriod(String avgPeriod) { iAvgPeriod = avgPeriod; }
		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }
		public List<String> getInstructors() { return iInstructors; }
		public void addInstructor(String instructor) { iInstructors.add(instructor); }
		public List<String> getOwners() { return iOwners; }
		public void addOwner(String owner) { iOwners.add(owner); }
		public List<IdName> getSeatingOptions() { return iSeatingOptions; }
		public void addSeatingOption(IdName option) { iSeatingOptions.add(option); }
	}

	public static class IdName implements IsSerializable {
		private Long iId;
		private String iName;
		public IdName() {}
		public IdName(Long id, String name) { iId = id; iName = name; }
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
	}
}
