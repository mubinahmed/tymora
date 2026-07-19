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
 * Read-only Scheduling Subpart Detail (legacy schedulingSubpartDetail.action)
 * migrated to a GwtRpc command bean. Mirrors the read path of
 * {@code SchedulingSubpartDetailAction.doLoad()} plus the subpart's class list.
 * Additive.
 *
 * @author Angular migration
 */
public class SchedulingSubpartDetailInterface implements IsSerializable {

	public static class SchedulingSubpartDetailRequest implements GwtRpcRequest<SchedulingSubpartDetailResponse> {
		private Long iSubpartId;

		public SchedulingSubpartDetailRequest() {}
		public SchedulingSubpartDetailRequest(Long subpartId) { iSubpartId = subpartId; }

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		@Override
		public String toString() { return "SchedulingSubpartDetail[" + iSubpartId + "]"; }
	}

	public static class SchedulingSubpartDetailResponse implements GwtRpcResponse {
		private Long iSubpartId;
		private Long iOfferingId;
		private Long iParentSubpartId;
		private String iInstructionalTypeLabel;
		private String iCourseName;
		private String iCourseTitle;
		private String iSubjectArea;
		private String iCourseNbr;
		private String iDatePattern;
		private Long iDatePatternId;
		private String iCredit;
		private String iManagingDept;
		private String iParentSubpartLabel;
		private Integer iMinutesPerWeek;
		private boolean iUnlimitedEnroll;
		private boolean iAutoSpreadInTime;
		private boolean iStudentAllowOverlap;
		private List<SubpartClassInfo> iClasses = new ArrayList<SubpartClassInfo>();

		public SchedulingSubpartDetailResponse() {}

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getParentSubpartId() { return iParentSubpartId; }
		public void setParentSubpartId(Long parentSubpartId) { iParentSubpartId = parentSubpartId; }

		public String getInstructionalTypeLabel() { return iInstructionalTypeLabel; }
		public void setInstructionalTypeLabel(String instructionalTypeLabel) { iInstructionalTypeLabel = instructionalTypeLabel; }

		public String getCourseName() { return iCourseName; }
		public void setCourseName(String courseName) { iCourseName = courseName; }

		public String getCourseTitle() { return iCourseTitle; }
		public void setCourseTitle(String courseTitle) { iCourseTitle = courseTitle; }

		public String getSubjectArea() { return iSubjectArea; }
		public void setSubjectArea(String subjectArea) { iSubjectArea = subjectArea; }

		public String getCourseNbr() { return iCourseNbr; }
		public void setCourseNbr(String courseNbr) { iCourseNbr = courseNbr; }

		public String getDatePattern() { return iDatePattern; }
		public void setDatePattern(String datePattern) { iDatePattern = datePattern; }

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public String getCredit() { return iCredit; }
		public void setCredit(String credit) { iCredit = credit; }

		public String getManagingDept() { return iManagingDept; }
		public void setManagingDept(String managingDept) { iManagingDept = managingDept; }

		public String getParentSubpartLabel() { return iParentSubpartLabel; }
		public void setParentSubpartLabel(String parentSubpartLabel) { iParentSubpartLabel = parentSubpartLabel; }

		public Integer getMinutesPerWeek() { return iMinutesPerWeek; }
		public void setMinutesPerWeek(Integer minutesPerWeek) { iMinutesPerWeek = minutesPerWeek; }

		public boolean isUnlimitedEnroll() { return iUnlimitedEnroll; }
		public void setUnlimitedEnroll(boolean unlimitedEnroll) { iUnlimitedEnroll = unlimitedEnroll; }

		public boolean isAutoSpreadInTime() { return iAutoSpreadInTime; }
		public void setAutoSpreadInTime(boolean autoSpreadInTime) { iAutoSpreadInTime = autoSpreadInTime; }

		public boolean isStudentAllowOverlap() { return iStudentAllowOverlap; }
		public void setStudentAllowOverlap(boolean studentAllowOverlap) { iStudentAllowOverlap = studentAllowOverlap; }

		public List<SubpartClassInfo> getClasses() { return iClasses; }
		public void addClass(SubpartClassInfo clazz) { iClasses.add(clazz); }
	}

	public static class SubpartClassInfo implements IsSerializable {
		private Long iId;
		private String iSection;
		private String iLimit;
		private String iEnrollment;
		private String iTime;
		private String iRoom;
		private String iInstructors;

		public SubpartClassInfo() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getSection() { return iSection; }
		public void setSection(String section) { iSection = section; }

		public String getLimit() { return iLimit; }
		public void setLimit(String limit) { iLimit = limit; }

		public String getEnrollment() { return iEnrollment; }
		public void setEnrollment(String enrollment) { iEnrollment = enrollment; }

		public String getTime() { return iTime; }
		public void setTime(String time) { iTime = time; }

		public String getRoom() { return iRoom; }
		public void setRoom(String room) { iRoom = room; }

		public String getInstructors() { return iInstructors; }
		public void setInstructors(String instructors) { iInstructors = instructors; }
	}
}
