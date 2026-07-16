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
 * Create / Edit protocol for the legacy Struts Date Pattern (datePatternEdit.action)
 * and Time Pattern (timePatternEdit.action) pages, unified behind one command.
 * The {@code kind} discriminator (DATE|TIME) selects the entity. One request
 * drives LOAD (return all patterns of the current academic session plus the
 * available type options and permission flags), SAVE (merge-on-update the
 * descriptive fields of one existing pattern) and DELETE (remove one pattern).
 *
 * The backend ({@code PatternEditBackend}) gates every operation with
 * {@link org.unitime.timetable.security.rights.Right#DatePatterns} /
 * {@code Right.TimePatterns} (Session qualified) and returns the refreshed list.
 *
 * DEFERRED (edited only on the legacy JSP page, not exposed here):
 *   - DatePattern day bitmap (pattern / offset / numberOfWeeks) and the
 *     department / parent / child (pattern-set) associations,
 *   - TimePattern day / start-slot / exact-time grid and department
 *     associations.
 * Because those complex bit-encoded / relational sub-parts are deferred, SAVE
 * only updates the descriptive fields of existing patterns (creation of a new
 * pattern requires the deferred editor to define the bitmap / grid).
 *
 * @author Angular migration
 */
public class PatternEditInterface implements IsSerializable {

	public static enum Operation implements IsSerializable {
		LOAD,
		SAVE,
		DELETE
	}

	public static enum Kind implements IsSerializable {
		DATE,
		TIME
	}

	/** One selectable type option (ordinal + localized label). */
	public static class PatternTypeOption implements IsSerializable {
		private int iId;
		private String iLabel;

		public PatternTypeOption() {}
		public PatternTypeOption(int id, String label) { iId = id; iLabel = label; }

		public int getId() { return iId; }
		public void setId(int id) { iId = id; }

		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	/**
	 * One pattern row. Shared shape for both kinds; the time-only numeric fields
	 * (nrMeetings / minPerMtg / slotsPerMtg / breakTime) and the date-only
	 * read-only preview / weeks fields are populated only for the matching kind.
	 */
	public static class PatternRecord implements IsSerializable {
		private Long iId;
		private String iName;
		private Integer iType;
		private String iTypeLabel;
		private boolean iVisible = true;
		private boolean iUsed = false;
		private boolean iDefault = false;

		// Time pattern only
		private Integer iNrMeetings;
		private Integer iMinPerMtg;
		private Integer iSlotsPerMtg;
		private Integer iBreakTime;

		// Date pattern only (read-only, informational)
		private String iPatternPreview;
		private String iNumberOfWeeks;

		public PatternRecord() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public Integer getType() { return iType; }
		public void setType(Integer type) { iType = type; }

		public String getTypeLabel() { return iTypeLabel; }
		public void setTypeLabel(String typeLabel) { iTypeLabel = typeLabel; }

		public boolean isVisible() { return iVisible; }
		public void setVisible(boolean visible) { iVisible = visible; }

		public boolean isUsed() { return iUsed; }
		public void setUsed(boolean used) { iUsed = used; }

		public boolean isDefault() { return iDefault; }
		public void setDefault(boolean isDefault) { iDefault = isDefault; }

		public Integer getNrMeetings() { return iNrMeetings; }
		public void setNrMeetings(Integer nrMeetings) { iNrMeetings = nrMeetings; }

		public Integer getMinPerMtg() { return iMinPerMtg; }
		public void setMinPerMtg(Integer minPerMtg) { iMinPerMtg = minPerMtg; }

		public Integer getSlotsPerMtg() { return iSlotsPerMtg; }
		public void setSlotsPerMtg(Integer slotsPerMtg) { iSlotsPerMtg = slotsPerMtg; }

		public Integer getBreakTime() { return iBreakTime; }
		public void setBreakTime(Integer breakTime) { iBreakTime = breakTime; }

		public String getPatternPreview() { return iPatternPreview; }
		public void setPatternPreview(String patternPreview) { iPatternPreview = patternPreview; }

		public String getNumberOfWeeks() { return iNumberOfWeeks; }
		public void setNumberOfWeeks(String numberOfWeeks) { iNumberOfWeeks = numberOfWeeks; }
	}

	public static class PatternEditRequest implements GwtRpcRequest<PatternEditResponse> {
		private Kind iKind = Kind.DATE;
		private Operation iOperation = Operation.LOAD;
		private PatternRecord iRecord;

		public PatternEditRequest() {}

		public Kind getKind() { return iKind; }
		public void setKind(Kind kind) { iKind = kind; }

		public Operation getOperation() { return iOperation; }
		public void setOperation(Operation operation) { iOperation = operation; }

		public PatternRecord getRecord() { return iRecord; }
		public void setRecord(PatternRecord record) { iRecord = record; }

		@Override
		public String toString() {
			return "PatternEdit[" + iKind + "," + iOperation
					+ (iRecord == null ? "" : "," + iRecord.getName()) + "]";
		}
	}

	public static class PatternEditResponse implements GwtRpcResponse {
		private Kind iKind = Kind.DATE;
		private boolean iEditable = false;
		private boolean iAddable = false;
		private boolean iDeletable = false;
		private List<PatternRecord> iRecords = new ArrayList<PatternRecord>();
		private List<PatternTypeOption> iTypes = new ArrayList<PatternTypeOption>();

		public PatternEditResponse() {}

		public Kind getKind() { return iKind; }
		public void setKind(Kind kind) { iKind = kind; }

		public boolean isEditable() { return iEditable; }
		public void setEditable(boolean editable) { iEditable = editable; }

		public boolean isAddable() { return iAddable; }
		public void setAddable(boolean addable) { iAddable = addable; }

		public boolean isDeletable() { return iDeletable; }
		public void setDeletable(boolean deletable) { iDeletable = deletable; }

		public List<PatternRecord> getRecords() { return iRecords; }
		public void addRecord(PatternRecord record) { iRecords.add(record); }

		public List<PatternTypeOption> getTypes() { return iTypes; }
		public void addType(PatternTypeOption type) { iTypes.add(type); }
	}
}
