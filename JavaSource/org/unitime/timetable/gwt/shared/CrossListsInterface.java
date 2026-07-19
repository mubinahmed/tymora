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
 * Cross Lists (legacy crossListsModify.action) migrated to GwtRpc command beans.
 * <ul>
 *   <li>{@code CrossListsRequest} — read the cross-listed courses of an offering,
 *       their controlling flag and reservation limits.</li>
 *   <li>{@code CrossListsUpdateRequest} — save the controlling course and the
 *       per-course reservation limits for the EXISTING set of courses (the safe
 *       case-2 of {@code CrossListsModifyAction.doUpdate()}).</li>
 * </ul>
 * Adding/removing courses (which splits/merges instructional offerings with wide
 * side effects) is intentionally out of scope here and stays on the legacy screen.
 * Additive.
 *
 * @author Angular migration
 */
public class CrossListsInterface implements IsSerializable {

	public static class CrossListsRequest implements GwtRpcRequest<CrossListsResponse> {
		private Long iOfferingId;

		public CrossListsRequest() {}
		public CrossListsRequest(Long offeringId) { iOfferingId = offeringId; }

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		@Override
		public String toString() { return "CrossLists[" + iOfferingId + "]"; }
	}

	public static class CrossListsUpdateRequest implements GwtRpcRequest<CrossListsResponse> {
		private Long iOfferingId;
		private Long iControllingCourseId;
		private List<CourseReservation> iCourses = new ArrayList<CourseReservation>();

		public CrossListsUpdateRequest() {}

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getControllingCourseId() { return iControllingCourseId; }
		public void setControllingCourseId(Long controllingCourseId) { iControllingCourseId = controllingCourseId; }

		public List<CourseReservation> getCourses() { return iCourses; }
		public void addCourse(CourseReservation course) { iCourses.add(course); }

		@Override
		public String toString() { return "CrossListsUpdate[" + iOfferingId + "]"; }
	}

	public static class CourseReservation implements IsSerializable {
		private Long iCourseId;
		private Integer iReservation;

		public CourseReservation() {}

		public Long getCourseId() { return iCourseId; }
		public void setCourseId(Long courseId) { iCourseId = courseId; }

		public Integer getReservation() { return iReservation; }
		public void setReservation(Integer reservation) { iReservation = reservation; }
	}

	public static class CrossListsResponse implements GwtRpcResponse {
		private Long iOfferingId;
		private Long iControllingCourseId;
		private String iOfferingName;
		private Integer iIoLimit;
		private boolean iUnlimited;
		private boolean iSingleCourseLimit;
		private boolean iSaved;
		private List<CrossCourse> iCourses = new ArrayList<CrossCourse>();

		public CrossListsResponse() {}

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getControllingCourseId() { return iControllingCourseId; }
		public void setControllingCourseId(Long controllingCourseId) { iControllingCourseId = controllingCourseId; }

		public String getOfferingName() { return iOfferingName; }
		public void setOfferingName(String offeringName) { iOfferingName = offeringName; }

		public Integer getIoLimit() { return iIoLimit; }
		public void setIoLimit(Integer ioLimit) { iIoLimit = ioLimit; }

		public boolean isUnlimited() { return iUnlimited; }
		public void setUnlimited(boolean unlimited) { iUnlimited = unlimited; }

		public boolean isSingleCourseLimit() { return iSingleCourseLimit; }
		public void setSingleCourseLimit(boolean singleCourseLimit) { iSingleCourseLimit = singleCourseLimit; }

		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }

		public List<CrossCourse> getCourses() { return iCourses; }
		public void addCourse(CrossCourse course) { iCourses.add(course); }
	}

	public static class CrossCourse implements IsSerializable {
		private Long iCourseId;
		private String iCourseName;
		private String iTitle;
		private boolean iControlling;
		private Integer iReservation;
		private boolean iCanDelete;

		public CrossCourse() {}

		public Long getCourseId() { return iCourseId; }
		public void setCourseId(Long courseId) { iCourseId = courseId; }

		public String getCourseName() { return iCourseName; }
		public void setCourseName(String courseName) { iCourseName = courseName; }

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public boolean isControlling() { return iControlling; }
		public void setControlling(boolean controlling) { iControlling = controlling; }

		public Integer getReservation() { return iReservation; }
		public void setReservation(Integer reservation) { iReservation = reservation; }

		public boolean isCanDelete() { return iCanDelete; }
		public void setCanDelete(boolean canDelete) { iCanDelete = canDelete; }
	}
}
