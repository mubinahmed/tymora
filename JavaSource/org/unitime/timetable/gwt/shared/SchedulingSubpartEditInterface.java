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
 * Scheduling Subpart Edit (legacy schedulingSubpartEdit.action) migrated to GwtRpc
 * command beans. Two requests share one response:
 * <ul>
 *   <li>{@code SubpartEditRequest} — read the editable subpart-row fields (plus the
 *       instructional-type and date-pattern option lists).</li>
 *   <li>{@code SubpartEditUpdateRequest} — persist the subpart-row subset that
 *       {@code SchedulingSubpartEditAction.doUpdate()} saves (itype, date pattern,
 *       auto-spread-in-time, student-allow-overlap).</li>
 * </ul>
 * Credit configuration and time/room/distribution preferences keep their own
 * handling and are read-only here. Additive.
 *
 * @author Angular migration
 */
public class SchedulingSubpartEditInterface implements IsSerializable {

	public static class SubpartEditRequest implements GwtRpcRequest<SubpartEditResponse> {
		private Long iSubpartId;

		public SubpartEditRequest() {}
		public SubpartEditRequest(Long subpartId) { iSubpartId = subpartId; }

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		@Override
		public String toString() { return "SubpartEdit[" + iSubpartId + "]"; }
	}

	public static class SubpartEditUpdateRequest implements GwtRpcRequest<SubpartEditResponse> {
		private Long iSubpartId;
		private Integer iInstructionalType;
		private Long iDatePatternId;
		private boolean iAutoSpreadInTime;
		private boolean iStudentAllowOverlap;

		public SubpartEditUpdateRequest() {}

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		public Integer getInstructionalType() { return iInstructionalType; }
		public void setInstructionalType(Integer instructionalType) { iInstructionalType = instructionalType; }

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public boolean isAutoSpreadInTime() { return iAutoSpreadInTime; }
		public void setAutoSpreadInTime(boolean autoSpreadInTime) { iAutoSpreadInTime = autoSpreadInTime; }

		public boolean isStudentAllowOverlap() { return iStudentAllowOverlap; }
		public void setStudentAllowOverlap(boolean studentAllowOverlap) { iStudentAllowOverlap = studentAllowOverlap; }

		@Override
		public String toString() { return "SubpartEditUpdate[" + iSubpartId + "]"; }
	}

	public static class SubpartEditResponse implements GwtRpcResponse {
		private Long iSubpartId;
		private Long iOfferingId;
		private Long iParentSubpartId;
		private String iInstructionalTypeLabel;
		private String iCourseName;
		private String iCourseTitle;
		private String iSubjectArea;
		private String iCourseNbr;
		private String iCreditText;
		private String iManagingDept;
		private String iParentSubpartLabel;
		private Integer iMinutesPerWeek;
		private Integer iInstructionalType;
		private Long iDatePatternId;
		private boolean iUnlimited;
		private boolean iDatePatternEditable;
		private boolean iSaved;
		private boolean iAutoSpreadInTime;
		private boolean iStudentAllowOverlap;
		private List<IdName> iDatePatternOptions = new ArrayList<IdName>();
		private List<IdName> iItypeOptions = new ArrayList<IdName>();

		public SubpartEditResponse() {}

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

		public String getCreditText() { return iCreditText; }
		public void setCreditText(String creditText) { iCreditText = creditText; }

		public String getManagingDept() { return iManagingDept; }
		public void setManagingDept(String managingDept) { iManagingDept = managingDept; }

		public String getParentSubpartLabel() { return iParentSubpartLabel; }
		public void setParentSubpartLabel(String parentSubpartLabel) { iParentSubpartLabel = parentSubpartLabel; }

		public Integer getMinutesPerWeek() { return iMinutesPerWeek; }
		public void setMinutesPerWeek(Integer minutesPerWeek) { iMinutesPerWeek = minutesPerWeek; }

		public Integer getInstructionalType() { return iInstructionalType; }
		public void setInstructionalType(Integer instructionalType) { iInstructionalType = instructionalType; }

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public boolean isUnlimited() { return iUnlimited; }
		public void setUnlimited(boolean unlimited) { iUnlimited = unlimited; }

		public boolean isDatePatternEditable() { return iDatePatternEditable; }
		public void setDatePatternEditable(boolean datePatternEditable) { iDatePatternEditable = datePatternEditable; }

		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }

		public boolean isAutoSpreadInTime() { return iAutoSpreadInTime; }
		public void setAutoSpreadInTime(boolean autoSpreadInTime) { iAutoSpreadInTime = autoSpreadInTime; }

		public boolean isStudentAllowOverlap() { return iStudentAllowOverlap; }
		public void setStudentAllowOverlap(boolean studentAllowOverlap) { iStudentAllowOverlap = studentAllowOverlap; }

		public List<IdName> getDatePatternOptions() { return iDatePatternOptions; }
		public void addDatePatternOption(IdName option) { iDatePatternOptions.add(option); }

		public List<IdName> getItypeOptions() { return iItypeOptions; }
		public void addItypeOption(IdName option) { iItypeOptions.add(option); }
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
