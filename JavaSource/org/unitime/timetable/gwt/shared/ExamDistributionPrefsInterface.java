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
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Create / list / edit protocol for the legacy examDistributionPrefs.action
 * (Examination Distribution Preferences) Struts page. One request drives every
 * operation:
 * <ul>
 *   <li>{@code LOAD} — the examination distribution preferences for the selected
 *       examination type (and optional subject-area / course-number filter),
 *       projected to string rows, plus the applicable examination types, subject
 *       areas, distribution types and preference levels;</li>
 *   <li>{@code COURSES} — the offered courses of a subject area (add/edit cascade);</li>
 *   <li>{@code EXAMS} — the examinations of a course of the selected type (cascade);</li>
 *   <li>{@code DETAIL} — the editable detail of one preference (type, level, the
 *       ordered list of examinations it groups);</li>
 *   <li>{@code SAVE} — create or update a preference (type, level, examinations),
 *       a faithful port of {@code ExamDistributionPrefsAction.doAddOrUpdate};</li>
 *   <li>{@code DELETE} — remove a preference ({@code doDelete}).</li>
 * </ul>
 * Reuses {@link SimpleListInterface.Row} for the tabular rows. Gated by
 * {@link org.unitime.timetable.security.rights.Right#ExaminationDistributionPreferences}
 * (plus the per-preference add / edit / delete / detail rights). Additive:
 * introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
public class ExamDistributionPrefsInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD, COURSES, EXAMS, DETAIL, SAVE, DELETE
	}

	/** Generic id + label pair (exam types, subject areas, courses, exams). */
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

	/** A selectable distribution type; iAllowed carries the allowed preference-level chars. */
	public static class DistributionTypeInfo implements IsSerializable {
		private Long iId;
		private String iLabel;
		private String iAllowed;
		private String iDescription;

		public DistributionTypeInfo() {}
		public DistributionTypeInfo(Long id, String label, String allowed, String description) {
			iId = id; iLabel = label; iAllowed = allowed; iDescription = description;
		}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }

		public String getAllowed() { return iAllowed; }
		public void setAllowed(String allowed) { iAllowed = allowed; }

		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }
	}

	/** A selectable preference level; iChar is the level's single-character prolog code. */
	public static class PrefLevelInfo implements IsSerializable {
		private int iId;
		private String iName;
		private String iChar;

		public PrefLevelInfo() {}
		public PrefLevelInfo(int id, String name, String ch) { iId = id; iName = name; iChar = ch; }

		public int getId() { return iId; }
		public void setId(int id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getChar() { return iChar; }
		public void setChar(String ch) { iChar = ch; }
	}

	/** One examination row in the add/edit list (the subject→course→exam cascade). */
	public static class ExamLine implements IsSerializable {
		private Long iSubjectAreaId;
		private Long iCourseId;
		private Long iExamId;
		private String iExamLabel;

		public ExamLine() {}

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long id) { iSubjectAreaId = id; }

		public Long getCourseId() { return iCourseId; }
		public void setCourseId(Long id) { iCourseId = id; }

		public Long getExamId() { return iExamId; }
		public void setExamId(Long id) { iExamId = id; }

		public String getExamLabel() { return iExamLabel; }
		public void setExamLabel(String label) { iExamLabel = label; }
	}

	/** The editable detail of a single examination distribution preference. */
	public static class DistPrefRecord implements IsSerializable {
		private Long iId;
		private Long iExamTypeId;
		private Long iDistributionTypeId;
		private Integer iPrefLevelId;
		private String iDescription;
		private List<ExamLine> iExams = new ArrayList<ExamLine>();

		public DistPrefRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long id) { iExamTypeId = id; }

		public Long getDistributionTypeId() { return iDistributionTypeId; }
		public void setDistributionTypeId(Long id) { iDistributionTypeId = id; }

		public Integer getPrefLevelId() { return iPrefLevelId; }
		public void setPrefLevelId(Integer id) { iPrefLevelId = id; }

		public String getDescription() { return iDescription; }
		public void setDescription(String description) { iDescription = description; }

		public List<ExamLine> getExams() { return iExams; }
		public void setExams(List<ExamLine> exams) { iExams = exams; }
		public void addExam(ExamLine exam) { iExams.add(exam); }
	}

	public static class ExamDistributionPrefsRequest implements GwtRpcRequest<ExamDistributionPrefsResponse> {
		private Operation iOperation = Operation.LOAD;
		private Long iExamTypeId;
		private Long iSubjectAreaId;
		private String iCourseNbr;
		private Long iId;
		private Long iLookupSubjectAreaId;
		private Long iLookupCourseId;
		private DistPrefRecord iRecord;

		public ExamDistributionPrefsRequest() {}

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long id) { iSubjectAreaId = id; }

		public String getCourseNbr() { return iCourseNbr; }
		public void setCourseNbr(String courseNbr) { iCourseNbr = courseNbr; }

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public Long getLookupSubjectAreaId() { return iLookupSubjectAreaId; }
		public void setLookupSubjectAreaId(Long id) { iLookupSubjectAreaId = id; }

		public Long getLookupCourseId() { return iLookupCourseId; }
		public void setLookupCourseId(Long id) { iLookupCourseId = id; }

		public DistPrefRecord getRecord() { return iRecord; }
		public void setRecord(DistPrefRecord record) { iRecord = record; }

		@Override
		public String toString() { return "ExamDistributionPrefs[" + iOperation + (iId == null ? "" : ",id=" + iId) + "]"; }
	}

	public static class ExamDistributionPrefsResponse implements GwtRpcResponse {
		private String iTitle;
		private Long iExamTypeId;
		private Long iSubjectAreaId;
		private String iCourseNbr;
		private boolean iCanAdd = false;
		private List<IdLabel> iExamTypes = new ArrayList<IdLabel>();
		private List<IdLabel> iSubjectAreas = new ArrayList<IdLabel>();
		private List<IdLabel> iCourses = new ArrayList<IdLabel>();
		private List<IdLabel> iExams = new ArrayList<IdLabel>();
		private List<String> iColumns = new ArrayList<String>();
		private List<Row> iRows = new ArrayList<Row>();
		private List<Long> iEditableIds = new ArrayList<Long>();
		private List<Long> iDeletableIds = new ArrayList<Long>();
		private DistPrefRecord iRecord;
		private List<DistributionTypeInfo> iDistributionTypes = new ArrayList<DistributionTypeInfo>();
		private List<PrefLevelInfo> iPrefLevels = new ArrayList<PrefLevelInfo>();

		public ExamDistributionPrefsResponse() {}

		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }

		public Long getExamTypeId() { return iExamTypeId; }
		public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long id) { iSubjectAreaId = id; }

		public String getCourseNbr() { return iCourseNbr; }
		public void setCourseNbr(String courseNbr) { iCourseNbr = courseNbr; }

		public boolean isCanAdd() { return iCanAdd; }
		public void setCanAdd(boolean canAdd) { iCanAdd = canAdd; }

		public List<IdLabel> getExamTypes() { return iExamTypes; }
		public void addExamType(IdLabel type) { iExamTypes.add(type); }

		public List<IdLabel> getSubjectAreas() { return iSubjectAreas; }
		public void addSubjectArea(IdLabel area) { iSubjectAreas.add(area); }

		public List<IdLabel> getCourses() { return iCourses; }
		public void addCourse(IdLabel course) { iCourses.add(course); }

		public List<IdLabel> getExams() { return iExams; }
		public void addExam(IdLabel exam) { iExams.add(exam); }

		public List<String> getColumns() { return iColumns; }
		public void addColumn(String column) { iColumns.add(column); }

		public List<Row> getRows() { return iRows; }
		public Row addRow(Long id) {
			Row row = new Row();
			row.setId(id);
			iRows.add(row);
			return row;
		}

		public List<Long> getEditableIds() { return iEditableIds; }
		public void addEditableId(Long id) { iEditableIds.add(id); }

		public List<Long> getDeletableIds() { return iDeletableIds; }
		public void addDeletableId(Long id) { iDeletableIds.add(id); }

		public DistPrefRecord getRecord() { return iRecord; }
		public void setRecord(DistPrefRecord record) { iRecord = record; }

		public List<DistributionTypeInfo> getDistributionTypes() { return iDistributionTypes; }
		public void addDistributionType(DistributionTypeInfo type) { iDistributionTypes.add(type); }

		public List<PrefLevelInfo> getPrefLevels() { return iPrefLevels; }
		public void addPrefLevel(PrefLevelInfo level) { iPrefLevels.add(level); }
	}
}
