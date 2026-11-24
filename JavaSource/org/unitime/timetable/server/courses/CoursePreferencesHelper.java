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
package org.unitime.timetable.server.courses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.shared.CoursePreferencesInterface.IdLabel;
import org.unitime.timetable.gwt.shared.CoursePreferencesInterface.PrefRow;
import org.unitime.timetable.gwt.shared.CoursePreferencesInterface.PreferencesData;
import org.unitime.timetable.gwt.shared.CoursePreferencesInterface.PreferencesUpdate;
import org.unitime.timetable.gwt.shared.CoursePreferencesInterface.TimePatternGrid;
import org.unitime.timetable.model.Building;
import org.unitime.timetable.model.BuildingPref;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Preference;
import org.unitime.timetable.model.PreferenceGroup;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.RoomFeature;
import org.unitime.timetable.model.RoomFeaturePref;
import org.unitime.timetable.model.RoomGroup;
import org.unitime.timetable.model.RoomGroupPref;
import org.unitime.timetable.model.RoomPref;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.TimePattern;
import org.unitime.timetable.model.TimePatternModel;
import org.unitime.timetable.model.TimePref;
import org.unitime.timetable.model.dao.BuildingDAO;
import org.unitime.timetable.model.dao.LocationDAO;
import org.unitime.timetable.model.dao.RoomFeatureDAO;
import org.unitime.timetable.model.dao.RoomGroupDAO;
import org.unitime.timetable.model.dao.TimePatternDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.duration.DurationModel;

/**
 * Shared load / save of the time and room preferences of a scheduling subpart or
 * a class for the migrated Scheduling Subpart Edit and Class Edit pages. Mirrors
 * {@code PreferencesAction2.initPrefs / generateTimePatternGrids} (load: effective
 * preferences, i.e. including the ones inherited from the parent subpart) and
 * {@code PreferencesAction2.doUpdate} (save: replace the owner's TIME, ROOM,
 * BUILDING, ROOM_GROUP and ROOM_FEATURE preferences; a row identical to an
 * inherited one is not stored, an inherited one that was removed is overridden by
 * a neutral copy). Date pattern preferences are intentionally left untouched.
 *
 * @author Angular migration
 */
public class CoursePreferencesHelper {

	private static final Preference.Type[] TYPES = new Preference.Type[] {
		Preference.Type.TIME, Preference.Type.ROOM, Preference.Type.ROOM_FEATURE, Preference.Type.ROOM_GROUP, Preference.Type.BUILDING
	};

	/* ------------------------------------------------------------------ load */

	/**
	 * @param pg the subpart or class
	 * @param minutes subpart minutes per week
	 * @param datePattern effective date pattern (time pattern applicability)
	 * @param durationModel offering configuration's duration model
	 * @param department managing department
	 * @param maxRooms number of rooms (room index options are offered when &gt; 1)
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PreferencesData load(PreferenceGroup pg, SessionContext context, int minutes, DatePattern datePattern,
			DurationModel durationModel, Department department, int maxRooms) {
		PreferencesData data = new PreferencesData();
		data.setAllowHardTime(context.hasPermission(pg, Right.CanUseHardTimePrefs));
		data.setAllowHardRoom(context.hasPermission(pg, Right.CanUseHardRoomPrefs));
		data.setInheritedFrom(inheritedFrom(pg));

		// Time pattern options (legacy TimePattern.findApplicable; exact-time patterns are not offered)
		try {
			for (TimePattern tp : TimePattern.findApplicable(context.getUser(), minutes, datePattern, durationModel, false, department)) {
				if (tp.isExactTime()) continue;
				data.addTimePatternOption(grid(tp, tp.getTimePatternModel(), false));
			}
		} catch (Exception e) {}

		// Selected time patterns + grids (effective preferences, no duration fix — as legacy init)
		List timePrefs = new ArrayList(pg.effectivePreferences(TimePref.class, false));
		Collections.sort(timePrefs);
		for (Object o : timePrefs) {
			TimePref tp = (TimePref) o;
			TimePattern pattern = tp.getTimePattern();
			boolean inherited = !isOwn(tp, pg);
			if (pattern == null || pattern.isExactTime()) {
				TimePatternGrid g = new TimePatternGrid();
				g.setTimePatternId(pattern == null ? null : pattern.getUniqueId());
				g.setName(pattern == null ? "Default" : pattern.getName());
				g.setExactTime(true);
				g.setInherited(inherited);
				g.setPreference(tp.getPreference());
				data.addTimePref(g);
			} else {
				data.addTimePref(grid(pattern, tp.getTimePatternModel(), inherited));
			}
		}

		// Option lists (legacy LookupTables.setupRooms / setupBldgs / setupRoomGroups / setupRoomFeatures)
		Set<Long> roomIds = new HashSet<Long>(), bldgIds = new HashSet<Long>(), groupIds = new HashSet<Long>(), featureIds = new HashSet<Long>();
		try {
			for (Object o : pg.getAvailableRooms()) {
				Location l = (Location) o;
				if (roomIds.add(l.getUniqueId())) data.getRoomOptions().add(new IdLabel(l.getUniqueId(), l.getLabelWithCapacity()));
			}
		} catch (Exception e) {}
		try {
			for (Building b : pg.getAvailableBuildings())
				if (bldgIds.add(b.getUniqueId())) data.getBuildingOptions().add(new IdLabel(b.getUniqueId(), b.getAbbrName()));
		} catch (Exception e) {}
		try {
			for (Object o : pg.getAvailableRoomGroups()) {
				RoomGroup g = (RoomGroup) o;
				if (groupIds.add(g.getUniqueId())) data.getRoomGroupOptions().add(new IdLabel(g.getUniqueId(), g.getNameWithTitle()));
			}
		} catch (Exception e) {}
		try {
			for (Object o : pg.getAvailableRoomFeatures()) {
				RoomFeature f = (RoomFeature) o;
				if (featureIds.add(f.getUniqueId())) data.getRoomFeatureOptions().add(new IdLabel(f.getUniqueId(), f.getLabelWithType()));
			}
		} catch (Exception e) {}
		if (maxRooms > 1) {
			data.getRoomIndexOptions().add(new IdLabel(Long.valueOf(-1), "All Rooms"));
			for (int i = 0; i < maxRooms; i++)
				data.getRoomIndexOptions().add(new IdLabel(Long.valueOf(i), "Room " + (i + 1)));
		}

		// Current (effective) rows; make sure every referenced item is in its option list
		for (RoomPref p : (Set<RoomPref>) pg.effectivePreferences(RoomPref.class)) {
			if (p.getRoom() == null) continue;
			data.getRoomPrefs().add(new PrefRow(p.getRoom().getUniqueId(), p.getPrefLevel().getPrefProlog(), p.getRoomIndex(), !isOwn(p, pg)));
			if (roomIds.add(p.getRoom().getUniqueId())) data.getRoomOptions().add(new IdLabel(p.getRoom().getUniqueId(), p.getRoom().getLabelWithCapacity()));
		}
		for (BuildingPref p : (Set<BuildingPref>) pg.effectivePreferences(BuildingPref.class)) {
			if (p.getBuilding() == null) continue;
			data.getBuildingPrefs().add(new PrefRow(p.getBuilding().getUniqueId(), p.getPrefLevel().getPrefProlog(), p.getRoomIndex(), !isOwn(p, pg)));
			if (bldgIds.add(p.getBuilding().getUniqueId())) data.getBuildingOptions().add(new IdLabel(p.getBuilding().getUniqueId(), p.getBuilding().getAbbrName()));
		}
		for (RoomGroupPref p : (Set<RoomGroupPref>) pg.effectivePreferences(RoomGroupPref.class)) {
			if (p.getRoomGroup() == null) continue;
			data.getRoomGroupPrefs().add(new PrefRow(p.getRoomGroup().getUniqueId(), p.getPrefLevel().getPrefProlog(), p.getRoomIndex(), !isOwn(p, pg)));
			if (groupIds.add(p.getRoomGroup().getUniqueId())) data.getRoomGroupOptions().add(new IdLabel(p.getRoomGroup().getUniqueId(), p.getRoomGroup().getNameWithTitle()));
		}
		for (RoomFeaturePref p : (Set<RoomFeaturePref>) pg.effectivePreferences(RoomFeaturePref.class)) {
			if (p.getRoomFeature() == null) continue;
			data.getRoomFeaturePrefs().add(new PrefRow(p.getRoomFeature().getUniqueId(), p.getPrefLevel().getPrefProlog(), p.getRoomIndex(), !isOwn(p, pg)));
			if (featureIds.add(p.getRoomFeature().getUniqueId())) data.getRoomFeatureOptions().add(new IdLabel(p.getRoomFeature().getUniqueId(), p.getRoomFeature().getLabelWithType()));
		}
		return data;
	}

	private static TimePatternGrid grid(TimePattern pattern, TimePatternModel model, boolean inherited) {
		TimePatternGrid g = new TimePatternGrid();
		g.setTimePatternId(pattern.getUniqueId());
		g.setName(pattern.getName());
		g.setExactTime(false);
		g.setInherited(inherited);
		for (int d = 0; d < model.getNrDays(); d++)
			g.getDays().add(model.getDayHeader(d));
		for (int t = 0; t < model.getNrTimes(); t++)
			g.getTimes().add(model.getStartTime(t));
		for (int d = 0; d < model.getNrDays(); d++)
			for (int t = 0; t < model.getNrTimes(); t++)
				g.getPrefs().add(model.getPreference(d, t));
		g.setPreference(model.getPreferences());
		return g;
	}

	private static boolean isOwn(Preference p, PreferenceGroup pg) {
		return p.getOwner() != null && p.getOwner().getUniqueId() != null && p.getOwner().getUniqueId().equals(pg.getUniqueId());
	}

	private static String inheritedFrom(PreferenceGroup pg) {
		try {
			if (pg instanceof Class_)
				return ((Class_) pg).getSchedulingSubpart().getSchedulingSubpartLabel();
			if (pg instanceof SchedulingSubpart && ((SchedulingSubpart) pg).canInheritParentPreferences())
				return ((SchedulingSubpart) pg).getParentSubpart().getSchedulingSubpartLabel();
		} catch (Exception e) {}
		return null;
	}

	/* ------------------------------------------------------------------ save */

	/**
	 * Replace the time / room / building / room group / room feature preferences
	 * of the owner (legacy {@code doClear} + {@code PreferencesAction2.doUpdate}).
	 * The caller merges the owner, logs the change and commits.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void save(PreferenceGroup pg, PreferencesUpdate update, SessionContext context) {
		if (update == null) return;
		Set<Preference> s = pg.getPreferences();

		// Clear all old prefs of the edited types
		int types = Preference.Type.toInt(TYPES);
		for (Iterator<Preference> i = s.iterator(); i.hasNext(); ) {
			if (i.next().getType().in(types)) i.remove();
		}
		pg.setPreferences(s);

		PreferenceLevel required = PreferenceLevel.getPreferenceLevel(PreferenceLevel.sRequired);
		PreferenceLevel neutral = PreferenceLevel.getPreferenceLevel(PreferenceLevel.sNeutral);

		// Time prefs
		Set parentTimePrefs = pg.effectivePreferences(TimePref.class, false);
		boolean canUseHardTimePrefs = context.hasPermission(pg, Right.CanUseHardTimePrefs);
		Set<Long> seenPatterns = new HashSet<Long>();
		if (update.getTimePrefs() != null) {
			for (TimePatternGrid g : update.getTimePrefs()) {
				if (g == null) continue;
				TimePattern timePattern = (g.getTimePatternId() == null || g.getTimePatternId() < 0 ? null : TimePatternDAO.getInstance().get(g.getTimePatternId()));
				if (g.getTimePatternId() != null && g.getTimePatternId() >= 0 && timePattern == null)
					throw new GwtRpcException("Meeting-time pattern " + g.getTimePatternId() + " does not exist.");
				if (timePattern != null && !seenPatterns.add(timePattern.getUniqueId())) continue;

				String preference;
				if (timePattern == null || timePattern.isExactTime() || g.getPrefs() == null || g.getPrefs().isEmpty()) {
					preference = g.getPreference();
					if (timePattern != null && !timePattern.isExactTime() && preference != null) {
						TimePatternModel model = timePattern.getTimePatternModel(canUseHardTimePrefs);
						model.setPreferences(preference);
						preference = model.getPreferences();
					}
				} else {
					TimePatternModel model = timePattern.getTimePatternModel(canUseHardTimePrefs);
					int idx = 0;
					for (int d = 0; d < model.getNrDays(); d++)
						for (int t = 0; t < model.getNrTimes(); t++) {
							String prolog = (idx < g.getPrefs().size() ? g.getPrefs().get(idx) : null);
							idx++;
							PreferenceLevel level = (prolog == null ? null : PreferenceLevel.getPreferenceLevel(prolog));
							model.setPreference(d, t, level == null ? PreferenceLevel.sNeutral : level.getPrefProlog());
						}
					preference = model.getPreferences();
				}

				TimePref tp = new TimePref();
				tp.setOwner(pg);
				tp.setPreference(preference);
				tp.setPrefLevel(required);
				tp.setTimePattern(timePattern);

				TimePref sameParentTimePref = null;
				if (parentTimePrefs != null && !parentTimePrefs.isEmpty()) {
					for (Iterator i = parentTimePrefs.iterator(); i.hasNext(); ) {
						TimePref parentTimePref = (TimePref) i.next();
						if (parentTimePref.isSame(tp, pg)) {
							if (parentTimePref.getPreference().equals(tp.getPreference()) && parentTimePref.getPrefLevel().equals(tp.getPrefLevel()))
								sameParentTimePref = parentTimePref;
							i.remove();
							break;
						}
					}
				}
				if (sameParentTimePref == null) s.add(tp);
			}
		}
		if (parentTimePrefs != null) {
			for (Iterator i = parentTimePrefs.iterator(); i.hasNext(); ) {
				TimePref tp = (TimePref) ((TimePref) i.next()).clone();
				tp.setOwner(pg);
				tp.setPrefLevel(neutral);
				s.add(tp);
			}
		}

		// Room prefs
		Set parentRoomPrefs = pg.effectivePreferences(RoomPref.class);
		for (PrefRow row : rows(update.getRoomPrefs())) {
			Location room = LocationDAO.getInstance().get(row.getId());
			if (room == null) continue;
			RoomPref rp = new RoomPref();
			rp.setOwner(pg);
			rp.setPrefLevel(level(row));
			rp.setRoom(room);
			rp.setRoomIndex(roomIndex(row));
			if (!sameAsParent(rp, parentRoomPrefs, pg)) s.add(rp);
		}
		for (Iterator i = parentRoomPrefs.iterator(); i.hasNext(); ) {
			RoomPref rp = (RoomPref) ((RoomPref) i.next()).clone();
			rp.setOwner(pg);
			rp.setPrefLevel(neutral);
			s.add(rp);
		}

		// Building prefs
		Set parentBuildingPrefs = pg.effectivePreferences(BuildingPref.class);
		for (PrefRow row : rows(update.getBuildingPrefs())) {
			Building bldg = BuildingDAO.getInstance().get(row.getId());
			if (bldg == null) continue;
			BuildingPref bp = new BuildingPref();
			bp.setOwner(pg);
			bp.setPrefLevel(level(row));
			bp.setBuilding(bldg);
			bp.setRoomIndex(roomIndex(row));
			if (!sameAsParent(bp, parentBuildingPrefs, pg)) s.add(bp);
		}
		for (Iterator i = parentBuildingPrefs.iterator(); i.hasNext(); ) {
			BuildingPref bp = (BuildingPref) ((BuildingPref) i.next()).clone();
			bp.setOwner(pg);
			bp.setPrefLevel(neutral);
			s.add(bp);
		}

		// Room feature prefs
		Set parentRoomFeaturePrefs = pg.effectivePreferences(RoomFeaturePref.class);
		for (PrefRow row : rows(update.getRoomFeaturePrefs())) {
			RoomFeature rf = RoomFeatureDAO.getInstance().get(row.getId());
			if (rf == null) continue;
			RoomFeaturePref rfp = new RoomFeaturePref();
			rfp.setOwner(pg);
			rfp.setPrefLevel(level(row));
			rfp.setRoomFeature(rf);
			rfp.setRoomIndex(roomIndex(row));
			if (!sameAsParent(rfp, parentRoomFeaturePrefs, pg)) s.add(rfp);
		}
		for (Iterator i = parentRoomFeaturePrefs.iterator(); i.hasNext(); ) {
			RoomFeaturePref rfp = (RoomFeaturePref) ((RoomFeaturePref) i.next()).clone();
			rfp.setOwner(pg);
			rfp.setPrefLevel(neutral);
			s.add(rfp);
		}

		// Room group prefs
		Set parentRoomGroupPrefs = pg.effectivePreferences(RoomGroupPref.class);
		for (PrefRow row : rows(update.getRoomGroupPrefs())) {
			RoomGroup gr = RoomGroupDAO.getInstance().get(row.getId());
			if (gr == null) continue;
			RoomGroupPref gp = new RoomGroupPref();
			gp.setOwner(pg);
			gp.setPrefLevel(level(row));
			gp.setRoomGroup(gr);
			gp.setRoomIndex(roomIndex(row));
			if (!sameAsParent(gp, parentRoomGroupPrefs, pg)) s.add(gp);
		}
		for (Iterator i = parentRoomGroupPrefs.iterator(); i.hasNext(); ) {
			RoomGroupPref gp = (RoomGroupPref) ((RoomGroupPref) i.next()).clone();
			gp.setOwner(pg);
			gp.setPrefLevel(neutral);
			s.add(gp);
		}

		pg.setPreferences(s);
	}

	/** Legacy same-parent rule: drop the matching parent pref; skip the new one when the level is the same too. */
	@SuppressWarnings("rawtypes")
	private static boolean sameAsParent(Preference p, Set parentPrefs, PreferenceGroup pg) {
		if (parentPrefs == null) return false;
		for (Iterator j = parentPrefs.iterator(); j.hasNext(); ) {
			Preference parent = (Preference) j.next();
			if (parent.isSame(p, pg)) {
				boolean same = parent.getPrefLevel().equals(p.getPrefLevel());
				j.remove();
				return same;
			}
		}
		return false;
	}

	private static List<PrefRow> rows(List<PrefRow> rows) {
		List<PrefRow> ret = new ArrayList<PrefRow>();
		if (rows == null) return ret;
		for (PrefRow r : rows)
			if (r != null && r.getId() != null && r.getId() >= 0) ret.add(r);
		return ret;
	}

	private static PreferenceLevel level(PrefRow row) {
		PreferenceLevel level = (row.getLevel() == null ? null : PreferenceLevel.getPreferenceLevel(row.getLevel()));
		if (level == null || PreferenceLevel.sNotAvailable.equals(level.getPrefProlog()))
			throw new GwtRpcException("Invalid preference level " + row.getLevel() + ".");
		return level;
	}

	private static Integer roomIndex(PrefRow row) {
		return (row.getRoomIndex() == null || row.getRoomIndex() < 0 ? null : row.getRoomIndex());
	}
}
