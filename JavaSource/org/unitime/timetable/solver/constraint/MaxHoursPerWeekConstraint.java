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
package org.unitime.timetable.solver.constraint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cpsolver.coursett.model.Lecture;
import org.cpsolver.coursett.model.Placement;
import org.cpsolver.ifs.assignment.Assignment;
import org.cpsolver.ifs.model.Constraint;
import org.cpsolver.ifs.util.ToolBox;

/**
 * Caps the total weekly teaching load of a group of classes (in practice, all
 * classes taught by one instructor, loaded the same way GroupConstraint's
 * MAX_HRS_DAY is - see TimetableDatabaseLoader.loadInstructorGroupConstraint)
 * to a configurable number of hours a week.
 * <br><br>
 * Distribution reference format: MAX_HRS_WEEK(n), e.g. MAX_HRS_WEEK(25).
 * <br><br>
 * The vendored cpsolver library only ships a per-day cap (GroupConstraint's
 * MAX_HRS_DAY); it has no per-week equivalent, and GroupConstraint.ConstraintType
 * / FlexibleConstraint.FlexibleConstraintType are both compiled Java enums that
 * cannot be extended from this project without rebuilding cpsolver itself. This
 * class is instead implemented directly against cpsolver's public IFS Constraint
 * API (the same extension point cpsolver itself uses for constraints such as
 * SpreadConstraint or IgnoreStudentConflictsConstraint, which also sit outside
 * the GroupConstraint enum) and is wired up in
 * TimetableDatabaseLoader.createGroupConstraint next to those.
 * <br><br>
 * The weekly load of a class is its meeting length multiplied by the number of
 * distinct days a week it meets (TimeLocation.getDayCode() bit count) - i.e.
 * the load of one recurring week, not a sum over every calendar week of the
 * term. This matches how a school workload rule is normally expressed ("no
 * more than N hours a week") and assumes a class meets on the same days every
 * week, which is the standard case for a fixed weekly bell timetable.
 * <br><br>
 * Only usable as a required (hard) constraint - see the caller in
 * TimetableDatabaseLoader, which mirrors the SAME_INSTR / IgnoreStudentConflictsConstraint
 * pattern of only constructing the constraint when the preference is Required.
 */
public class MaxHoursPerWeekConstraint extends Constraint<Lecture, Placement> {
	/** 5-minute slots per hour, matching GroupConstraint.MAX_HRS_DAY's granularity. */
	private static final int SLOTS_PER_HOUR = 12;
	private static final Pattern sReferencePattern = Pattern.compile("MAX_HRS_WEEK\\(([0-9]+(?:\\.[0-9]+)?)\\)");

	private final int iMaxSlotsAWeek;
	private final String iName;

	public MaxHoursPerWeekConstraint(double hours) {
		super();
		iMaxSlotsAWeek = (int) Math.round(SLOTS_PER_HOUR * hours);
		iName = "At Most " + (hours == Math.rint(hours) ? String.valueOf((long) hours) : String.valueOf(hours)) + " Hours A Week";
	}

	/** True when the given distribution reference is a MAX_HRS_WEEK(n) constraint. */
	public static boolean matches(String reference) {
		return reference != null && sReferencePattern.matcher(reference).matches();
	}

	/** Parses a MAX_HRS_WEEK(n) reference into a constraint instance, or null if it does not match. */
	public static MaxHoursPerWeekConstraint create(String reference) {
		Matcher m = sReferencePattern.matcher(reference == null ? "" : reference);
		if (!m.matches()) return null;
		return new MaxHoursPerWeekConstraint(Double.parseDouble(m.group(1)));
	}

	private int nrSlotsAWeek(Assignment<Lecture, Placement> assignment, HashMap<Lecture, Placement> assignments, Set<Placement> conflicts) {
		int total = 0;
		for (Lecture lecture: variables()) {
			Placement placement = assignments.get(lecture);
			if (placement == null) placement = assignment.getValue(lecture);
			if (placement == null || conflicts.contains(placement)) continue;
			total += placement.getTimeLocation().getNrSlotsPerMeeting() * Integer.bitCount(placement.getTimeLocation().getDayCode());
		}
		return total;
	}

	@Override
	public void computeConflicts(Assignment<Lecture, Placement> assignment, Placement value, Set<Placement> conflicts) {
		HashMap<Lecture, Placement> assignments = new HashMap<Lecture, Placement>();
		assignments.put(value.variable(), value);
		if (nrSlotsAWeek(assignment, assignments, conflicts) <= iMaxSlotsAWeek) return;

		List<Placement> adepts = new ArrayList<Placement>();
		for (Lecture other: variables()) {
			if (other.equals(value.variable()) || other.isConstant()) continue;
			Placement p = assignment.getValue(other);
			if (p == null || conflicts.contains(p)) continue;
			adepts.add(p);
		}
		while (nrSlotsAWeek(assignment, assignments, conflicts) > iMaxSlotsAWeek) {
			if (adepts.isEmpty()) { conflicts.add(value); break; }
			Placement conflict = ToolBox.random(adepts);
			adepts.remove(conflict);
			conflicts.add(conflict);
		}
	}

	@Override
	public boolean isConsistent(Placement value1, Placement value2) {
		int slots = value1.getTimeLocation().getNrSlotsPerMeeting() * Integer.bitCount(value1.getTimeLocation().getDayCode())
				+ value2.getTimeLocation().getNrSlotsPerMeeting() * Integer.bitCount(value2.getTimeLocation().getDayCode());
		return slots <= iMaxSlotsAWeek;
	}

	@Override
	public String getName() {
		return iName;
	}

	@Override
	public String toString() {
		return iName;
	}
}
