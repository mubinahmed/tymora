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
 * Add Examination (legacy examEdit.action without an examId, reached with
 * firstType / firstId) migrated to a GwtRpc command bean. One request drives
 * LOAD (defaults, examination types, seating options, the owner label and the
 * instructors of the owner's department) and SAVE (create the Exam with one
 * ExamOwner, mirroring ExamEditAction.doUpdate). Gated by Right.ExaminationAdd.
 *
 * firstType accepts the legacy strings: CourseOffering, InstructionalOffering,
 * InstrOfferingConfig, SchedulingSubpart (treated as its configuration, like the
 * legacy page) and Class_.
 *
 * @author Angular migration
 */
public class ExamAddInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE
	}

	public static class ExamTypeInfo implements IsSerializable {
		private Long iId;
		private String iLabel;
		private boolean iFinalType;
		private Integer iDefaultLength;

		public ExamTypeInfo() {}
		public ExamTypeInfo(Long id, String label, boolean isFinal, Integer defaultLength) {
			iId = id; iLabel = label; iFinalType = isFinal; iDefaultLength = defaultLength;
		}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
		public boolean isFinalType() { return iFinalType; }
		public void setFinalType(boolean isFinal) { iFinalType = isFinal; }
		public Integer getDefaultLength() { return iDefaultLength; }
		public void setDefaultLength(Integer defaultLength) { iDefaultLength = defaultLength; }
	}

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

	public static class ExamAddRequest implements GwtRpcRequest<ExamAddResponse> {
		private Operation iOperation = Operation.LOAD;
		private String iFirstType;
		private Long iFirstId;
		private Long iExamTypeId;
		private String iName;
		private String iNote;
		private Integer iLength;
		private Integer iSeatingType;
		private Integer iExamSize;
		private Integer iPrintOffset;
		private Integer iMaxNbrRooms;
		private List<Long> iInstructorIds = new ArrayList<Long>();

		public ExamAddRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }
		public String getFirstType() { return iFirstType; }
		public void setFirstType(String firstType) { iFirstType = firstType; }
		public Long getFirstId() { return iFirstId; }
		public void setFirstId(Long firstId) { iFirstId = firstId; }
		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }
		public String getName() { return iName; }
		public void setName(String name) { iName = name; }
		public String getNote() { return iNote; }
		public void setNote(String note) { iNote = note; }
		public Integer getLength() { return iLength; }
		public void setLength(Integer length) { iLength = length; }
		public Integer getSeatingType() { return iSeatingType; }
		public void setSeatingType(Integer seatingType) { iSeatingType = seatingType; }
		public Integer getExamSize() { return iExamSize; }
		public void setExamSize(Integer examSize) { iExamSize = examSize; }
		public Integer getPrintOffset() { return iPrintOffset; }
		public void setPrintOffset(Integer printOffset) { iPrintOffset = printOffset; }
		public Integer getMaxNbrRooms() { return iMaxNbrRooms; }
		public void setMaxNbrRooms(Integer maxNbrRooms) { iMaxNbrRooms = maxNbrRooms; }
		public List<Long> getInstructorIds() { return iInstructorIds; }
		public void setInstructorIds(List<Long> instructorIds) { iInstructorIds = instructorIds; }

		@Override
		public String toString() { return "ExamAdd[" + iOperation + "," + iFirstType + "," + iFirstId + "]"; }
	}

	public static class ExamAddResponse implements GwtRpcResponse {
		private Long iExamId;
		private String iOwnerType;
		private String iOwnerLabel;
		private Long iExamTypeId;
		private Integer iLength;
		private Integer iSeatingType;
		private Integer iMaxNbrRooms;
		private String iSizeNote;
		private List<ExamTypeInfo> iExamTypes = new ArrayList<ExamTypeInfo>();
		private List<IdLabel> iSeatingOptions = new ArrayList<IdLabel>();
		private List<IdLabel> iInstructors = new ArrayList<IdLabel>();

		public ExamAddResponse() {}

		public Long getExamId() { return iExamId; }
		public void setExamId(Long examId) { iExamId = examId; }
		public String getOwnerType() { return iOwnerType; }
		public void setOwnerType(String ownerType) { iOwnerType = ownerType; }
		public String getOwnerLabel() { return iOwnerLabel; }
		public void setOwnerLabel(String ownerLabel) { iOwnerLabel = ownerLabel; }
		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }
		public Integer getLength() { return iLength; }
		public void setLength(Integer length) { iLength = length; }
		public Integer getSeatingType() { return iSeatingType; }
		public void setSeatingType(Integer seatingType) { iSeatingType = seatingType; }
		public Integer getMaxNbrRooms() { return iMaxNbrRooms; }
		public void setMaxNbrRooms(Integer maxNbrRooms) { iMaxNbrRooms = maxNbrRooms; }
		public String getSizeNote() { return iSizeNote; }
		public void setSizeNote(String sizeNote) { iSizeNote = sizeNote; }
		public List<ExamTypeInfo> getExamTypes() { return iExamTypes; }
		public void addExamType(ExamTypeInfo type) { iExamTypes.add(type); }
		public List<IdLabel> getSeatingOptions() { return iSeatingOptions; }
		public void addSeatingOption(IdLabel option) { iSeatingOptions.add(option); }
		public List<IdLabel> getInstructors() { return iInstructors; }
		public void addInstructor(IdLabel instructor) { iInstructors.add(instructor); }
	}
}
