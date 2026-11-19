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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.commons.web.WebTable;
import org.unitime.timetable.action.ExamAssignmentReportAction;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.form.ExamAssignmentReportForm;
import org.unitime.timetable.form.ExamAssignmentReportForm.ExamReport;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ExamAssignmentReportRequest;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ExamAssignmentReportResponse;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ReportInfo;
import org.unitime.timetable.gwt.shared.ExamAssignmentReportInterface.ReportRow;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo;
import org.unitime.timetable.solver.service.ExaminationSolverService;
import org.unitime.timetable.webutil.PdfWebTable;

/**
 * Backend of the Angular Examination Reports page (legacy examAssignmentReport.action).
 *
 * It runs the legacy page's own report generators (ExamAssignmentReportAction.getTable), so all
 * 23 reports have exactly the legacy content, including the student and instructor clash reports.
 * The generated table is returned as plain-text cells, a sort rank per column (from the legacy
 * comparators) and the examination each row links to. As on the legacy page, the data comes from a
 * loaded examination solver of the selected type when there is one, otherwise from the committed
 * assignments; a subject area (or All, with Right.DepartmentIndependent) must be chosen.
 * Permission: Right.ExaminationReports, as the legacy action.
 */
@GwtRpcImplements(ExamAssignmentReportRequest.class)
public class ExamAssignmentReportBackend implements GwtRpcImplementation<ExamAssignmentReportRequest, ExamAssignmentReportResponse> {
	private static Logger sLog = LogManager.getLogger(ExamAssignmentReportBackend.class);
	private static final Pattern EXAM_LINK = Pattern.compile("examId=([0-9]+)");

	@Autowired ExaminationSolverService examinationSolverService;

	/** Gives the legacy generators the RPC session context instead of a Struts request. */
	private static class Generator extends ExamAssignmentReportAction {
		private static final long serialVersionUID = 1L;
		Generator(SessionContext context) { this.sessionContext = context; }
	}

	@Override
	public ExamAssignmentReportResponse execute(ExamAssignmentReportRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		context.checkPermission(Right.ExaminationReports);

		ExamAssignmentReportForm form = new ExamAssignmentReportForm();
		form.reset();
		ExamAssignmentReportResponse response = new ExamAssignmentReportResponse();
		response.setTitle("Examination Reports");

		// reports, grouped as on the legacy page
		for (ExamReport r: ExamReport.values())
			response.addReport(new ReportInfo(r.name(), form.getReportName(r), group(r)));
		String report = request.getReport();
		try { ExamReport.valueOf(report); } catch (Exception e) { report = ExamReport.ExamAssignmentReport.name(); }
		response.setReport(report);

		// examination types
		List<ExamType> types = ExamType.findAllApplicable(context.getUser(), DepartmentStatusType.Status.ExamView, DepartmentStatusType.Status.ExamTimetable);
		for (ExamType t: types) response.addExamType(new ExamTypeInfo(t.getUniqueId(), t.getLabel()));
		ExamSolverProxy solver = null;
		try { solver = examinationSolverService.getSolver(); } catch (Exception e) {}
		Long examTypeId = request.getExamTypeId();
		if (examTypeId == null && solver != null) examTypeId = solver.getExamTypeId();
		if (examTypeId == null) examTypeId = (Long)context.getAttribute("Exam.Type");
		boolean ok = false;
		for (ExamType t: types) if (t.getUniqueId().equals(examTypeId)) { ok = true; break; }
		if (!ok) examTypeId = (types.isEmpty() ? null : types.get(0).getUniqueId());
		response.setExamTypeId(examTypeId);

		// subject areas (as ExamReportForm.load): All for department-independent users, then the user's subject areas
		boolean canSeeAll = context.hasPermission(Right.DepartmentIndependent);
		if (canSeeAll) response.addSubjectArea(new ExamTypeInfo(-1l, "All"));
		TreeSet<SubjectArea> subjects = SubjectArea.getUserSubjectAreas(context.getUser(), false);
		for (SubjectArea sa: subjects) response.addSubjectArea(new ExamTypeInfo(sa.getUniqueId(), sa.getSubjectAreaAbbreviation()));
		Long subjectAreaId = request.getSubjectAreaId();
		if (subjectAreaId == null) subjectAreaId = (Long)context.getAttribute("ExamReport.subjectArea");
		if (subjectAreaId == null) subjectAreaId = (canSeeAll ? -1l : subjects.size() == 1 ? subjects.first().getUniqueId() : null);
		boolean subjectOk = false;
		for (ExamTypeInfo sa: response.getSubjectAreas()) if (sa.getId().equals(subjectAreaId)) { subjectOk = true; break; }
		if (!subjectOk) subjectAreaId = null;
		response.setSubjectAreaId(subjectAreaId);

		boolean showSections = (request.getShowSections() != null ? request.getShowSections() : "1".equals(context.getUser().getProperty("ExamReport.showSections", "1")));
		response.setShowSections(showSections);
		String filter = (request.getFilter() == null ? "" : request.getFilter().trim());
		response.setFilter(filter);

		form.setReport(report);
		form.setExamType(examTypeId);
		form.setSubjectArea(subjectAreaId);
		form.setShowSections(showSections);
		form.setFilter(filter);
		form.setCanSeeAll(canSeeAll);
		form.save(context); // remember the choices, like the legacy page

		if (examTypeId == null) { response.setMessage("No examination type is available."); return response; }
		if (subjectAreaId == null) { response.setMessage("Select a subject area."); return response; }

		Collection<ExamAssignmentInfo> exams;
		try {
			if (solver != null && solver.getExamTypeId().equals(examTypeId)) {
				exams = solver.getAssignedExams(subjectAreaId);
				response.setSolverData(true);
			} else if (ApplicationProperty.ExaminationCacheConflicts.isTrue() && subjectAreaId > 0) {
				exams = Exam.findAssignedExams(sessionId, subjectAreaId, examTypeId);
			} else {
				exams = ExamAssignmentReportAction.findAssignedExams(sessionId, subjectAreaId, examTypeId);
			}
		} catch (Exception e) {
			sLog.error("Failed to load assigned examinations: " + e.getMessage(), e);
			throw new GwtRpcException("Failed to load the examinations: " + e.getMessage(), e);
		}
		response.setNrExams(exams == null ? 0 : exams.size());
		if (exams == null || exams.isEmpty()) { response.setMessage("No assigned examinations match the selection."); return response; }

		PdfWebTable table = new Generator(context).getTable(sessionId, false, form, exams);
		if (table == null) { response.setMessage("Nothing to report: no rows for this selection."); return response; }
		convert(table, response);
		if (response.getRows().isEmpty()) response.setMessage("Nothing to report" + (filter.isEmpty() ? "." : " for the filter."));
		return response;
	}

	private static String group(ExamReport r) {
		String n = r.name();
		if (n.contains("StudentConflicts")) return "Student conflicts";
		if (n.contains("InstructorConflicts")) return "Instructor conflicts";
		if (n.startsWith("Individual")) return "Individual schedules";
		return "Assignments and statistics";
	}

	/** WebTable -> header, alignment, rows with plain-text cells, exam link and per-column sort ranks. */
	@SuppressWarnings("unchecked")
	protected static void convert(WebTable table, ExamAssignmentReportResponse response) {
		String[] headers = table.getHeader();
		String[] align = (String[])field(table, "iAlign");
		int nrColumns = table.getNrColumns();
		for (int c = 0; c < nrColumns; c++) {
			response.addColumn(headers == null || c >= headers.length || headers[c] == null ? "" : headers[c].replace("\n", " ").replace("<br>", " "));
			response.getAlignments().add(align == null || c >= align.length || align[c] == null ? "left" : align[c]);
		}
		List<WebTable.WebTableLine> lines = new ArrayList<WebTable.WebTableLine>((Collection<WebTable.WebTableLine>)field(table, "iLines"));
		List<ReportRow> rows = new ArrayList<ReportRow>();
		for (WebTable.WebTableLine line: lines) {
			ReportRow row = new ReportRow();
			String[] cells = line.getLine();
			for (int c = 0; c < nrColumns; c++) row.getCells().add(cells == null || c >= cells.length || cells[c] == null ? "" : cells[c]);
			if (line.getOnClick() != null) {
				Matcher m = EXAM_LINK.matcher(line.getOnClick());
				if (m.find()) row.setExamId(Long.valueOf(m.group(1)));
			}
			rows.add(row);
		}
		// sort rank per column with the legacy comparators (so numbers, dates and periods sort correctly)
		for (int c = 0; c < nrColumns; c++) {
			final int col = c;
			List<Integer> idx = new ArrayList<Integer>();
			for (int i = 0; i < lines.size(); i++) idx.add(i);
			try {
				Collections.sort(idx, new Comparator<Integer>() {
					@Override
					public int compare(Integer a, Integer b) { return lines.get(a).compareTo(lines.get(b), col, true); }
				});
			} catch (Exception e) {
				sLog.debug("Column " + col + " is not sortable: " + e.getMessage());
			}
			int[] rank = new int[lines.size()];
			for (int r = 0; r < idx.size(); r++) rank[idx.get(r)] = r;
			for (int i = 0; i < rows.size(); i++) rows.get(i).getRanks().add(rank[i]);
		}
		// default order: first column ascending, as the legacy page
		Collections.sort(rows, new Comparator<ReportRow>() {
			@Override
			public int compare(ReportRow a, ReportRow b) {
				return Integer.compare(a.getRanks().isEmpty() ? 0 : a.getRanks().get(0), b.getRanks().isEmpty() ? 0 : b.getRanks().get(0));
			}
		});
		response.getRows().addAll(rows);
	}

	private static Object field(Object o, String name) {
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
}
