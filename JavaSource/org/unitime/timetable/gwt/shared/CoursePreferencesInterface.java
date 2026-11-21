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

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Time and room preferences of a scheduling subpart or a class, shared by the
 * migrated Scheduling Subpart Edit and Class Edit pages (legacy
 * {@code PreferencesAction2} / {@code preferencesEdit2.jspf}). Carried as an
 * optional {@code preferences} field on the existing edit responses and update
 * requests: when an update request leaves it {@code null}, preferences are not
 * touched (backward compatible with the previous payload shape).
 * <p>
 * Preference levels are exchanged as PreferenceLevel prolog codes
 * ({@code R, -2, -1, 0, 1, 2, P}; {@code N} = not available, read-only).
 *
 * @author Angular migration
 */
public class CoursePreferencesInterface implements IsSerializable {

	/** Load-side preference state (options + current rows). */
	public static class PreferencesData implements IsSerializable {
		private boolean iAllowHardTime = true;
		private boolean iAllowHardRoom = true;
		private String iInheritedFrom;
		private List<TimePatternGrid> iTimePatternOptions = new ArrayList<TimePatternGrid>();
		private List<TimePatternGrid> iTimePrefs = new ArrayList<TimePatternGrid>();
		private List<IdLabel> iRoomOptions = new ArrayList<IdLabel>();
		private List<IdLabel> iBuildingOptions = new ArrayList<IdLabel>();
		private List<IdLabel> iRoomGroupOptions = new ArrayList<IdLabel>();
		private List<IdLabel> iRoomFeatureOptions = new ArrayList<IdLabel>();
		private List<IdLabel> iRoomIndexOptions = new ArrayList<IdLabel>();
		private List<PrefRow> iRoomPrefs = new ArrayList<PrefRow>();
		private List<PrefRow> iBuildingPrefs = new ArrayList<PrefRow>();
		private List<PrefRow> iRoomGroupPrefs = new ArrayList<PrefRow>();
		private List<PrefRow> iRoomFeaturePrefs = new ArrayList<PrefRow>();

		public PreferencesData() {}

		public boolean isAllowHardTime() { return iAllowHardTime; }
		public void setAllowHardTime(boolean allowHardTime) { iAllowHardTime = allowHardTime; }

		public boolean isAllowHardRoom() { return iAllowHardRoom; }
		public void setAllowHardRoom(boolean allowHardRoom) { iAllowHardRoom = allowHardRoom; }

		/** Label of the preference group inherited preferences come from (e.g. the parent subpart), or null. */
		public String getInheritedFrom() { return iInheritedFrom; }
		public void setInheritedFrom(String inheritedFrom) { iInheritedFrom = inheritedFrom; }

		public List<TimePatternGrid> getTimePatternOptions() { return iTimePatternOptions; }
		public void addTimePatternOption(TimePatternGrid option) { iTimePatternOptions.add(option); }

		public List<TimePatternGrid> getTimePrefs() { return iTimePrefs; }
		public void addTimePref(TimePatternGrid grid) { iTimePrefs.add(grid); }

		public List<IdLabel> getRoomOptions() { return iRoomOptions; }
		public List<IdLabel> getBuildingOptions() { return iBuildingOptions; }
		public List<IdLabel> getRoomGroupOptions() { return iRoomGroupOptions; }
		public List<IdLabel> getRoomFeatureOptions() { return iRoomFeatureOptions; }
		public List<IdLabel> getRoomIndexOptions() { return iRoomIndexOptions; }

		public List<PrefRow> getRoomPrefs() { return iRoomPrefs; }
		public List<PrefRow> getBuildingPrefs() { return iBuildingPrefs; }
		public List<PrefRow> getRoomGroupPrefs() { return iRoomGroupPrefs; }
		public List<PrefRow> getRoomFeaturePrefs() { return iRoomFeaturePrefs; }
	}

	/** Save-side preference state; replaces the owner's time / room / building / room group / room feature preferences. */
	public static class PreferencesUpdate implements IsSerializable {
		private List<TimePatternGrid> iTimePrefs = new ArrayList<TimePatternGrid>();
		private List<PrefRow> iRoomPrefs = new ArrayList<PrefRow>();
		private List<PrefRow> iBuildingPrefs = new ArrayList<PrefRow>();
		private List<PrefRow> iRoomGroupPrefs = new ArrayList<PrefRow>();
		private List<PrefRow> iRoomFeaturePrefs = new ArrayList<PrefRow>();

		public PreferencesUpdate() {}

		public List<TimePatternGrid> getTimePrefs() { return iTimePrefs; }
		public void setTimePrefs(List<TimePatternGrid> timePrefs) { iTimePrefs = timePrefs; }

		public List<PrefRow> getRoomPrefs() { return iRoomPrefs; }
		public void setRoomPrefs(List<PrefRow> roomPrefs) { iRoomPrefs = roomPrefs; }

		public List<PrefRow> getBuildingPrefs() { return iBuildingPrefs; }
		public void setBuildingPrefs(List<PrefRow> buildingPrefs) { iBuildingPrefs = buildingPrefs; }

		public List<PrefRow> getRoomGroupPrefs() { return iRoomGroupPrefs; }
		public void setRoomGroupPrefs(List<PrefRow> roomGroupPrefs) { iRoomGroupPrefs = roomGroupPrefs; }

		public List<PrefRow> getRoomFeaturePrefs() { return iRoomFeaturePrefs; }
		public void setRoomFeaturePrefs(List<PrefRow> roomFeaturePrefs) { iRoomFeaturePrefs = roomFeaturePrefs; }
	}

	/**
	 * A time pattern with its preference grid. As an option it is a blank (all
	 * neutral) template; as a selected time preference it carries the cell levels.
	 * Cells are row-major: {@code prefs[day * times.size() + time]}. Exact-time
	 * and pattern-less time preferences have no grid and keep the raw
	 * {@code preference} string untouched.
	 */
	public static class TimePatternGrid implements IsSerializable {
		private Long iTimePatternId;
		private String iName;
		private boolean iExactTime;
		private boolean iInherited;
		private List<String> iDays = new ArrayList<String>();
		private List<String> iTimes = new ArrayList<String>();
		private List<String> iPrefs = new ArrayList<String>();
		private String iPreference;

		public TimePatternGrid() {}

		public Long getTimePatternId() { return iTimePatternId; }
		public void setTimePatternId(Long timePatternId) { iTimePatternId = timePatternId; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public boolean isExactTime() { return iExactTime; }
		public void setExactTime(boolean exactTime) { iExactTime = exactTime; }

		public boolean isInherited() { return iInherited; }
		public void setInherited(boolean inherited) { iInherited = inherited; }

		public List<String> getDays() { return iDays; }
		public void setDays(List<String> days) { iDays = days; }

		public List<String> getTimes() { return iTimes; }
		public void setTimes(List<String> times) { iTimes = times; }

		public List<String> getPrefs() { return iPrefs; }
		public void setPrefs(List<String> prefs) { iPrefs = prefs; }

		public String getPreference() { return iPreference; }
		public void setPreference(String preference) { iPreference = preference; }
	}

	/** One room / building / room group / room feature preference row. */
	public static class PrefRow implements IsSerializable {
		private Long iId;
		private String iLevel;
		private Integer iRoomIndex;
		private boolean iInherited;

		public PrefRow() {}
		public PrefRow(Long id, String level, Integer roomIndex, boolean inherited) {
			iId = id; iLevel = level; iRoomIndex = roomIndex; iInherited = inherited;
		}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		/** PreferenceLevel prolog code. */
		public String getLevel() { return iLevel; }
		public void setLevel(String level) { iLevel = level; }

		/** Room index for multi-room classes, null = all rooms. */
		public Integer getRoomIndex() { return iRoomIndex; }
		public void setRoomIndex(Integer roomIndex) { iRoomIndex = roomIndex; }

		public boolean isInherited() { return iInherited; }
		public void setInherited(boolean inherited) { iInherited = inherited; }
	}

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
}
