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
 * SAVE now edits, in addition to the descriptive fields:
 *   - the DatePattern day bitmap (via {@code PatternRecord.offeredDays} +
 *     {@code CalendarDate}); the backend rebuilds pattern/offset exactly like
 *     {@code DatePattern.setPatternAndOffset}.
 *   - the TimePattern day / start-slot grid (via {@code PatternRecord.dayCodes}
 *     and {@code startSlots}); the backend rewrites the TimePatternDays /
 *     TimePatternTime sets like {@code TimePatternEditForm.update}.
 *
 * STILL DEFERRED (edited only on the legacy JSP page): DatePattern numberOfWeeks,
 * the department / parent / child (pattern-set) associations, and creating a
 * brand-new pattern.
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
	 * One session calendar date (DATE kind). {@code key} is the running day index
	 * (equal to {@code Session.getDayOfYear(day,month)}), stable across LOAD/SAVE
	 * and used as the toggle id in the date-bitmap grid. {@code month} is the
	 * 0-based calendar month, {@code day} the day of month, {@code dayOfWeek}
	 * 0=Mon..6=Sun, {@code holiday} the session holiday code (0 none, 1 holiday,
	 * 2 break) for shading.
	 */
	public static class CalendarDate implements IsSerializable {
		private int iKey;
		private int iYear;
		private int iMonth;
		private int iDay;
		private int iDayOfWeek;
		private int iHoliday;

		public CalendarDate() {}

		public int getKey() { return iKey; }
		public void setKey(int key) { iKey = key; }
		public int getYear() { return iYear; }
		public void setYear(int year) { iYear = year; }
		public int getMonth() { return iMonth; }
		public void setMonth(int month) { iMonth = month; }
		public int getDay() { return iDay; }
		public void setDay(int day) { iDay = day; }
		public int getDayOfWeek() { return iDayOfWeek; }
		public void setDayOfWeek(int dow) { iDayOfWeek = dow; }
		public int getHoliday() { return iHoliday; }
		public void setHoliday(int holiday) { iHoliday = holiday; }
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

		// Date pattern day-bitmap grid. On LOAD: the set of CalendarDate keys that
		// are offered ('1'). On SAVE: the edited set (null => do not touch the
		// stored pattern/offset). iPatternEditable indicates the grid may be edited.
		private List<Integer> iOfferedDays;
		private boolean iPatternEditable = false;

		// Time pattern day/time grid. iDayCodes: raw day-code bitmasks (one per
		// meeting-day combination); iStartSlots: raw start slots. On SAVE null =>
		// do not touch the stored days/times. iGridEditable indicates the grid may
		// be edited (mirrors TimePattern.isEditable()).
		private List<Integer> iDayCodes;
		private List<Integer> iStartSlots;
		private boolean iGridEditable = false;

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

		public List<Integer> getOfferedDays() { return iOfferedDays; }
		public void setOfferedDays(List<Integer> offeredDays) { iOfferedDays = offeredDays; }
		public void addOfferedDay(int key) {
			if (iOfferedDays == null) iOfferedDays = new ArrayList<Integer>();
			iOfferedDays.add(key);
		}

		public boolean isPatternEditable() { return iPatternEditable; }
		public void setPatternEditable(boolean editable) { iPatternEditable = editable; }

		public List<Integer> getDayCodes() { return iDayCodes; }
		public void setDayCodes(List<Integer> dayCodes) { iDayCodes = dayCodes; }
		public void addDayCode(int dayCode) {
			if (iDayCodes == null) iDayCodes = new ArrayList<Integer>();
			iDayCodes.add(dayCode);
		}

		public List<Integer> getStartSlots() { return iStartSlots; }
		public void setStartSlots(List<Integer> startSlots) { iStartSlots = startSlots; }
		public void addStartSlot(int startSlot) {
			if (iStartSlots == null) iStartSlots = new ArrayList<Integer>();
			iStartSlots.add(startSlot);
		}

		public boolean isGridEditable() { return iGridEditable; }
		public void setGridEditable(boolean editable) { iGridEditable = editable; }
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

		// DATE kind: the shared session calendar (all date patterns of a session
		// span the same date range); each record references these by CalendarDate.key.
		private List<CalendarDate> iSessionDates = new ArrayList<CalendarDate>();

		// TIME kind: encoding constants supplied by the backend so the client never
		// hard-codes them. iDayCodes are the 7 day-code bitmasks in iDayNames order.
		private int[] iDayCodes;
		private List<String> iDayNames = new ArrayList<String>();
		private int iSlotLengthMin;
		private int iFirstSlotTimeMin;
		private int iSlotsPerDay;

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

		public List<CalendarDate> getSessionDates() { return iSessionDates; }
		public void addSessionDate(CalendarDate date) { iSessionDates.add(date); }

		public int[] getDayCodes() { return iDayCodes; }
		public void setDayCodes(int[] dayCodes) { iDayCodes = dayCodes; }

		public List<String> getDayNames() { return iDayNames; }
		public void addDayName(String name) { iDayNames.add(name); }

		public int getSlotLengthMin() { return iSlotLengthMin; }
		public void setSlotLengthMin(int v) { iSlotLengthMin = v; }

		public int getFirstSlotTimeMin() { return iFirstSlotTimeMin; }
		public void setFirstSlotTimeMin(int v) { iFirstSlotTimeMin = v; }

		public int getSlotsPerDay() { return iSlotsPerDay; }
		public void setSlotsPerDay(int v) { iSlotsPerDay = v; }
	}
}
