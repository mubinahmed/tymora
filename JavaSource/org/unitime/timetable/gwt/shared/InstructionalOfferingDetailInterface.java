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
 * Read-only Instructional Offering Detail (legacy instructionalOfferingDetail.action)
 * migrated to a GwtRpc command bean. The request resolves an offering by its
 * instructional-offering id (preferred) or by a course-offering id; the response
 * carries the offering summary, its cross-listed courses, offering coordinators
 * and the configuration -&gt; subpart -&gt; class tree. Additive: introduces no
 * changes to existing behavior. See {@code InstructionalOfferingDetailBackend}.
 *
 * @author Angular migration
 */
public class InstructionalOfferingDetailInterface implements IsSerializable {

	public static class InstructionalOfferingDetailRequest implements GwtRpcRequest<InstructionalOfferingDetailResponse> {
		private Long iOfferingId;
		private Long iCourseOfferingId;

		public InstructionalOfferingDetailRequest() {}

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getCourseOfferingId() { return iCourseOfferingId; }
		public void setCourseOfferingId(Long courseOfferingId) { iCourseOfferingId = courseOfferingId; }

		@Override
		public String toString() {
			return "InstructionalOfferingDetail[" + (iOfferingId != null ? "io=" + iOfferingId : "co=" + iCourseOfferingId) + "]";
		}
	}

	public static class InstructionalOfferingDetailResponse implements GwtRpcResponse {
		private Long iOfferingId;
		private Long iControllingCourseId;
		private String iCourseName;
		private String iTitle;
		private boolean iOffered;
		private boolean iUnlimited;
		private boolean iByReservationOnly;
		private String iConsent;
		private String iCredit;
		private String iWaitList;
		private String iNotes;
		private Integer iEnrollment;
		private Integer iLimit;
		private Integer iSnapshotLimit;
		private Integer iDemand;
		private Integer iProjectedDemand;
		private List<CoordinatorInfo> iCoordinators = new ArrayList<CoordinatorInfo>();
		private List<CrossListInfo> iCrossListings = new ArrayList<CrossListInfo>();
		private List<ConfigInfo> iConfigs = new ArrayList<ConfigInfo>();

		public InstructionalOfferingDetailResponse() {}

		public Long getOfferingId() { return iOfferingId; }
		public void setOfferingId(Long offeringId) { iOfferingId = offeringId; }

		public Long getControllingCourseId() { return iControllingCourseId; }
		public void setControllingCourseId(Long controllingCourseId) { iControllingCourseId = controllingCourseId; }

		public String getCourseName() { return iCourseName; }
		public void setCourseName(String courseName) { iCourseName = courseName; }

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public boolean isOffered() { return iOffered; }
		public void setOffered(boolean offered) { iOffered = offered; }

		public boolean isUnlimited() { return iUnlimited; }
		public void setUnlimited(boolean unlimited) { iUnlimited = unlimited; }

		public boolean isByReservationOnly() { return iByReservationOnly; }
		public void setByReservationOnly(boolean byReservationOnly) { iByReservationOnly = byReservationOnly; }

		public String getConsent() { return iConsent; }
		public void setConsent(String consent) { iConsent = consent; }

		public String getCredit() { return iCredit; }
		public void setCredit(String credit) { iCredit = credit; }

		public String getWaitList() { return iWaitList; }
		public void setWaitList(String waitList) { iWaitList = waitList; }

		public String getNotes() { return iNotes; }
		public void setNotes(String notes) { iNotes = notes; }

		public Integer getEnrollment() { return iEnrollment; }
		public void setEnrollment(Integer enrollment) { iEnrollment = enrollment; }

		public Integer getLimit() { return iLimit; }
		public void setLimit(Integer limit) { iLimit = limit; }

		public Integer getSnapshotLimit() { return iSnapshotLimit; }
		public void setSnapshotLimit(Integer snapshotLimit) { iSnapshotLimit = snapshotLimit; }

		public Integer getDemand() { return iDemand; }
		public void setDemand(Integer demand) { iDemand = demand; }

		public Integer getProjectedDemand() { return iProjectedDemand; }
		public void setProjectedDemand(Integer projectedDemand) { iProjectedDemand = projectedDemand; }

		public List<CoordinatorInfo> getCoordinators() { return iCoordinators; }
		public void addCoordinator(CoordinatorInfo coordinator) { iCoordinators.add(coordinator); }

		public List<CrossListInfo> getCrossListings() { return iCrossListings; }
		public void addCrossListing(CrossListInfo crossListing) { iCrossListings.add(crossListing); }

		public List<ConfigInfo> getConfigs() { return iConfigs; }
		public void addConfig(ConfigInfo config) { iConfigs.add(config); }
	}

	public static class CoordinatorInfo implements IsSerializable {
		private Long iInstructorId;
		private String iName;
		private String iShare;

		public CoordinatorInfo() {}

		public Long getInstructorId() { return iInstructorId; }
		public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getShare() { return iShare; }
		public void setShare(String share) { iShare = share; }
	}

	public static class CrossListInfo implements IsSerializable {
		private Long iCourseId;
		private String iCourse;
		private String iTitle;
		private boolean iControlling;
		private String iReservation;

		public CrossListInfo() {}

		public Long getCourseId() { return iCourseId; }
		public void setCourseId(Long courseId) { iCourseId = courseId; }

		public String getCourse() { return iCourse; }
		public void setCourse(String course) { iCourse = course; }

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public boolean isControlling() { return iControlling; }
		public void setControlling(boolean controlling) { iControlling = controlling; }

		public String getReservation() { return iReservation; }
		public void setReservation(String reservation) { iReservation = reservation; }
	}

	public static class ConfigInfo implements IsSerializable {
		private Long iId;
		private String iName;
		private String iLimit;
		private boolean iUnlimited;
		private List<SubpartInfo> iSubparts = new ArrayList<SubpartInfo>();

		public ConfigInfo() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getLimit() { return iLimit; }
		public void setLimit(String limit) { iLimit = limit; }

		public boolean isUnlimited() { return iUnlimited; }
		public void setUnlimited(boolean unlimited) { iUnlimited = unlimited; }

		public List<SubpartInfo> getSubparts() { return iSubparts; }
		public void addSubpart(SubpartInfo subpart) { iSubparts.add(subpart); }
	}

	public static class SubpartInfo implements IsSerializable {
		private Long iId;
		private String iType;
		private int iIndent;
		private List<ClassInfo> iClasses = new ArrayList<ClassInfo>();

		public SubpartInfo() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getType() { return iType; }
		public void setType(String type) { iType = type; }

		public int getIndent() { return iIndent; }
		public void setIndent(int indent) { iIndent = indent; }

		public List<ClassInfo> getClasses() { return iClasses; }
		public void addClass(ClassInfo clazz) { iClasses.add(clazz); }
	}

	public static class ClassInfo implements IsSerializable {
		private Long iId;
		private String iSection;
		private String iLimit;
		private String iEnrollment;
		private String iTime;
		private String iRoom;
		private String iInstructors;

		public ClassInfo() {}

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
