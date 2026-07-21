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

import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.unitime.localization.impl.Localization;
import org.unitime.localization.messages.ExaminationMessages;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.form.ExamPdfReportForm;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamPdfReportInterface.ComboItem;
import org.unitime.timetable.gwt.shared.ExamPdfReportInterface.ExamPdfReportRequest;
import org.unitime.timetable.gwt.shared.ExamPdfReportInterface.ExamPdfReportResponse;
import org.unitime.timetable.gwt.shared.ExamPdfReportInterface.IdLabel;
import org.unitime.timetable.gwt.shared.ExamPdfReportInterface.Operation;
import org.unitime.timetable.gwt.shared.ExamPdfReportInterface.QueueRow;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.service.SolverService;
import org.unitime.timetable.util.ComboBoxLookup;
import org.unitime.timetable.util.Formats;
import org.unitime.timetable.util.queue.PdfExamReportQueueItem;
import org.unitime.timetable.util.queue.QueueItem;

/**
 * Backing bean for the legacy examPdfReport.action (Examination PDF Reports)
 * Struts page. Reuses the existing asynchronous report infrastructure: GENERATE
 * builds an {@link ExamPdfReportForm} from the request and enqueues a
 * {@link PdfExamReportQueueItem} on the solver-server queue processor (exactly
 * as {@code ExamPdfReportAction} does); LOAD returns the report / format /
 * examination-type / subject-area selectors, the saved per-user defaults and the
 * current queue rows; REMOVE deletes a queue item. Report generation itself is
 * unchanged (the queue item does the work); the output is downloaded via the
 * queue item's existing output link.
 *
 * Gated by {@link Right#ExaminationPdfReports} (Session qualified). E-mail
 * delivery is deferred to the legacy page. Additive: introduces no changes to
 * existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamPdfReportRequest.class)
public class ExamPdfReportBackend implements GwtRpcImplementation<ExamPdfReportRequest, ExamPdfReportResponse> {
	protected static final ExaminationMessages MSG = Localization.create(ExaminationMessages.class);

	@Autowired SolverService<ExamSolverProxy> examinationSolverService;
	@Autowired org.unitime.timetable.solver.service.SolverServerService solverServerService;

	@Override
	public ExamPdfReportResponse execute(ExamPdfReportRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.ExaminationPdfReports);

		switch (request.getOperation() == null ? Operation.LOAD : request.getOperation()) {
		case GENERATE:
			generate(request, context, sessionId);
			break;
		case REMOVE:
			if (request.getRemoveId() != null && !request.getRemoveId().isEmpty())
				solverServerService.getQueueProcessor().remove(request.getRemoveId());
			break;
		case LOAD:
		default:
			break;
		}
		return load(request, context, sessionId);
	}

	// ---- LOAD ---------------------------------------------------------------

	protected ExamPdfReportResponse load(ExamPdfReportRequest request, SessionContext context, Long sessionId) {
		ExamPdfReportResponse response = new ExamPdfReportResponse();
		response.setTitle("Examination PDF Reports");

		ExamSolverProxy solver = examinationSolverService.getSolver();
		if (solver != null)
			response.setWarning(ApplicationProperty.ExaminationPdfReportsCanUseSolution.isTrue()
					? MSG.warnExamPdfReportsUsingSolution() : MSG.warnEamPdfReportsUsingSaved());

		// Load per-user defaults through the legacy form (also builds subject-area list).
		ExamPdfReportForm form = new ExamPdfReportForm();
		form.reset();
		form.load(context);

		// Applicable examination types (mirror LookupTables.setupExamTypes selector).
		List<ExamType> types = ExamType.findAllApplicable(context.getUser(),
				DepartmentStatusType.Status.ExamView, DepartmentStatusType.Status.ExamTimetable);
		for (ExamType t : types)
			response.addExamType(new IdLabel(t.getUniqueId(), t.getLabel()));

		Long examTypeId = request.getExamTypeId();
		if (examTypeId == null && solver != null) examTypeId = solver.getExamTypeId();
		if (examTypeId != null) {
			boolean ok = false;
			for (ExamType t : types)
				if (t.getUniqueId().equals(examTypeId)) { ok = true; break; }
			if (!ok) examTypeId = null;
		}
		if (examTypeId == null && !types.isEmpty())
			examTypeId = types.get(0).getUniqueId();
		response.setExamTypeId(examTypeId);

		for (ComboBoxLookup r : form.getAllReports())
			response.addReport(new ComboItem(r.getValue(), r.getLabel()));
		for (ComboBoxLookup m : form.getModes())
			response.addMode(new ComboItem(m.getValue(), m.getLabel()));

		for (SubjectArea sa : SubjectArea.getUserSubjectAreas(context.getUser(), false))
			response.addSubjectArea(new IdLabel(sa.getUniqueId(), sa.getSubjectAreaAbbreviation()));

		// Defaults.
		response.setMode(form.getMode());
		response.setAll(form.getAll());
		response.setDispRooms(form.getDispRooms());
		response.setDispLimit(form.getDispLimit());
		response.setTotals(form.getTotals());
		response.setDirect(form.getDirect());
		response.setM2d(form.getM2d());
		response.setBtb(form.getBtb());
		response.setItype(form.getItype());
		response.setClassSchedule(form.getClassSchedule());
		response.setIgnoreEmptyExams(form.getIgnoreEmptyExams());
		response.setDispNote(form.getDispNote());
		response.setCompact(form.getCompact());
		response.setRoomDispNames(form.getRoomDispNames());
		response.setLimit(form.getLimit());
		response.setRoomCodes(form.getRoomCodes());
		response.setNoRoom(form.getNoRoom());
		response.setSince(form.getSince());

		// Queue rows (mirror ExamPdfReportAction.getQueueTable).
		Formats.Format<Date> df = Formats.getDateFormat(Formats.Pattern.DATE_TIME_STAMP);
		String ownerId = null;
		if (!context.getUser().getCurrentAuthority().hasRight(Right.DepartmentIndependent))
			ownerId = context.getUser().getExternalUserId();
		List<QueueItem> queue = solverServerService.getQueueProcessor().getItems(ownerId, null, PdfExamReportQueueItem.TYPE);
		Date now = new Date();
		long timeToShow = 1000 * 60 * 60;
		for (QueueItem item : queue) {
			if (item.finished() != null && now.getTime() - item.finished().getTime() > timeToShow) continue;
			if (item.getSession() == null) continue;
			QueueRow row = new QueueRow();
			row.setId(item.getId().toString());
			String name = item.name();
			if (name != null && name.length() > 60) name = name.substring(0, 57) + "...";
			row.setName(name);
			row.setStatus(item.status());
			row.setProgress(item.progress() <= 0.0 || item.progress() >= 1.0 ? "" : String.valueOf(Math.round(100 * item.progress())) + "%");
			row.setOwner(item.getOwnerName());
			row.setSession(item.getSession().getLabel());
			row.setCreated(item.created() == null ? "" : df.format(item.created()));
			row.setStarted(item.started() == null ? "" : df.format(item.started()));
			row.setFinished(item.finished() == null ? "" : df.format(item.finished()));
			if (item.hasOutput()) {
				row.setOutput(item.getOutputName());
				row.setOutputLink(item.getOutputLink());
			}
			row.setLog(item.log());
			row.setCanDelete(context.getUser().getExternalUserId().equals(item.getOwnerId())
					&& (item.started() == null || item.finished() != null));
			response.addQueueRow(row);
		}

		return response;
	}

	// ---- GENERATE — port of ExamPdfReportAction generate branch -------------

	protected void generate(ExamPdfReportRequest request, SessionContext context, Long sessionId) {
		if (request.getReports() == null || request.getReports().isEmpty())
			throw new GwtRpcException(MSG.errorNoReportSelected());
		if (!request.isAll() && (request.getSubjects() == null || request.getSubjects().isEmpty()))
			throw new GwtRpcException(MSG.errorNoSubjectAreaSelected());
		if (request.getExamTypeId() == null)
			throw new GwtRpcException("Examination type is required.");
		if (request.getSince() != null && !request.getSince().isEmpty()
				&& !Formats.getDateFormat(Formats.Pattern.DATE_ENTRY_FORMAT).isValid(request.getSince()))
			throw new GwtRpcException(MSG.errorNotValidDate(request.getSince()));

		ExamSolverProxy solver = examinationSolverService.getSolver();

		ExamPdfReportForm form = new ExamPdfReportForm();
		form.reset();
		form.load(context);

		form.setExamType(request.getExamTypeId());
		form.setReports(request.getReports().toArray(new String[0]));
		if (request.getMode() != null) form.setMode(request.getMode());
		form.setAll(request.isAll());
		String[] subjects = new String[request.getSubjects() == null ? 0 : request.getSubjects().size()];
		for (int i = 0; i < subjects.length; i++) subjects[i] = String.valueOf(request.getSubjects().get(i));
		form.setSubjects(subjects);
		form.setDispRooms(request.isDispRooms());
		form.setDispLimit(request.isDispLimit());
		form.setTotals(request.isTotals());
		form.setDirect(request.isDirect());
		form.setM2d(request.isM2d());
		form.setBtb(request.isBtb());
		form.setItype(request.isItype());
		form.setClassSchedule(request.isClassSchedule());
		form.setIgnoreEmptyExams(request.isIgnoreEmptyExams());
		form.setDispNote(request.isDispNote());
		form.setCompact(request.isCompact());
		form.setRoomDispNames(request.isRoomDispNames());
		form.setLimit(request.getLimit());
		if (request.getRoomCodes() != null) form.setRoomCodes(request.getRoomCodes());
		if (request.getNoRoom() != null) form.setNoRoom(request.getNoRoom());
		form.setSince(request.getSince());
		// E-mail delivery is not offered here (deferred to the legacy page).
		form.setEmail(false);

		// Persist the chosen options as the user's defaults (mirror form.save on generate).
		form.save(context);

		Session session = SessionDAO.getInstance().get(sessionId);
		HttpServletRequest httpRequest = currentRequest();
		solverServerService.getQueueProcessor().add(new PdfExamReportQueueItem(
				session, context.getUser(), (ExamPdfReportForm) form.clone(), httpRequest, solver));
	}

	/** The current HTTP request, bound by Spring's RequestContextListener (web.xml). */
	private static HttpServletRequest currentRequest() {
		ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attr == null)
			throw new GwtRpcException("No HTTP request is bound to this thread; report generation cannot start.");
		return attr.getRequest();
	}
}
