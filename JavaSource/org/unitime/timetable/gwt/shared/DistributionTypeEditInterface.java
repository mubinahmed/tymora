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
 * Distribution Type Edit (legacy distributionTypeEdit.action) migrated to GwtRpc command
 * beans. {@code DistTypeListRequest} loads every distribution type plus the shared option
 * lists (session departments, preference levels); {@code DistTypeUpdateRequest} saves one
 * type's label/abbreviation/description/flags, its allowed preference levels (rebuilt into
 * the encoded {@code allowedPref} string) and its department set. Additive.
 *
 * @author Angular migration
 */
public class DistributionTypeEditInterface implements IsSerializable {

	public static class DistTypeListRequest implements GwtRpcRequest<DistTypeListResponse> {
		public DistTypeListRequest() {}
		@Override public String toString() { return "DistTypeList[]"; }
	}

	public static class DistTypeUpdateRequest implements GwtRpcRequest<DistTypeListResponse> {
		private Long iId;
		private String iLabel;
		private String iAbbreviation;
		private String iDescr;
		private boolean iInstructorPref;
		private boolean iSurvey;
		private boolean iVisible;
		private List<Long> iAllowedPrefIds = new ArrayList<Long>();
		private List<Long> iDepartmentIds = new ArrayList<Long>();

		public DistTypeUpdateRequest() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
		public String getAbbreviation() { return iAbbreviation; }
		public void setAbbreviation(String abbreviation) { iAbbreviation = abbreviation; }
		public String getDescr() { return iDescr; }
		public void setDescr(String descr) { iDescr = descr; }
		public boolean isInstructorPref() { return iInstructorPref; }
		public void setInstructorPref(boolean instructorPref) { iInstructorPref = instructorPref; }
		public boolean isSurvey() { return iSurvey; }
		public void setSurvey(boolean survey) { iSurvey = survey; }
		public boolean isVisible() { return iVisible; }
		public void setVisible(boolean visible) { iVisible = visible; }
		public List<Long> getAllowedPrefIds() { return iAllowedPrefIds; }
		public List<Long> getDepartmentIds() { return iDepartmentIds; }

		@Override public String toString() { return "DistTypeUpdate[" + iId + "]"; }
	}

	public static class DistTypeListResponse implements GwtRpcResponse {
		private boolean iSaved;
		private List<DistTypeInfo> iTypes = new ArrayList<DistTypeInfo>();
		private List<IdName> iDepartments = new ArrayList<IdName>();
		private List<IdName> iPrefLevels = new ArrayList<IdName>();

		public DistTypeListResponse() {}

		public boolean isSaved() { return iSaved; }
		public void setSaved(boolean saved) { iSaved = saved; }
		public List<DistTypeInfo> getTypes() { return iTypes; }
		public void addType(DistTypeInfo type) { iTypes.add(type); }
		public List<IdName> getDepartments() { return iDepartments; }
		public void addDepartment(IdName d) { iDepartments.add(d); }
		public List<IdName> getPrefLevels() { return iPrefLevels; }
		public void addPrefLevel(IdName p) { iPrefLevels.add(p); }
	}

	public static class DistTypeInfo implements IsSerializable {
		private Long iId;
		private String iReference;
		private String iLabel;
		private String iAbbreviation;
		private String iDescr;
		private boolean iInstructorPref;
		private boolean iExamPref;
		private boolean iSurvey;
		private boolean iVisible;
		private List<Long> iAllowedPrefIds = new ArrayList<Long>();
		private List<Long> iDepartmentIds = new ArrayList<Long>();

		public DistTypeInfo() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getReference() { return iReference; }
		public void setReference(String reference) { iReference = reference; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
		public String getAbbreviation() { return iAbbreviation; }
		public void setAbbreviation(String abbreviation) { iAbbreviation = abbreviation; }
		public String getDescr() { return iDescr; }
		public void setDescr(String descr) { iDescr = descr; }
		public boolean isInstructorPref() { return iInstructorPref; }
		public void setInstructorPref(boolean instructorPref) { iInstructorPref = instructorPref; }
		public boolean isExamPref() { return iExamPref; }
		public void setExamPref(boolean examPref) { iExamPref = examPref; }
		public boolean isSurvey() { return iSurvey; }
		public void setSurvey(boolean survey) { iSurvey = survey; }
		public boolean isVisible() { return iVisible; }
		public void setVisible(boolean visible) { iVisible = visible; }
		public List<Long> getAllowedPrefIds() { return iAllowedPrefIds; }
		public void addAllowedPrefId(Long id) { iAllowedPrefIds.add(id); }
		public List<Long> getDepartmentIds() { return iDepartmentIds; }
		public void addDepartmentId(Long id) { iDepartmentIds.add(id); }
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
