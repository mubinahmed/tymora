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
 * Load / Save protocol for the legacy Curriculum Projection Rules
 * (curprojrules) GWT page. The classic CurriculaService.loadProjectionRules
 * returns HashMap&lt;AcademicArea, HashMap&lt;Major, HashMap&lt;Classification,
 * Number[]&gt;&gt;&gt; whose object keys the Gson-based command facade cannot
 * serialize. This interface instead exposes a FLAT, string/number row model:
 * one row per (academic area, major, classification) tuple that has last-like
 * enrollment, carrying the stored projection fraction (nullable) alongside the
 * last-like enrollment count.
 *
 * The dummy "Default" major (majorId == -1, empty code) carries the aggregate
 * last-like enrollment summed across every major of the area, matching the
 * legacy page. Projection is stored as a fraction (1.0 == 100%). The backend
 * (CurriculumProjectionRulesEditBackend) gates LOAD with
 * Right.CurriculumProjectionRulesDetail and SAVE with
 * Right.CurriculumProjectionRulesEdit and returns the refreshed rows.
 *
 * @author Angular migration
 */
public class CurriculumProjectionRulesEditInterface implements IsSerializable {

	/** Sentinel major id used for the aggregate "Default" (no-major) row. */
	public static final long DEFAULT_MAJOR_ID = -1L;

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE
	}

	public static class ProjectionRuleRow implements IsSerializable {
		private Long iAcademicAreaId;
		private String iAcademicAreaCode;
		private String iAcademicAreaLabel;
		private Long iMajorId;
		private String iMajorCode;
		private String iMajorLabel;
		private Long iClassificationId;
		private String iClassificationCode;
		private String iClassificationLabel;
		private Float iProjection;
		private Integer iEnrollment;

		public ProjectionRuleRow() {}

		public Long getAcademicAreaId() { return iAcademicAreaId; }
		public void setAcademicAreaId(Long academicAreaId) { iAcademicAreaId = academicAreaId; }

		public String getAcademicAreaCode() { return iAcademicAreaCode; }
		public void setAcademicAreaCode(String academicAreaCode) { iAcademicAreaCode = academicAreaCode; }

		public String getAcademicAreaLabel() { return iAcademicAreaLabel; }
		public void setAcademicAreaLabel(String academicAreaLabel) { iAcademicAreaLabel = academicAreaLabel; }

		public Long getMajorId() { return iMajorId; }
		public void setMajorId(Long majorId) { iMajorId = majorId; }

		public String getMajorCode() { return iMajorCode; }
		public void setMajorCode(String majorCode) { iMajorCode = majorCode; }

		public String getMajorLabel() { return iMajorLabel; }
		public void setMajorLabel(String majorLabel) { iMajorLabel = majorLabel; }

		public Long getClassificationId() { return iClassificationId; }
		public void setClassificationId(Long classificationId) { iClassificationId = classificationId; }

		public String getClassificationCode() { return iClassificationCode; }
		public void setClassificationCode(String classificationCode) { iClassificationCode = classificationCode; }

		public String getClassificationLabel() { return iClassificationLabel; }
		public void setClassificationLabel(String classificationLabel) { iClassificationLabel = classificationLabel; }

		/** Projection fraction (1.0 == 100%); null means no rule stored. */
		public Float getProjection() { return iProjection; }
		public void setProjection(Float projection) { iProjection = projection; }

		/** Last-like enrollment count for this tuple. */
		public Integer getEnrollment() { return iEnrollment; }
		public void setEnrollment(Integer enrollment) { iEnrollment = enrollment; }

		public boolean isDefaultMajor() { return iMajorId != null && iMajorId.longValue() == DEFAULT_MAJOR_ID; }
	}

	public static class CurriculumProjectionRulesEditRequest implements GwtRpcRequest<CurriculumProjectionRulesEditResponse> {
		private Operation iOperation = Operation.LOAD;
		private List<ProjectionRuleRow> iRows = new ArrayList<ProjectionRuleRow>();

		public CurriculumProjectionRulesEditRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public List<ProjectionRuleRow> getRows() { return iRows; }
		public void setRows(List<ProjectionRuleRow> rows) { iRows = rows; }
		public void addRow(ProjectionRuleRow row) { iRows.add(row); }

		@Override
		public String toString() {
			return "CurriculumProjectionRulesEdit[" + iOperation + "," + (iRows == null ? 0 : iRows.size()) + " row(s)]";
		}
	}

	public static class CurriculumProjectionRulesEditResponse implements GwtRpcResponse {
		private boolean iEditable = false;
		private List<ProjectionRuleRow> iRows = new ArrayList<ProjectionRuleRow>();

		public CurriculumProjectionRulesEditResponse() {}

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public List<ProjectionRuleRow> getRows() { return iRows; }
		public void addRow(ProjectionRuleRow row) { iRows.add(row); }
	}
}
