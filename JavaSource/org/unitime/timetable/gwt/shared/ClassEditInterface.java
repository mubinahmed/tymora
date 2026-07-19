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
 * Class Edit (legacy classEdit.action) migrated to GwtRpc command beans. Two
 * requests share one response:
 * <ul>
 *   <li>{@code ClassEditRequest} — read the editable class-data fields (and the
 *       date-pattern option list) for one class.</li>
 *   <li>{@code ClassEditUpdateRequest} — persist those fields (the subset that
 *       {@code ClassEditAction.doUpdate()} saves for the class row itself).</li>
 * </ul>
 * Instructor assignment and time/room/distribution preferences keep their own
 * dedicated screens and are intentionally out of scope here. Additive.
 *
 * @author Angular migration
 */
public class ClassEditInterface implements IsSerializable {

	public static class ClassEditRequest implements GwtRpcRequest<ClassEditResponse> {
		private Long iClassId;

		public ClassEditRequest() {}
		public ClassEditRequest(Long classId) { iClassId = classId; }

		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }

		@Override
		public String toString() { return "ClassEdit[" + iClassId + "]"; }
	}

	public static class ClassEditUpdateRequest implements GwtRpcRequest<ClassEditResponse> {
		private Long iClassId;
		private Integer iExpectedCapacity;
		private Integer iMaxExpectedCapacity;
		private Float iRoomRatio;
		private Integer iNbrRooms;
		private boolean iSplitAttendance;
		private Long iDatePatternId;
		private String iNotes;
		private String iSchedulePrintNote;
		private boolean iEnabledForStudentScheduling;
		private boolean iDisplayInstructor;

		public ClassEditUpdateRequest() {}

		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }

		public Integer getExpectedCapacity() { return iExpectedCapacity; }
		public void setExpectedCapacity(Integer expectedCapacity) { iExpectedCapacity = expectedCapacity; }

		public Integer getMaxExpectedCapacity() { return iMaxExpectedCapacity; }
		public void setMaxExpectedCapacity(Integer maxExpectedCapacity) { iMaxExpectedCapacity = maxExpectedCapacity; }

		public Float getRoomRatio() { return iRoomRatio; }
		public void setRoomRatio(Float roomRatio) { iRoomRatio = roomRatio; }

		public Integer getNbrRooms() { return iNbrRooms; }
		public void setNbrRooms(Integer nbrRooms) { iNbrRooms = nbrRooms; }

		public boolean isSplitAttendance() { return iSplitAttendance; }
		public void setSplitAttendance(boolean splitAttendance) { iSplitAttendance = splitAttendance; }

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public String getNotes() { return iNotes; }
		public void setNotes(String notes) { iNotes = notes; }

		public String getSchedulePrintNote() { return iSchedulePrintNote; }
		public void setSchedulePrintNote(String schedulePrintNote) { iSchedulePrintNote = schedulePrintNote; }

		public boolean isEnabledForStudentScheduling() { return iEnabledForStudentScheduling; }
		public void setEnabledForStudentScheduling(boolean enabledForStudentScheduling) { iEnabledForStudentScheduling = enabledForStudentScheduling; }

		public boolean isDisplayInstructor() { return iDisplayInstructor; }
		public void setDisplayInstructor(boolean displayInstructor) { iDisplayInstructor = displayInstructor; }

		@Override
		public String toString() { return "ClassEditUpdate[" + iClassId + "]"; }
	}

	public static class ClassEditResponse implements GwtRpcResponse {
		private Long iClassId;
		private Long iOfferingId;
		private Long iSubpartId;
		private String iClassName;
		private String iSection;
		private String iItypeDesc;
		private String iCourseName;
		private String iCourseTitle;
		private String iManagingDept;
		private String iLms;
		private boolean iUnlimited;
		private boolean iDatePatternEditable;
		private boolean iSaved;
		private Integer iExpectedCapacity;
		private Integer iMaxExpectedCapacity;
		private Float iRoomRatio;
		private Integer iNbrRooms;
		private boolean iSplitAttendance;
		private Long iDatePatternId;
		private String iNotes;
		private String iSchedulePrintNote;
		private boolean iEnabledForStudentScheduling;
		private boolean iDisplayInstructor;
		private List<IdName> iDatePatternOptions = new ArrayList<IdName>();

		public ClassEditResponse() {}

		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		public String getClassName() { return iClassName; }
		public void setClassName(String className) { iClassName = className; }

		public String getSection() { return iSection; }
		public void setSection(String section) { iSection = section; }

		public String getItypeDesc() { return iItypeDesc; }
		public void setItypeDesc(String itypeDesc) { iItypeDesc = itypeDesc; }

		public String getCourseName() { return iCourseName; }
		public void setCourseName(String courseName) { iCourseName = courseName; }

		public String getCourseTitle() { return iCourseTitle; }
		public void setCourseTitle(String courseTitle) { iCourseTitle = courseTitle; }

		public String getManagingDept() { return iManagingDept; }
		public void setManagingDept(String managingDept) { iManagingDept = managingDept; }

		public String getLms() { return iLms; }
		public void setLms(String lms) { iLms = lms; }

		public boolean isUnlimited() { return iUnlimited; }
		public void setUnlimited(boolean unlimited) { iUnlimited = unlimited; }

		public boolean isDatePatternEditable() { return iDatePatternEditable; }
		public void setDatePatternEditable(boolean datePatternEditable) { iDatePatternEditable = datePatternEditable; }

		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }

		public Integer getExpectedCapacity() { return iExpectedCapacity; }
		public void setExpectedCapacity(Integer expectedCapacity) { iExpectedCapacity = expectedCapacity; }

		public Integer getMaxExpectedCapacity() { return iMaxExpectedCapacity; }
		public void setMaxExpectedCapacity(Integer maxExpectedCapacity) { iMaxExpectedCapacity = maxExpectedCapacity; }

		public Float getRoomRatio() { return iRoomRatio; }
		public void setRoomRatio(Float roomRatio) { iRoomRatio = roomRatio; }

		public Integer getNbrRooms() { return iNbrRooms; }
		public void setNbrRooms(Integer nbrRooms) { iNbrRooms = nbrRooms; }

		public boolean isSplitAttendance() { return iSplitAttendance; }
		public void setSplitAttendance(boolean splitAttendance) { iSplitAttendance = splitAttendance; }

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public String getNotes() { return iNotes; }
		public void setNotes(String notes) { iNotes = notes; }

		public String getSchedulePrintNote() { return iSchedulePrintNote; }
		public void setSchedulePrintNote(String schedulePrintNote) { iSchedulePrintNote = schedulePrintNote; }

		public boolean isEnabledForStudentScheduling() { return iEnabledForStudentScheduling; }
		public void setEnabledForStudentScheduling(boolean enabledForStudentScheduling) { iEnabledForStudentScheduling = enabledForStudentScheduling; }

		public boolean isDisplayInstructor() { return iDisplayInstructor; }
		public void setDisplayInstructor(boolean displayInstructor) { iDisplayInstructor = displayInstructor; }

		public List<IdName> getDatePatternOptions() { return iDatePatternOptions; }
		public void addDatePatternOption(IdName option) { iDatePatternOptions.add(option); }
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
