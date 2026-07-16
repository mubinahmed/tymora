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
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.DistributionPrefListInterface.DistributionPrefListRequest;
import org.unitime.timetable.gwt.shared.DistributionPrefListInterface.DistributionPrefListResponse;
import org.unitime.timetable.gwt.shared.DistributionPrefListInterface.SubjectAreaInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.DistributionObject;
import org.unitime.timetable.model.DistributionPref;
import org.unitime.timetable.model.PreferenceGroup;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only backing bean for the legacy distributionPrefs.action (Distribution
 * Preferences) Struts page. Returns the subject areas visible to the current
 * user (to populate a filter selector) and the distribution preferences owned by
 * the current user's departments (together with their instructor distribution
 * preferences) projected to string rows. Permission-gated by
 * {@link Right#DistributionPreferences}. Editing, add/delete and PDF/CSV export
 * remain on the legacy page (deferred). Additive: introduces no changes to
 * existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(DistributionPrefListRequest.class)
public class DistributionPrefListBackend implements GwtRpcImplementation<DistributionPrefListRequest, DistributionPrefListResponse> {

	@Override
	public DistributionPrefListResponse execute(DistributionPrefListRequest request, SessionContext context) {
		context.checkPermission(Right.DistributionPreferences);

		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		DistributionPrefListResponse response = new DistributionPrefListResponse();
		response.setTitle("Distribution Preferences");

		// Subject areas visible to the current user (drive the filter selector).
		TreeSet<SubjectArea> userSubjectAreas = SubjectArea.getUserSubjectAreas(context.getUser());
		for (SubjectArea sa : userSubjectAreas)
			response.addSubjectArea(new SubjectAreaInfo(sa.getUniqueId(), sa.getSubjectAreaAbbreviation()));

		// Resolve the selected subject area (null = all).
		Long subjectAreaId = request.getSubjectAreaId();
		if (subjectAreaId != null) {
			boolean ok = false;
			for (SubjectArea sa : userSubjectAreas)
				if (sa.getUniqueId().equals(subjectAreaId)) { ok = true; break; }
			if (!ok) subjectAreaId = null;
		}
		response.setSubjectAreaId(subjectAreaId);

		for (String c : new String[] { "Preference", "Distribution Type", "Owner", "Applies To" })
			response.addColumn(c);

		// Gather preferences for the departments the user can manage, mirroring
		// DistributionPrefsTableBuilder.getAllDistPrefsTableForCurrentUser.
		Set<DistributionPref> prefs = new LinkedHashSet<DistributionPref>();
		for (Department d : Department.getUserDepartments(context.getUser())) {
			List<DistributionPref> dp = DistributionPref.getPreferences(sessionId, d.getUniqueId(), true, null, subjectAreaId, null);
			if (dp != null) prefs.addAll(dp);
			List<DistributionPref> ip = DistributionPref.getInstructorPreferences(sessionId, d.getUniqueId(), subjectAreaId, null);
			if (ip != null) prefs.addAll(ip);
		}

		List<DistributionPref> sorted = new ArrayList<DistributionPref>(prefs);
		Collections.sort(sorted, new Comparator<DistributionPref>() {
			@Override
			public int compare(DistributionPref a, DistributionPref b) {
				int cmp = label(a).compareToIgnoreCase(label(b));
				if (cmp != 0) return cmp;
				Long ai = a.getUniqueId(), bi = b.getUniqueId();
				return Long.compare(ai == null ? -1L : ai.longValue(), bi == null ? -1L : bi.longValue());
			}
			private String label(DistributionPref p) {
				try { String s = p.getLabel(); return s == null ? "" : s; } catch (Exception e) { return ""; }
			}
		});

		for (DistributionPref dp : sorted) {
			Row r = response.addRow(dp.getUniqueId());

			// Preference level.
			try {
				r.add(dp.getPrefLevel() == null ? "" : dp.getPrefLevel().getPrefName());
			} catch (Exception e) { r.add(""); }

			// Distribution type label (includes structure/grouping if any).
			try {
				r.add(dp.getLabel());
			} catch (Exception e) { r.add(""); }

			// Owner label.
			try {
				r.add(ownerLabel(dp.getOwner()));
			} catch (Exception e) { r.add(""); }

			// Applies to: ordered distribution objects.
			try {
				StringBuilder sb = new StringBuilder();
				for (Iterator<DistributionObject> it = dp.getOrderedSetOfDistributionObjects().iterator(); it.hasNext();) {
					DistributionObject dObj = it.next();
					if (sb.length() > 0) sb.append(", ");
					sb.append(dObj.preferenceText());
				}
				r.add(sb.toString());
			} catch (Exception e) { r.add(""); }
		}

		return response;
	}

	private static String ownerLabel(PreferenceGroup owner) {
		if (owner == null) return "";
		if (owner instanceof Department)
			return ((Department) owner).getLabel();
		if (owner instanceof DepartmentalInstructor)
			return ((DepartmentalInstructor) owner).getNameLastFirst();
		if (owner instanceof Class_)
			return ((Class_) owner).getClassLabel();
		if (owner instanceof SchedulingSubpart)
			return ((SchedulingSubpart) owner).getSchedulingSubpartLabel();
		return "";
	}
}
