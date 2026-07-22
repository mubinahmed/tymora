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
package org.unitime.timetable.server.curricula;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.CurriculumProjectionRulesEditInterface.CurriculumProjectionRulesEditRequest;
import org.unitime.timetable.gwt.shared.CurriculumProjectionRulesEditInterface.CurriculumProjectionRulesEditResponse;
import org.unitime.timetable.gwt.shared.CurriculumProjectionRulesEditInterface.Operation;
import org.unitime.timetable.gwt.shared.CurriculumProjectionRulesEditInterface.ProjectionRuleRow;
import org.unitime.timetable.model.AcademicArea;
import org.unitime.timetable.model.AcademicClassification;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ChangeLog.Source;
import org.unitime.timetable.model.CurriculumProjectionRule;
import org.unitime.timetable.model.PosMajor;
import org.unitime.timetable.model.dao.AcademicClassificationDAO;
import org.unitime.timetable.model.dao.AcademicAreaDAO;
import org.unitime.timetable.model.dao.CurriculumProjectionRuleDAO;
import org.unitime.timetable.model.dao.PosMajorDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * Load / Save backing bean for the legacy Curriculum Projection Rules
 * (curprojrules) GWT page. The classic CurriculaService.loadProjectionRules
 * returns an object-keyed nested HashMap that the Gson command facade cannot
 * serialize; this bean computes the same values via direct model/DAO queries
 * and returns a FLAT row model (one row per area/major/classification tuple
 * with last-like enrollment).
 *
 * LOAD gathers all academic areas, classifications and last-like enrollments of
 * the current academic session (mirroring
 * CurriculaServlet.loadAreaMajorClasf2ll) and emits every tuple that has
 * enrollment &gt; 0 (matching the legacy page, which hides zero-enrollment
 * cells), carrying the stored projection fraction where a rule exists.
 *
 * SAVE performs a per-row merge (never the legacy delete-all/re-create, which
 * would silently discard the snapshot_proj / snapshot_proj_date columns): an
 * edited projection updates the matching CurriculumProjectionRule in place
 * (preserving the snapshot fields) or creates a new one; a cleared projection
 * deletes the matching rule. Each change is recorded in the ChangeLog under
 * Source.CUR_PROJ_RULES, matching the legacy action.
 *
 * LOAD is gated by Right.CurriculumProjectionRulesDetail and SAVE by
 * Right.CurriculumProjectionRulesEdit (both Session-qualified). Additive:
 * introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(CurriculumProjectionRulesEditRequest.class)
public class CurriculumProjectionRulesEditBackend implements GwtRpcImplementation<CurriculumProjectionRulesEditRequest, CurriculumProjectionRulesEditResponse> {
	private static DecimalFormat sPct = new DecimalFormat("0.0");

	@Override
	public CurriculumProjectionRulesEditResponse execute(CurriculumProjectionRulesEditRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		Operation op = (request.getOperation() == null ? Operation.LOAD : request.getOperation());

		if (op == Operation.SAVE) {
			context.checkPermission(sessionId, "Session", Right.CurriculumProjectionRulesEdit);
			save(request.getRows(), sessionId, context);
		} else {
			context.checkPermission(sessionId, "Session", Right.CurriculumProjectionRulesDetail);
		}

		return load(sessionId, context);
	}

	protected CurriculumProjectionRulesEditResponse load(Long sessionId, SessionContext context) {
		CurriculumProjectionRulesEditResponse response = new CurriculumProjectionRulesEditResponse();
		response.setEditable(context.hasPermission(sessionId, "Session", Right.CurriculumProjectionRulesEdit));

		org.hibernate.Session hibSession = CurriculumProjectionRuleDAO.getInstance().getSession();

		// Academic areas of the session (ordered like the legacy page).
		List<AcademicArea> areas = hibSession.createQuery(
				"select a from AcademicArea a where a.session.uniqueId = :sessionId order by a.academicAreaAbbreviation, a.title",
				AcademicArea.class).setParameter("sessionId", sessionId).setCacheable(true).list();

		// Academic classifications of the session.
		List<AcademicClassification> classifications = hibSession.createQuery(
				"select c from AcademicClassification c where c.session.uniqueId = :sessionId order by c.code",
				AcademicClassification.class).setParameter("sessionId", sessionId).setCacheable(true).list();

		// Majors of the session and the mapping area id -> majors of that area.
		Map<Long, List<PosMajor>> areaMajors = new HashMap<Long, List<PosMajor>>();
		for (PosMajor major : hibSession.createQuery(
				"select m from PosMajor m where m.session.uniqueId = :sessionId", PosMajor.class)
				.setParameter("sessionId", sessionId).setCacheable(true).list()) {
			for (AcademicArea a : major.getAcademicAreas()) {
				List<PosMajor> list = areaMajors.get(a.getUniqueId());
				if (list == null) { list = new ArrayList<PosMajor>(); areaMajors.put(a.getUniqueId(), list); }
				list.add(major);
			}
		}

		// Last-like enrollments: area abbreviation -> major code -> classification code -> count.
		Hashtable<String, Hashtable<String, Hashtable<String, Integer>>> area2major2clasf2ll = loadAreaMajorClasf2ll(hibSession, sessionId);

		// Stored projections: (areaId, majorId-or-null, clasfId) -> projection fraction.
		Map<String, Float> projections = new HashMap<String, Float>();
		for (CurriculumProjectionRule rule : hibSession.createQuery(
				"select r from CurriculumProjectionRule r where r.academicArea.session.uniqueId = :sessionId", CurriculumProjectionRule.class)
				.setParameter("sessionId", sessionId).setCacheable(true).list()) {
			projections.put(key(rule.getAcademicArea().getUniqueId(),
					rule.getMajor() == null ? null : rule.getMajor().getUniqueId(),
					rule.getAcademicClassification().getUniqueId()), rule.getProjection());
		}

		for (AcademicArea area : areas) {
			String areaCode = area.getAcademicAreaAbbreviation();
			String areaLabel = Constants.curriculaToInitialCase(area.getTitle());
			Hashtable<String, Hashtable<String, Integer>> majorClasf2ll = area2major2clasf2ll.get(areaCode);

			// Default (aggregate, no-major) rows: enrollment summed across all majors of the area.
			for (AcademicClassification clasf : classifications) {
				int ll = 0;
				if (majorClasf2ll != null)
					for (Hashtable<String, Integer> clasf2ll : majorClasf2ll.values()) {
						Integer c = clasf2ll.get(clasf.getCode());
						if (c != null) ll += c;
					}
				if (ll <= 0) continue;
				ProjectionRuleRow row = new ProjectionRuleRow();
				row.setAcademicAreaId(area.getUniqueId());
				row.setAcademicAreaCode(areaCode);
				row.setAcademicAreaLabel(areaLabel);
				row.setMajorId(org.unitime.timetable.gwt.shared.CurriculumProjectionRulesEditInterface.DEFAULT_MAJOR_ID);
				row.setMajorCode("");
				row.setMajorLabel("Default (no major)");
				row.setClassificationId(clasf.getUniqueId());
				row.setClassificationCode(clasf.getCode());
				row.setClassificationLabel(clasf.getName());
				row.setEnrollment(ll);
				row.setProjection(projections.get(key(area.getUniqueId(), null, clasf.getUniqueId())));
				response.addRow(row);
			}

			// Per-major rows.
			List<PosMajor> majors = areaMajors.get(area.getUniqueId());
			if (majors != null)
				for (PosMajor major : majors) {
					Hashtable<String, Integer> clasf2ll = (majorClasf2ll == null ? null : majorClasf2ll.get(major.getCode()));
					for (AcademicClassification clasf : classifications) {
						Integer ll = (clasf2ll == null ? null : clasf2ll.get(clasf.getCode()));
						if (ll == null || ll <= 0) continue;
						ProjectionRuleRow row = new ProjectionRuleRow();
						row.setAcademicAreaId(area.getUniqueId());
						row.setAcademicAreaCode(areaCode);
						row.setAcademicAreaLabel(areaLabel);
						row.setMajorId(major.getUniqueId());
						row.setMajorCode(major.getCode());
						row.setMajorLabel(Constants.curriculaToInitialCase(major.getName()));
						row.setClassificationId(clasf.getUniqueId());
						row.setClassificationCode(clasf.getCode());
						row.setClassificationLabel(clasf.getName());
						row.setEnrollment(ll);
						row.setProjection(projections.get(key(area.getUniqueId(), major.getUniqueId(), clasf.getUniqueId())));
						response.addRow(row);
					}
				}
		}

		return response;
	}

	/** Mirrors CurriculaServlet.loadAreaMajorClasf2ll: last-like counts grouped by area / major / classification. */
	private Hashtable<String, Hashtable<String, Hashtable<String, Integer>>> loadAreaMajorClasf2ll(org.hibernate.Session hibSession, Long sessionId) {
		Hashtable<String, Hashtable<String, Hashtable<String, Integer>>> area2major2clasf2ll = new Hashtable<String, Hashtable<String, Hashtable<String, Integer>>>();
		for (Object[] o : hibSession.createQuery(
				"select a.academicAreaAbbreviation, m.code, f.code, sum(ac.weight) from Student s " +
				"inner join s.areaClasfMajors ac inner join ac.academicClassification f inner join ac.academicArea a " +
				"inner join ac.major m " +
				"where s.uniqueId in (select x.student.uniqueId from LastLikeCourseDemand x where x.subjectArea.session.uniqueId = :sessionId) " +
				"group by a.academicAreaAbbreviation, m.code, f.code", Object[].class)
				.setParameter("sessionId", sessionId).setCacheable(true).list()) {
			String area = (String) o[0];
			String major = (String) o[1];
			String clasf = (String) o[2];
			int students = Math.round(((Number) o[3]).floatValue());
			Hashtable<String, Hashtable<String, Integer>> majorClasf2ll = area2major2clasf2ll.get(area);
			if (majorClasf2ll == null) { majorClasf2ll = new Hashtable<String, Hashtable<String, Integer>>(); area2major2clasf2ll.put(area, majorClasf2ll); }
			Hashtable<String, Integer> clasf2ll = majorClasf2ll.get(major);
			if (clasf2ll == null) { clasf2ll = new Hashtable<String, Integer>(); majorClasf2ll.put(major, clasf2ll); }
			clasf2ll.put(clasf, students);
		}
		return area2major2clasf2ll;
	}

	protected void save(List<ProjectionRuleRow> rows, Long sessionId, SessionContext context) {
		if (rows == null) return;

		Transaction tx = null;
		org.hibernate.Session hibSession = CurriculumProjectionRuleDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			// Index existing rules of the session by (areaId, majorId-or-null, clasfId).
			Map<String, CurriculumProjectionRule> existing = new HashMap<String, CurriculumProjectionRule>();
			for (CurriculumProjectionRule rule : hibSession.createQuery(
					"select r from CurriculumProjectionRule r where r.academicArea.session.uniqueId = :sessionId", CurriculumProjectionRule.class)
					.setParameter("sessionId", sessionId).list()) {
				existing.put(key(rule.getAcademicArea().getUniqueId(),
						rule.getMajor() == null ? null : rule.getMajor().getUniqueId(),
						rule.getAcademicClassification().getUniqueId()), rule);
			}

			for (ProjectionRuleRow row : rows) {
				if (row.getAcademicAreaId() == null || row.getClassificationId() == null) continue;
				Long majorId = (row.getMajorId() == null || row.isDefaultMajor()) ? null : row.getMajorId();
				String k = key(row.getAcademicAreaId(), majorId, row.getClassificationId());
				CurriculumProjectionRule rule = existing.get(k);
				Float projection = row.getProjection();

				if (projection == null) {
					// Cleared projection -> delete the matching rule (if any).
					if (rule != null) {
						ChangeLog.addChange(hibSession, context, rule, describe(rule), Source.CUR_PROJ_RULES, ChangeLog.Operation.DELETE, null, null);
						hibSession.remove(rule);
					}
					continue;
				}
				if (projection.floatValue() < 0f)
					throw new GwtRpcException("Projection must not be negative.");

				if (rule == null) {
					// Create a new rule for this tuple.
					AcademicArea area = AcademicAreaDAO.getInstance().get(row.getAcademicAreaId(), hibSession);
					AcademicClassification clasf = AcademicClassificationDAO.getInstance().get(row.getClassificationId(), hibSession);
					if (area == null || clasf == null)
						throw new GwtRpcException("The referenced academic area or classification no longer exists.");
					PosMajor major = (majorId == null ? null : PosMajorDAO.getInstance().get(majorId, hibSession));
					rule = new CurriculumProjectionRule();
					rule.setAcademicArea(area);
					rule.setAcademicClassification(clasf);
					rule.setMajor(major);
					rule.setProjection(projection);
					hibSession.persist(rule);
					ChangeLog.addChange(hibSession, context, rule, describe(rule), Source.CUR_PROJ_RULES, ChangeLog.Operation.CREATE, null, null);
				} else if (rule.getProjection() == null || rule.getProjection().floatValue() != projection.floatValue()) {
					// Merge on update: only touch the projection, preserve snapshot columns.
					rule.setProjection(projection);
					hibSession.merge(rule);
					ChangeLog.addChange(hibSession, context, rule, describe(rule), Source.CUR_PROJ_RULES, ChangeLog.Operation.UPDATE, null, null);
				}
			}

			hibSession.flush();
			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	private static String describe(CurriculumProjectionRule r) {
		return r.getAcademicArea().getAcademicAreaAbbreviation()
				+ (r.getMajor() == null ? "" : "/" + r.getMajor().getCode())
				+ " " + r.getAcademicClassification().getCode()
				+ ": " + sPct.format(100.0 * (r.getProjection() == null ? 0f : r.getProjection())) + "%";
	}

	private static String key(Long areaId, Long majorId, Long clasfId) {
		return areaId + ":" + (majorId == null ? "-" : majorId) + ":" + clasfId;
	}
}
