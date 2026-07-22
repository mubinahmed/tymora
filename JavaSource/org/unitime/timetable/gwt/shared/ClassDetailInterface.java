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
 * Read-only Class Detail (legacy classDetail.action) migrated to a GwtRpc command
 * bean. Mirrors the read path of {@code ClassDetailAction.doLoad()}. Additive.
 *
 * @author Angular migration
 */
public class ClassDetailInterface implements IsSerializable {

	public static class ClassDetailRequest implements GwtRpcRequest<ClassDetailResponse> {
		private Long iClassId;

		public ClassDetailRequest() {}
		public ClassDetailRequest(Long classId) { iClassId = classId; }

		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }

		@Override
		public String toString() { return "ClassDetail[" + iClassId + "]"; }
	}

	public static class ClassDetailResponse implements GwtRpcResponse {
		private Long iClassId;
		private Long iOfferingId;
		private Long iSubpartId;
		private Long iParentClassId;
		private String iClassName;
		private String iSection;
		private String iItypeDesc;
		private String iCourseName;
		private String iCourseTitle;
		private String iParentClassName;
		private boolean iCrosslisted;
		private boolean iCancelled;
		private boolean iDisplayInstructor;
		private boolean iEnabledForStudentScheduling;
		private boolean iSplitAttendance;
		private String iExpectedCapacity;
		private String iEnrollment;
		private String iSnapshotLimit;
		private String iDatePattern;
		private Long iDatePatternId;
		private String iRoomRatio;
		private Integer iNbrRooms;
		private String iManagingDept;
		private String iFundingDept;
		private String iLms;
		private String iNotes;
		private String iSchedulePrintNote;
		private String iTime;
		private String iRoom;
		private List<ClassInstructorInfo> iInstructors = new ArrayList<ClassInstructorInfo>();

		public ClassDetailResponse() {}

		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		public Long getParentClassId() { return iParentClassId; }
		public void setParentClassId(Long parentClassId) { iParentClassId = parentClassId; }

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

		public String getParentClassName() { return iParentClassName; }
		public void setParentClassName(String parentClassName) { iParentClassName = parentClassName; }

		public boolean isCrosslisted() { return iCrosslisted; }
		public void setCrosslisted(boolean crosslisted) { iCrosslisted = crosslisted; }

		public boolean isCancelled() { return iCancelled; }
		public void setCancelled(boolean cancelled) { iCancelled = cancelled; }

		public boolean isDisplayInstructor() { return iDisplayInstructor; }
		public void setDisplayInstructor(boolean displayInstructor) { iDisplayInstructor = displayInstructor; }

		public boolean isEnabledForStudentScheduling() { return iEnabledForStudentScheduling; }
		public void setEnabledForStudentScheduling(boolean enabledForStudentScheduling) { iEnabledForStudentScheduling = enabledForStudentScheduling; }

		public boolean isSplitAttendance() { return iSplitAttendance; }
		public void setSplitAttendance(boolean splitAttendance) { iSplitAttendance = splitAttendance; }

		public String getExpectedCapacity() { return iExpectedCapacity; }
		public void setExpectedCapacity(String expectedCapacity) { iExpectedCapacity = expectedCapacity; }

		public String getEnrollment() { return iEnrollment; }
		public void setEnrollment(String enrollment) { iEnrollment = enrollment; }

		public String getSnapshotLimit() { return iSnapshotLimit; }
		public void setSnapshotLimit(String snapshotLimit) { iSnapshotLimit = snapshotLimit; }

		public String getDatePattern() { return iDatePattern; }
		public void setDatePattern(String datePattern) { iDatePattern = datePattern; }

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public String getRoomRatio() { return iRoomRatio; }
		public void setRoomRatio(String roomRatio) { iRoomRatio = roomRatio; }

		public Integer getNbrRooms() { return iNbrRooms; }
		public void setNbrRooms(Integer nbrRooms) { iNbrRooms = nbrRooms; }

		public String getManagingDept() { return iManagingDept; }
		public void setManagingDept(String managingDept) { iManagingDept = managingDept; }

		public String getFundingDept() { return iFundingDept; }
		public void setFundingDept(String fundingDept) { iFundingDept = fundingDept; }

		public String getLms() { return iLms; }
		public void setLms(String lms) { iLms = lms; }

		public String getNotes() { return iNotes; }
		public void setNotes(String notes) { iNotes = notes; }

		public String getSchedulePrintNote() { return iSchedulePrintNote; }
		public void setSchedulePrintNote(String schedulePrintNote) { iSchedulePrintNote = schedulePrintNote; }

		public String getTime() { return iTime; }
		public void setTime(String time) { iTime = time; }

		public String getRoom() { return iRoom; }
		public void setRoom(String room) { iRoom = room; }

		public List<ClassInstructorInfo> getInstructors() { return iInstructors; }
		public void addInstructor(ClassInstructorInfo instructor) { iInstructors.add(instructor); }
	}

	public static class ClassInstructorInfo implements IsSerializable {
		private Long iInstructorId;
		private String iName;
		private String iShare;
		private boolean iLead;

		public ClassInstructorInfo() {}

		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getShare() { return iShare; }
		public void setShare(String share) { iShare = share; }

		public boolean isLead() { return iLead; }
		public void setLead(boolean lead) { iLead = lead; }
	}
}
