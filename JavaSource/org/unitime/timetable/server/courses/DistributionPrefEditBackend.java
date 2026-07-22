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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionPrefEditRequest;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionPrefEditResponse;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionPrefRecord;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionTypeInfo;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.PrefLevelInfo;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.SubjectAreaInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.DistributionObject;
import org.unitime.timetable.model.DistributionPref;
import org.unitime.timetable.model.DistributionType;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.PreferenceGroup;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.StudentSectioningQueue;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.DistributionPrefDAO;
import org.unitime.timetable.model.dao.DistributionTypeDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.permissions.Permission;
import org.unitime.timetable.security.rights.Right;

/**
 * Create / Edit backend for the legacy distributionPrefs.action (Distribution
 * Preferences) Struts page. Sibling to the read-only DistributionPrefListBackend
 * (same DistributionPref entity). One request drives LOAD (the current user's
 * distribution preferences projected to string rows, plus, when an id is given,
 * the editable detail of a single preference together with the applicable
 * distribution types and preference levels), SAVE and DELETE.
 *
 * Functional core (conservative, unverified at runtime):
 *   - SAVE updates the distribution TYPE and preference LEVEL of an existing
 *     preference only. It merges on update: the entity is loaded, only the two
 *     rendered fields are set, and the owner / structure / distribution objects
 *     are left untouched. The chosen level must be allowed by the chosen type.
 *   - DELETE removes an existing preference, mirroring the legacy delete: it
 *     detaches every distribution object from its preference group, removes the
 *     preference from its owner, and records ChangeLog / StudentSectioningQueue.
 *
 * Deferred (noted, not implemented here): creating a brand new preference and
 * editing the owners (adding / removing / re-ordering the classes and subparts a
 * preference applies to) which are relational and encoded on the legacy page.
 *
 * Every operation is gated by {@link Right#DistributionPreferences}; SAVE and
 * DELETE additionally check the per-preference {@link Right#DistributionPreferenceEdit}
 * / {@link Right#DistributionPreferenceDelete}. Session id via
 * context.getUser().getCurrentAcademicSessionId().
 *
 * @author Angular migration
 */
@GwtRpcImplements(DistributionPrefEditRequest.class)
public class DistributionPrefEditBackend implements GwtRpcImplementation<DistributionPrefEditRequest, DistributionPrefEditResponse> {

	@Autowired Permission<InstructionalOffering> permissionOfferingLockNeeded;

	@Override
	public DistributionPrefEditResponse execute(DistributionPrefEditRequest request, SessionContext context) {
		context.checkPermission(Right.DistributionPreferences);

		switch (request.getOperation() == null ? org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.Operation.LOAD : request.getOperation()) {
		case SAVE:
			save(request.getRecord(), context);
			break;
		case DELETE:
			delete(request.getRecord(), context);
			break;
		case LOAD:
		default:
			break;
		}

		return load(request, context);
	}

	protected DistributionPrefEditResponse load(DistributionPrefEditRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		DistributionPrefEditResponse response = new DistributionPrefEditResponse();
		response.setTitle("Distribution Preferences");
		response.setEditable(context.hasPermission(Right.DistributionPreferences));
		response.setDeletable(context.hasPermission(Right.DistributionPreferences));

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
		// DistributionPrefListBackend / DistributionPrefsTableBuilder.
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
			try { r.add(dp.getPrefLevel() == null ? "" : dp.getPrefLevel().getPrefName()); } catch (Exception e) { r.add(""); }
			try { r.add(dp.getLabel()); } catch (Exception e) { r.add(""); }
			try { r.add(ownerLabel(dp.getOwner())); } catch (Exception e) { r.add(""); }
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

		// Detail for a single preference (drives the edit dialog).
		DistributionPref current = null;
		if (request.getId() != null && request.getId() >= 0) {
			current = DistributionPrefDAO.getInstance().get(request.getId());
			if (current != null) {
				DistributionPrefRecord record = new DistributionPrefRecord();
				record.setId(current.getUniqueId());
				try { record.setDistributionTypeId(current.getDistributionType() == null ? null : current.getDistributionType().getUniqueId()); } catch (Exception e) {}
				try { record.setPrefLevelId(current.getPrefLevel() == null ? null : current.getPrefLevel().getPrefId()); } catch (Exception e) {}
				try { record.setTypeLabel(current.getLabel()); } catch (Exception e) {}
				try { record.setOwnerLabel(ownerLabel(current.getOwner())); } catch (Exception e) {}
				try {
					StringBuilder sb = new StringBuilder();
					for (Iterator<DistributionObject> it = current.getOrderedSetOfDistributionObjects().iterator(); it.hasNext();) {
						DistributionObject dObj = it.next();
						if (sb.length() > 0) sb.append(", ");
						sb.append(dObj.preferenceText());
					}
					record.setAppliesTo(sb.toString());
				} catch (Exception e) {}
				response.setRecord(record);
			}
		}

		// Applicable (non-instructor, non-exam) distribution types for the selector.
		try {
			DistributionType currentType = (current == null ? null : current.getDistributionType());
			for (DistributionType dt : DistributionType.findApplicable(context, false, false, currentType))
				response.addDistributionType(new DistributionTypeInfo(dt.getUniqueId(), dt.getLabel(), dt.getAllowedPref()));
		} catch (Exception e) {
			throw new GwtRpcException("Unable to load distribution types: " + e.getMessage(), e);
		}

		// Preference levels for the selector (excludes 'not available'). The client
		// filters them by whether the chosen type's allowed string contains the char.
		for (PreferenceLevel pl : PreferenceLevel.getPreferenceLevelList())
			response.addPrefLevel(new PrefLevelInfo(pl.getPrefId(), pl.getPrefName(), String.valueOf(PreferenceLevel.prolog2char(pl.getPrefProlog()))));

		return response;
	}

	protected void save(DistributionPrefRecord record, SessionContext context) {
		if (record == null)
			throw new GwtRpcException("No distribution preference provided.");
		if (record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("Creating a new distribution preference is not supported here; use the legacy page.");
		if (record.getDistributionTypeId() == null)
			throw new GwtRpcException("Distribution type is required.");
		if (record.getPrefLevelId() == null)
			throw new GwtRpcException("Preference level is required.");

		Transaction tx = null;
		org.hibernate.Session hibSession = DistributionPrefDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			DistributionPref dp = DistributionPrefDAO.getInstance().get(record.getId(), hibSession);
			if (dp == null)
				throw new GwtRpcException("The distribution preference no longer exists.");

			context.checkPermission(dp, Right.DistributionPreferenceEdit);

			DistributionType type = DistributionTypeDAO.getInstance().get(record.getDistributionTypeId(), hibSession);
			if (type == null)
				throw new GwtRpcException("The selected distribution type no longer exists.");
			PreferenceLevel level = PreferenceLevel.getPreferenceLevel(record.getPrefLevelId());
			if (level == null)
				throw new GwtRpcException("The selected preference level is invalid.");
			if (!type.isAllowed(level))
				throw new GwtRpcException("Preference '" + level.getPrefName() + "' is not allowed for distribution type '" + type.getLabel() + "'.");

			// Merge-on-update: change only the two rendered fields. Owner, structure
			// (grouping) and distribution objects are intentionally left untouched.
			dp.setDistributionType(type);
			dp.setPrefLevel(level);
			hibSession.merge(dp);

			logAffectedOfferings(dp, context, hibSession, ChangeLog.Operation.UPDATE);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	protected void delete(DistributionPrefRecord record, SessionContext context) {
		if (record == null || record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("No distribution preference provided.");

		Transaction tx = null;
		org.hibernate.Session hibSession = DistributionPrefDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			DistributionPref dp = DistributionPrefDAO.getInstance().get(record.getId(), hibSession);
			if (dp == null)
				throw new GwtRpcException("The distribution preference no longer exists.");

			context.checkPermission(dp, Right.DistributionPreferenceDelete);

			// Gather related offerings before detaching, for ChangeLog / sectioning.
			Set<InstructionalOffering> related = collectOfferings(dp);

			// Detach every distribution object from its preference group (inverse side)
			// and remove it, then remove the preference from its owner.
			PreferenceGroup owner = dp.getOwner();
			if (owner != null && owner.getPreferences() != null)
				owner.getPreferences().remove(dp);
			for (Iterator<?> i = dp.getDistributionObjects().iterator(); i.hasNext();) {
				DistributionObject dObj = (DistributionObject) i.next();
				PreferenceGroup pg = dObj.getPrefGroup();
				if (pg != null && pg.getDistributionObjects() != null) {
					pg.getDistributionObjects().remove(dObj);
					hibSession.merge(pg);
				}
			}

			hibSession.remove(dp);
			if (owner != null)
				hibSession.merge(owner);

			logOfferings(related, context, hibSession, ChangeLog.Operation.DELETE);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	/** Instructional offerings touched by the distribution objects of a preference. */
	private static Set<InstructionalOffering> collectOfferings(DistributionPref dp) {
		Set<InstructionalOffering> related = new HashSet<InstructionalOffering>();
		try {
			for (Iterator<?> i = dp.getDistributionObjects().iterator(); i.hasNext();) {
				DistributionObject dObj = (DistributionObject) i.next();
				PreferenceGroup pg = dObj.getPrefGroup();
				SchedulingSubpart ss = (pg instanceof Class_ ? ((Class_) pg).getSchedulingSubpart() : (pg instanceof SchedulingSubpart ? (SchedulingSubpart) pg : null));
				if (ss != null)
					related.add(ss.getInstrOfferingConfig().getInstructionalOffering());
			}
		} catch (Exception e) {}
		return related;
	}

	private void logAffectedOfferings(DistributionPref dp, SessionContext context, org.hibernate.Session hibSession, ChangeLog.Operation op) {
		logOfferings(collectOfferings(dp), context, hibSession, op);
	}

	private void logOfferings(Set<InstructionalOffering> related, SessionContext context, org.hibernate.Session hibSession, ChangeLog.Operation op) {
		List<Long> changedOfferingIds = new ArrayList<Long>();
		for (InstructionalOffering io : related) {
			try {
				ChangeLog.addChange(
						hibSession,
						context,
						io,
						ChangeLog.Source.DIST_PREF_EDIT,
						op,
						io.getControllingCourseOffering().getSubjectArea(),
						null);
				if (permissionOfferingLockNeeded != null && permissionOfferingLockNeeded.check(context.getUser(), io))
					changedOfferingIds.add(io.getUniqueId());
			} catch (Exception e) {}
		}
		if (!changedOfferingIds.isEmpty())
			StudentSectioningQueue.offeringChanged(hibSession, context.getUser(), context.getUser().getCurrentAcademicSessionId(), changedOfferingIds);
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
