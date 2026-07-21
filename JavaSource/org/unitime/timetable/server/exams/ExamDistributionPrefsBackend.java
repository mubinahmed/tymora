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
package org.unitime.timetable.server.exams;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.DistPrefRecord;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.DistributionTypeInfo;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.ExamDistributionPrefsRequest;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.ExamDistributionPrefsResponse;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.ExamLine;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.IdLabel;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.Operation;
import org.unitime.timetable.gwt.shared.ExamDistributionPrefsInterface.PrefLevelInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.DistributionObject;
import org.unitime.timetable.model.DistributionPref;
import org.unitime.timetable.model.DistributionType;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.PreferenceGroup;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.CourseOfferingDAO;
import org.unitime.timetable.model.dao.DistributionPrefDAO;
import org.unitime.timetable.model.dao.DistributionTypeDAO;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Backing bean for the legacy examDistributionPrefs.action (Examination
 * Distribution Preferences) Struts page. Faithful port of
 * {@code ExamDistributionPrefsAction}: LOAD lists the exam distribution
 * preferences for the selected examination type (optionally filtered by subject
 * area / course number) as string rows; COURSES / EXAMS drive the
 * subject→course→exam add/edit cascade; DETAIL returns one preference's editable
 * detail; SAVE ports {@code doAddOrUpdate} (create or update the type, level and
 * the grouped examinations); DELETE ports {@code doDelete}. Gated by
 * {@link Right#ExaminationDistributionPreferences} plus the per-preference
 * add / edit / delete / detail rights. Additive: introduces no changes to
 * existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamDistributionPrefsRequest.class)
public class ExamDistributionPrefsBackend implements GwtRpcImplementation<ExamDistributionPrefsRequest, ExamDistributionPrefsResponse> {

	@Override
	public ExamDistributionPrefsResponse execute(ExamDistributionPrefsRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.ExaminationDistributionPreferences);

		switch (request.getOperation() == null ? Operation.LOAD : request.getOperation()) {
		case COURSES:
			return courses(request, context);
		case EXAMS:
			return exams(request, context);
		case DETAIL:
			return detail(request, context);
		case SAVE:
			save(request, context, sessionId);
			return load(request, context, sessionId);
		case DELETE:
			delete(request, context);
			return load(request, context, sessionId);
		case LOAD:
		default:
			return load(request, context, sessionId);
		}
	}

	// ---- LOAD (list + selectors) -------------------------------------------

	protected ExamDistributionPrefsResponse load(ExamDistributionPrefsRequest request, SessionContext context, Long sessionId) {
		ExamDistributionPrefsResponse response = new ExamDistributionPrefsResponse();
		response.setTitle("Examination Distribution Preferences");

		// Applicable examination types (used in this session).
		List<ExamType> types = ExamType.findAllUsedApplicable(context.getUser(),
				DepartmentStatusType.Status.ExamView, DepartmentStatusType.Status.ExamTimetable);
		for (ExamType t : types)
			response.addExamType(new IdLabel(t.getUniqueId(), t.getLabel()));

		Long examTypeId = request.getExamTypeId();
		if (examTypeId != null) {
			boolean ok = false;
			for (ExamType t : types)
				if (t.getUniqueId().equals(examTypeId)) { ok = true; break; }
			if (!ok) examTypeId = null;
		}
		if (examTypeId == null && !types.isEmpty())
			examTypeId = types.get(0).getUniqueId();
		response.setExamTypeId(examTypeId);

		// Subject areas visible to the user (filter + cascade).
		TreeSet<SubjectArea> userSubjectAreas = SubjectArea.getUserSubjectAreas(context.getUser(), false);
		for (SubjectArea sa : userSubjectAreas)
			response.addSubjectArea(new IdLabel(sa.getUniqueId(), sa.getSubjectAreaAbbreviation()));

		Long subjectAreaId = request.getSubjectAreaId();
		if (subjectAreaId != null) {
			boolean ok = false;
			for (SubjectArea sa : userSubjectAreas)
				if (sa.getUniqueId().equals(subjectAreaId)) { ok = true; break; }
			if (!ok) subjectAreaId = null;
		}
		response.setSubjectAreaId(subjectAreaId);
		response.setCourseNbr(request.getCourseNbr());

		response.setCanAdd(context.hasPermission(sessionId, "Session", Right.ExaminationDistributionPreferenceAdd));

		for (String c : new String[] { "Preference", "Distribution Type", "Examinations" })
			response.addColumn(c);

		// Selectors for the add/edit dialog.
		addSelectors(response, context, null);

		if (examTypeId != null) {
			String courseNbr = request.getCourseNbr();
			List<DistributionPref> prefs = queryPrefs(context, sessionId, subjectAreaId, courseNbr, examTypeId);
			for (DistributionPref dp : prefs) {
				if (!context.hasPermission(dp, Right.ExaminationDistributionPreferenceDetail)) continue;
				Row r = response.addRow(dp.getUniqueId());
				try { r.add(dp.getPrefLevel() == null ? "" : dp.getPrefLevel().getPrefName()); } catch (Exception e) { r.add(""); }
				try { r.add(dp.getDistributionType() == null ? "" : dp.getDistributionType().getLabel()); } catch (Exception e) { r.add(""); }
				try { r.add(examLabels(dp)); } catch (Exception e) { r.add(""); }
				if (context.hasPermission(dp, Right.ExaminationDistributionPreferenceEdit)) response.addEditableId(dp.getUniqueId());
				if (context.hasPermission(dp, Right.ExaminationDistributionPreferenceDelete)) response.addDeletableId(dp.getUniqueId());
			}
		}

		return response;
	}

	/** Applicable exam distribution types + preference levels for the selectors. */
	private void addSelectors(ExamDistributionPrefsResponse response, SessionContext context, DistributionType current) {
		try {
			for (DistributionType dt : DistributionType.findApplicable(context, false, true, current))
				response.addDistributionType(new DistributionTypeInfo(dt.getUniqueId(), dt.getLabel(), dt.getAllowedPref(), dt.getDescr()));
		} catch (Exception e) {
			throw new GwtRpcException("Unable to load distribution types: " + e.getMessage(), e);
		}
		for (PreferenceLevel pl : PreferenceLevel.getPreferenceLevelList())
			response.addPrefLevel(new PrefLevelInfo(pl.getPrefId(), pl.getPrefName(), String.valueOf(PreferenceLevel.prolog2char(pl.getPrefProlog()))));
	}

	/** Exam distribution preferences matching the filter — mirrors ExamDistributionPrefsTableBuilder.getDistPrefsTable. */
	@SuppressWarnings("unchecked")
	private List<DistributionPref> queryPrefs(SessionContext context, Long sessionId, Long subjectAreaId, String courseNbr, Long examTypeId) {
		String query = "select distinct dp from DistributionPref dp " +
				"inner join dp.distributionObjects do, Exam x inner join x.owners o where ";
		boolean titleSearch = ApplicationProperty.CourseOfferingTitleSearch.isTrue() && courseNbr != null && courseNbr.trim().length() > 2;
		if (titleSearch) {
			query += "(" + (courseNbr.indexOf('*') >= 0 ? "o.course.courseNbr like :courseNbr " : "o.course.courseNbr=:courseNbr ") +
					" or lower(o.course.title) like lower('%' || :courseNbr || '%')) and ";
		} else if (courseNbr != null && !courseNbr.trim().isEmpty()) {
			query += (courseNbr.indexOf('*') >= 0 ? "o.course.courseNbr like :courseNbr and " : "o.course.courseNbr=:courseNbr and ");
		}
		query += (subjectAreaId == null ? "" : " o.course.subjectArea.uniqueId=:subjectAreaId and ") +
				"dp.distributionType.examPref = true and " +
				"do.prefGroup = x and x.session.uniqueId=:sessionId and x.examType.uniqueId=:examTypeId";
		Query<DistributionPref> q = DistributionPrefDAO.getInstance().getSession().createQuery(query, DistributionPref.class)
				.setParameter("sessionId", sessionId)
				.setParameter("examTypeId", examTypeId);
		if (subjectAreaId != null)
			q.setParameter("subjectAreaId", subjectAreaId);
		if (courseNbr != null && !courseNbr.trim().isEmpty())
			q.setParameter("courseNbr", courseNbr.trim().replaceAll("\\*", "%"));
		return q.setCacheable(true).list();
	}

	private static String examLabels(DistributionPref dp) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<DistributionObject> it = new TreeSet<DistributionObject>(dp.getDistributionObjects()).iterator(); it.hasNext();) {
			DistributionObject dObj = it.next();
			PreferenceGroup pg = dObj.getPrefGroup();
			if (!(pg instanceof Exam)) continue;
			if (sb.length() > 0) sb.append(", ");
			sb.append(((Exam) pg).getLabel());
		}
		return sb.toString();
	}

	// ---- cascade lookups ----------------------------------------------------

	/** Offered courses of a subject area — mirrors ExamDistributionPrefsForm.getCourseNbrs. */
	protected ExamDistributionPrefsResponse courses(ExamDistributionPrefsRequest request, SessionContext context) {
		ExamDistributionPrefsResponse response = new ExamDistributionPrefsResponse();
		Long subjectAreaId = request.getLookupSubjectAreaId();
		if (subjectAreaId != null && subjectAreaId >= 0) {
			for (Object[] o : CourseOfferingDAO.getInstance().getSession().createQuery(
					"select co.uniqueId, co.courseNbr, co.title from CourseOffering co " +
							"where co.subjectArea.uniqueId = :subjectAreaId " +
							"and co.instructionalOffering.notOffered = false order by co.courseNbr", Object[].class)
					.setFetchSize(200).setCacheable(true)
					.setParameter("subjectAreaId", subjectAreaId).list()) {
				response.addCourse(new IdLabel((Long) o[0], ((String) o[1]) + " - " + (String) o[2]));
			}
		}
		return response;
	}

	/** Examinations of a course of the selected type — mirrors ExamDistributionPrefsForm.getExams. */
	protected ExamDistributionPrefsResponse exams(ExamDistributionPrefsRequest request, SessionContext context) {
		ExamDistributionPrefsResponse response = new ExamDistributionPrefsResponse();
		Long courseId = request.getLookupCourseId();
		if (courseId != null && courseId >= 0 && request.getExamTypeId() != null) {
			for (Exam exam : new TreeSet<Exam>(Exam.findExamsOfCourseOffering(courseId, request.getExamTypeId())))
				response.addExam(new IdLabel(exam.getUniqueId(), exam.getLabel()));
		}
		return response;
	}

	// ---- DETAIL -------------------------------------------------------------

	protected ExamDistributionPrefsResponse detail(ExamDistributionPrefsRequest request, SessionContext context) {
		if (request.getId() == null)
			throw new GwtRpcException("No distribution preference provided.");
		context.checkPermission(request.getId(), "DistributionPref", Right.ExaminationDistributionPreferenceDetail);

		DistributionPref dp = DistributionPrefDAO.getInstance().get(request.getId());
		if (dp == null)
			throw new GwtRpcException("The distribution preference no longer exists.");

		ExamDistributionPrefsResponse response = new ExamDistributionPrefsResponse();
		DistributionType current = dp.getDistributionType();
		addSelectors(response, context, current);

		DistPrefRecord record = new DistPrefRecord();
		record.setId(dp.getUniqueId());
		record.setDistributionTypeId(current == null ? null : current.getUniqueId());
		record.setDescription(current == null ? "" : current.getDescr());
		try { record.setPrefLevelId(dp.getPrefLevel() == null ? null : dp.getPrefLevel().getPrefId()); } catch (Exception e) {}
		for (Iterator<DistributionObject> it = new TreeSet<DistributionObject>(dp.getDistributionObjects()).iterator(); it.hasNext();) {
			DistributionObject dObj = it.next();
			PreferenceGroup pg = dObj.getPrefGroup();
			if (!(pg instanceof Exam)) continue;
			Exam exam = (Exam) pg;
			ExamLine line = new ExamLine();
			try { line.setSubjectAreaId(exam.firstSubjectArea().getUniqueId()); } catch (Exception e) {}
			try { line.setCourseId(exam.firstCourseOffering().getUniqueId()); } catch (Exception e) {}
			line.setExamId(exam.getUniqueId());
			line.setExamLabel(exam.getLabel());
			record.addExam(line);
			if (record.getExamTypeId() == null && exam.getExamType() != null)
				record.setExamTypeId(exam.getExamType().getUniqueId());
		}
		response.setRecord(record);
		response.setExamTypeId(record.getExamTypeId());
		return response;
	}

	// ---- SAVE (add / update) — port of doAddOrUpdate ------------------------

	protected void save(ExamDistributionPrefsRequest request, SessionContext context, Long sessionId) {
		DistPrefRecord record = request.getRecord();
		if (record == null)
			throw new GwtRpcException("No distribution preference provided.");
		if (record.getDistributionTypeId() == null)
			throw new GwtRpcException("Distribution type is required.");
		if (record.getPrefLevelId() == null)
			throw new GwtRpcException("Preference level is required.");

		boolean isUpdate = record.getId() != null && record.getId() >= 0;
		if (isUpdate)
			context.checkPermission(record.getId(), "DistributionPref", Right.ExaminationDistributionPreferenceEdit);
		else
			context.checkPermission(sessionId, "Session", Right.ExaminationDistributionPreferenceAdd);

		DistributionType type = DistributionTypeDAO.getInstance().get(record.getDistributionTypeId());
		if (type == null)
			throw new GwtRpcException("The selected distribution type no longer exists.");
		PreferenceLevel level = PreferenceLevel.getPreferenceLevel(record.getPrefLevelId());
		if (level == null)
			throw new GwtRpcException("The selected preference level is invalid.");
		if (!type.isAllowed(level))
			throw new GwtRpcException("Preference '" + level.getPrefName() + "' is not allowed for distribution type '" + type.getLabel() + "'.");

		DistributionPrefDAO dpDao = DistributionPrefDAO.getInstance();
		Transaction tx = null;
		org.hibernate.Session hibSession = dpDao.getSession();
		Set<Exam> relatedExams = new HashSet<Exam>();
		try {
			tx = hibSession.beginTransaction();

			DistributionPref dp;
			if (isUpdate) {
				dp = dpDao.get(record.getId(), hibSession);
				if (dp == null)
					throw new GwtRpcException("The distribution preference no longer exists.");
				Set<DistributionObject> s = dp.getDistributionObjects();
				for (Iterator<DistributionObject> i = s.iterator(); i.hasNext();) {
					DistributionObject dObj = i.next();
					PreferenceGroup pg = dObj.getPrefGroup();
					relatedExams.add((Exam) pg);
					pg.getDistributionObjects().remove(dObj);
					hibSession.remove(dObj);
				}
				s.clear();
				dp.setDistributionObjects(s);
			} else {
				dp = new DistributionPref();
			}

			dp.setDistributionType(DistributionTypeDAO.getInstance().get(record.getDistributionTypeId(), hibSession));
			dp.setGrouping(-1);
			dp.setPrefLevel(level);
			dp.setOwner(SessionDAO.getInstance().get(sessionId));

			Set<Exam> addedExams = new HashSet<Exam>();
			int idx = 0;
			if (record.getExams() != null)
				for (ExamLine el : record.getExams()) {
					if (el.getExamId() == null || el.getExamId() < 0) continue;
					Exam exam = ExamDAO.getInstance().get(el.getExamId(), hibSession);
					if (exam == null) continue;
					if (!addedExams.add(exam)) continue;
					relatedExams.add(exam);

					DistributionObject dObj = new DistributionObject();
					dObj.setPrefGroup(exam);
					dObj.setDistributionPref(dp);
					dObj.setSequenceNumber(Integer.valueOf(++idx));
					exam.getDistributionObjects().add(dObj);
					dp.addToDistributionObjects(dObj);
					if (dp.getUniqueId() != null)
						hibSession.persist(dObj);
				}

			if (dp.getUniqueId() == null)
				hibSession.persist(dp);
			else
				hibSession.merge(dp);

			for (Exam exam : relatedExams) {
				ChangeLog.addChange(hibSession, context, exam, ChangeLog.Source.DIST_PREF_EDIT,
						(isUpdate ? ChangeLog.Operation.UPDATE : ChangeLog.Operation.CREATE),
						exam.firstSubjectArea(), exam.firstDepartment());
			}

			tx.commit();
			hibSession.flush();
			hibSession.refresh(dp.getOwner());
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

	// ---- DELETE — port of doDelete ------------------------------------------

	protected void delete(ExamDistributionPrefsRequest request, SessionContext context) {
		if (request.getId() == null)
			throw new GwtRpcException("No distribution preference provided.");
		context.checkPermission(request.getId(), "DistributionPref", Right.ExaminationDistributionPreferenceDelete);

		DistributionPrefDAO dpDao = DistributionPrefDAO.getInstance();
		Transaction tx = null;
		org.hibernate.Session hibSession = dpDao.getSession();
		try {
			tx = hibSession.beginTransaction();

			Set<Exam> relatedExams = new HashSet<Exam>();
			DistributionPref dp = dpDao.get(request.getId(), hibSession);
			if (dp == null)
				throw new GwtRpcException("The distribution preference no longer exists.");
			PreferenceGroup owner = (PreferenceGroup) dp.getOwner();
			owner.getPreferences().remove(dp);
			for (Iterator<DistributionObject> i = dp.getDistributionObjects().iterator(); i.hasNext();) {
				DistributionObject dObj = i.next();
				PreferenceGroup pg = dObj.getPrefGroup();
				if (pg instanceof Exam) relatedExams.add((Exam) pg);
				pg.getDistributionObjects().remove(dObj);
				hibSession.merge(pg);
			}

			hibSession.remove(dp);
			hibSession.merge(owner);

			for (Exam exam : relatedExams) {
				ChangeLog.addChange(hibSession, context, exam, ChangeLog.Source.DIST_PREF_EDIT,
						ChangeLog.Operation.DELETE, exam.firstSubjectArea(), exam.firstDepartment());
			}

			tx.commit();
			hibSession.flush();
			hibSession.refresh(owner);
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}
}
