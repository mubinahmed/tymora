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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.ClassLine;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionPrefEditRequest;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionPrefEditResponse;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionPrefRecord;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.DistributionTypeInfo;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.IdLabel;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.PrefLevelInfo;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.StructureInfo;
import org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.SubjectAreaInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
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
import org.unitime.timetable.model.comparators.ClassComparator;
import org.unitime.timetable.model.comparators.SchedulingSubpartComparator;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.DistributionPrefDAO;
import org.unitime.timetable.model.dao.DistributionTypeDAO;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
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
 *   - SAVE with a record without an id (or with class lines) creates a new
 *     preference / replaces the structure and the ordered distribution objects,
 *     mirroring DistributionPrefsAction.doAddOrUpdate (owner = managing department
 *     with the highest distribution preference priority; sequence numbers; prefGroup
 *     = subpart for "All" or the class), gated by DistributionPreferenceAdd and the
 *     per-preference DistributionPreferenceEdit check.
 *   - COURSES / SUBPARTS / CLASSES drive the Subject -> Course -> Subpart -> Class
 *     cascade of the add / edit dialog (same queries and labels as the legacy page).
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

		Long savedId = null;
		switch (request.getOperation() == null ? org.unitime.timetable.gwt.shared.DistributionPrefEditInterface.Operation.LOAD : request.getOperation()) {
		case COURSES:
			return lookupCourses(request.getLookupId(), context);
		case SUBPARTS:
			return lookupSubparts(request.getLookupId(), context);
		case CLASSES:
			return lookupClasses(request.getLookupId(), context);
		case SAVE:
			if (request.getRecord() != null && (request.getRecord().getId() == null || request.getRecord().getId() < 0 || request.getRecord().getLines() != null))
				savedId = saveFull(request.getRecord(), context);
			else
				save(request.getRecord(), context);
			break;
		case DELETE:
			delete(request.getRecord(), context);
			break;
		case LOAD:
		default:
			break;
		}

		DistributionPrefEditResponse response = load(request, context);
		if (savedId != null) response.setSavedId(savedId);
		return response;
	}

	protected DistributionPrefEditResponse load(DistributionPrefEditRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		DistributionPrefEditResponse response = new DistributionPrefEditResponse();
		response.setTitle("Distribution Preferences");
		response.setEditable(context.hasPermission(Right.DistributionPreferences));
		response.setDeletable(context.hasPermission(Right.DistributionPreferences));
		response.setCanAdd(context.hasPermission(Right.DistributionPreferenceAdd));
		for (DistributionPref.Structure st : DistributionPref.Structure.values())
			response.addStructure(new StructureInfo(st.name(), st.getName(), st.getDescription()));

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
				try { record.setStructure(current.getStructure() == null ? DistributionPref.Structure.AllClasses.name() : current.getStructure().name()); } catch (Exception e) {}
				try {
					if (current.getOwner() instanceof Department) {
						for (DistributionObject dObj : new TreeSet<DistributionObject>(current.getDistributionObjects())) {
							if (dObj.getPrefGroup() instanceof Class_) {
								Class_ c = (Class_) dObj.getPrefGroup();
								CourseOffering co = c.getSchedulingSubpart().getInstrOfferingConfig().getInstructionalOffering().getControllingCourseOffering();
								record.addLine(new ClassLine(co.getSubjectArea().getUniqueId(), co.getUniqueId(), c.getSchedulingSubpart().getUniqueId(), c.getUniqueId()));
							} else if (dObj.getPrefGroup() instanceof SchedulingSubpart) {
								SchedulingSubpart ss = (SchedulingSubpart) dObj.getPrefGroup();
								CourseOffering co = ss.getInstrOfferingConfig().getInstructionalOffering().getControllingCourseOffering();
								record.addLine(new ClassLine(co.getSubjectArea().getUniqueId(), co.getUniqueId(), ss.getUniqueId(), -1l));
							}
						}
					}
				} catch (Exception e) {}
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

	// ---------------------------------------------------------------- cascade lookups

	private static boolean isUserSubjectArea(Long subjectAreaId, SessionContext context) {
		if (subjectAreaId == null) return false;
		for (SubjectArea sa : SubjectArea.getUserSubjectAreas(context.getUser()))
			if (sa.getUniqueId().equals(subjectAreaId)) return true;
		return false;
	}

	/** Controlling, offered courses of a subject area (legacy setLookupLists, first query). */
	protected DistributionPrefEditResponse lookupCourses(Long subjectAreaId, SessionContext context) {
		DistributionPrefEditResponse response = new DistributionPrefEditResponse();
		if (subjectAreaId == null || !isUserSubjectArea(subjectAreaId, context)) return response;
		List<Object[]> result = DistributionPrefDAO.getInstance().getSession().createQuery(
				"select co.uniqueId, co.courseNbr, co.title from InstructionalOffering as io, CourseOffering co " +
				"where co.subjectArea.uniqueId = :subjectAreaId and io.uniqueId = co.instructionalOffering.uniqueId " +
				"and io.notOffered = false and co.isControl = true order by co.courseNbr", Object[].class)
				.setParameter("subjectAreaId", subjectAreaId)
				.setCacheable(true).list();
		for (Object[] a : result)
			response.addOption(new IdLabel((Long) a[0], a[1].toString() + " - " + (a[2] == null ? "" : a[2].toString())));
		return response;
	}

	/** Scheduling subparts of a course (itype abbv, indented by parent, [config], (suffix)). */
	protected DistributionPrefEditResponse lookupSubparts(Long courseId, SessionContext context) {
		DistributionPrefEditResponse response = new DistributionPrefEditResponse();
		if (courseId == null) return response;
		CourseOffering course = CourseOfferingDAO.getInstance().get(courseId);
		if (course == null) return response;
		List<SchedulingSubpart> subparts = new ArrayList<SchedulingSubpart>(DistributionPrefDAO.getInstance().getSession().createQuery(
				"select distinct s from CourseOffering co, InstructionalOffering io, InstrOfferingConfig ioc, SchedulingSubpart s " +
				"where co.uniqueId = :courseId and co.instructionalOffering.uniqueId = io.uniqueId " +
				"and ioc.instructionalOffering.uniqueId = io.uniqueId and s.instrOfferingConfig.uniqueId = ioc.uniqueId", SchedulingSubpart.class)
				.setParameter("courseId", courseId)
				.setCacheable(true).list());
		Collections.sort(subparts, new SchedulingSubpartComparator(course.getSubjectArea().getUniqueId()));
		for (SchedulingSubpart subpart : subparts) {
			String name = subpart.getItype().getAbbv();
			String suffix = subpart.getSchedulingSubpartSuffix();
			SchedulingSubpart a = subpart;
			while (a.getParentSubpart() != null) {
				name = "\u00A0\u00A0\u00A0\u00A0" + name;
				a = a.getParentSubpart();
			}
			if (a.getInstrOfferingConfig().getInstructionalOffering().getInstrOfferingConfigs().size() > 1)
				name += " [" + a.getInstrOfferingConfig().getName() + "]";
			response.addOption(new IdLabel(subpart.getUniqueId(), name + (suffix == null || suffix.isEmpty() ? "" : " (" + suffix + ")")));
		}
		return response;
	}

	/** Classes of a subpart; the first option (-1) is All (= the whole subpart). */
	protected DistributionPrefEditResponse lookupClasses(Long subpartId, SessionContext context) {
		DistributionPrefEditResponse response = new DistributionPrefEditResponse();
		if (subpartId == null) return response;
		List<Class_> classes = new ArrayList<Class_>(DistributionPrefDAO.getInstance().getSession().createQuery(
				"select distinct c from SchedulingSubpart s, Class_ c where s.uniqueId = :subpartId and s.uniqueId = c.schedulingSubpart.uniqueId", Class_.class)
				.setParameter("subpartId", subpartId)
				.setCacheable(true).list());
		if (classes.isEmpty()) return response;
		Collections.sort(classes, new ClassComparator(ClassComparator.COMPARE_BY_HIERARCHY));
		boolean showSuffix = ApplicationProperty.DistributionsShowClassSufix.isTrue();
		response.addOption(new IdLabel(-1l, "All"));
		for (Class_ clazz : classes) {
			String label = clazz.getSectionNumberString();
			if (showSuffix) {
				String extId = clazz.getClassSuffix(clazz.getSchedulingSubpart().getControllingCourseOffering());
				if (extId != null && !extId.isEmpty() && !extId.equalsIgnoreCase(clazz.getSectionNumberString()))
					label = clazz.getSectionNumberString() + " - " + extId;
			}
			response.addOption(new IdLabel(clazz.getUniqueId(), label));
		}
		return response;
	}

	// ------------------------------------------------------------------ create / full update

	private static boolean isAll(Long classId) {
		return classId == null || classId < 0;
	}

	/** Mirrors DistributionPrefsForm.validate (save) + DistributionPrefsAction.doAddOrUpdate. */
	protected Long saveFull(DistributionPrefRecord record, SessionContext context) {
		if (record.getDistributionTypeId() == null)
			throw new GwtRpcException("Distribution type is required.");
		if (record.getPrefLevelId() == null)
			throw new GwtRpcException("Preference level is required.");
		boolean add = (record.getId() == null || record.getId() < 0);
		if (add) context.checkPermission(Right.DistributionPreferenceAdd);

		List<ClassLine> lines = new ArrayList<ClassLine>();
		if (record.getLines() != null)
			for (ClassLine line : record.getLines())
				if (line != null && (line.getSubpartId() != null || line.getClassId() != null || line.getCourseId() != null || line.getSubjectAreaId() != null))
					lines.add(line);
		// Blank / duplicate rows
		Set<String> seen = new HashSet<String>();
		for (ClassLine line : lines) {
			if (line.getSubpartId() == null)
				throw new GwtRpcException("Invalid class selections: Select a subpart (and a class or All) on every line.");
			String key = line.getSubpartId() + ":" + (isAll(line.getClassId()) ? "-1" : line.getClassId().toString());
			if (!seen.add(key))
				throw new GwtRpcException("Invalid class selections: Check for duplicate / blank selection.");
		}
		if (lines.isEmpty())
			throw new GwtRpcException("Invalid class selections: Select at least one subpart.");
		if (lines.size() == 1 && !isAll(lines.get(0).getClassId()))
			throw new GwtRpcException("Invalid class selections: Select at least two classes.");
		if (lines.size() > 1) {
			Map<Long, Boolean> subparts = new HashMap<Long, Boolean>(); // subpart -> whole subpart selected
			for (ClassLine line : lines) {
				Boolean prev = subparts.get(line.getSubpartId());
				boolean all = isAll(line.getClassId());
				if (prev != null && (prev.booleanValue() || all))
					throw new GwtRpcException("Invalid class selections: An individual class cannot be selected when all classes of its subpart are selected.");
				subparts.put(line.getSubpartId(), all);
			}
		}

		DistributionPref.Structure structure = DistributionPref.Structure.AllClasses;
		if (record.getStructure() != null && !record.getStructure().isEmpty()) {
			try { structure = DistributionPref.Structure.valueOf(record.getStructure()); }
			catch (IllegalArgumentException e) { throw new GwtRpcException("Unknown structure " + record.getStructure() + "."); }
		}

		Transaction tx = null;
		org.hibernate.Session hibSession = DistributionPrefDAO.getInstance().getSession();
		Set<InstructionalOffering> related = new HashSet<InstructionalOffering>();
		try {
			tx = hibSession.beginTransaction();

			DistributionPref dp = null;
			if (!add) {
				dp = DistributionPrefDAO.getInstance().get(record.getId(), hibSession);
				if (dp == null)
					throw new GwtRpcException("The distribution preference no longer exists.");
				if (!(dp.getOwner() instanceof Department))
					throw new GwtRpcException("Only class distribution preferences owned by a department can be edited here.");
				context.checkPermission(dp, Right.DistributionPreferenceEdit);
				Set<DistributionObject> s = dp.getDistributionObjects();
				for (Iterator<DistributionObject> i = s.iterator(); i.hasNext();) {
					DistributionObject dObj = i.next();
					PreferenceGroup pg = dObj.getPrefGroup();
					related.add((pg instanceof Class_ ? ((Class_) pg).getSchedulingSubpart() : (SchedulingSubpart) pg).getInstrOfferingConfig().getInstructionalOffering());
					pg.getDistributionObjects().remove(dObj);
					hibSession.remove(dObj);
				}
				s.clear();
				dp.setDistributionObjects(s);
			} else {
				dp = new DistributionPref();
			}

			DistributionType type = DistributionTypeDAO.getInstance().get(record.getDistributionTypeId(), hibSession);
			if (type == null)
				throw new GwtRpcException("The selected distribution type no longer exists.");
			PreferenceLevel level = PreferenceLevel.getPreferenceLevel(record.getPrefLevelId());
			if (level == null)
				throw new GwtRpcException("The selected preference level is invalid.");
			if (!type.isAllowed(level))
				throw new GwtRpcException("Preference '" + level.getPrefName() + "' is not allowed for distribution type '" + type.getLabel() + "'.");

			dp.setDistributionType(type);
			dp.setStructure(structure);
			dp.setPrefLevel(level);

			Department owningDept = null;
			List<DistributionObject> distributionObjects = new ArrayList<DistributionObject>();
			int seq = 1;
			for (ClassLine line : lines) {
				DistributionObject dObj = new DistributionObject();
				Department dept = null;
				if (isAll(line.getClassId())) {
					SchedulingSubpart subpart = SchedulingSubpartDAO.getInstance().get(line.getSubpartId(), hibSession);
					if (subpart == null) throw new GwtRpcException("Subpart " + line.getSubpartId() + " does not exist.");
					dept = subpart.getManagingDept();
					dObj.setPrefGroup(subpart);
					related.add(subpart.getInstrOfferingConfig().getInstructionalOffering());
				} else {
					Class_ clazz = Class_DAO.getInstance().get(line.getClassId(), hibSession);
					if (clazz == null) throw new GwtRpcException("Class " + line.getClassId() + " does not exist.");
					dept = clazz.getManagingDept();
					dObj.setPrefGroup(clazz);
					related.add(clazz.getSchedulingSubpart().getInstrOfferingConfig().getInstructionalOffering());
				}
				if (owningDept == null) owningDept = dept;
				else if (dept != null && !owningDept.getUniqueId().equals(dept.getUniqueId())) {
					int p1 = (owningDept.getDistributionPrefPriority() == null ? 0 : owningDept.getDistributionPrefPriority().intValue());
					int p2 = (dept.getDistributionPrefPriority() == null ? 0 : dept.getDistributionPrefPriority().intValue());
					if (p1 < p2)
						owningDept = dept;
					else if (p1 == p2) {
						if (!context.getUser().getCurrentAuthority().hasQualifier(owningDept) && context.getUser().getCurrentAuthority().hasQualifier(dept))
							owningDept = dept;
					}
				}
				dObj.setSequenceNumber(Integer.valueOf(seq++));
				distributionObjects.add(dObj);
			}

			dp.setOwner(owningDept);
			if (dp.getUniqueId() == null) hibSession.persist(dp);

			for (DistributionObject dObj : distributionObjects) {
				dObj.setDistributionPref(dp);
				dp.addToDistributionObjects(dObj);
				hibSession.persist(dObj);
				dObj.getPrefGroup().addToDistributionObjects(dObj);
			}

			context.checkPermission(dp, Right.DistributionPreferenceEdit);

			hibSession.merge(dp);

			logOfferings(related, context, hibSession, add ? ChangeLog.Operation.CREATE : ChangeLog.Operation.UPDATE);

			tx.commit(); tx = null;
			return dp.getUniqueId();
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			hibSession.clear();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			hibSession.clear();
			throw new GwtRpcException(t.getMessage(), t);
		}
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
