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
package org.unitime.timetable.server.enrollment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.form.EnrollmentAuditPdfReportForm;
import org.unitime.timetable.form.EnrollmentAuditPdfReportForm.RegisteredReport;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface.EnrollmentAuditGenerateRequest;
import org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface.EnrollmentAuditReportResponse;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.SubjectAreaDAO;
import org.unitime.timetable.reports.AbstractReport;
import org.unitime.timetable.reports.enrollment.PdfEnrollmentAuditReport;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Generate backend for the Enrollment Audit PDF Reports screen (legacy
 * enrollmentAuditPdfReport.action op=Generate). Runs the selected registered audit reports for the
 * current academic session — either whole-session or restricted to selected subject areas — into
 * temp files, exactly as the legacy action does (same report classes, constructors and show-id /
 * show-name flags). A single generated file is returned directly; multiple files are zipped. The
 * bytes are returned to the browser for download. E-mail delivery is not migrated. Gated by
 * {@link Right#EnrollmentAuditPDFReports}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(EnrollmentAuditGenerateRequest.class)
public class EnrollmentAuditGenerateBackend implements GwtRpcImplementation<EnrollmentAuditGenerateRequest, EnrollmentAuditReportResponse> {

	@Override
	public EnrollmentAuditReportResponse execute(EnrollmentAuditGenerateRequest request, SessionContext context) {
		context.checkPermission(Right.EnrollmentAuditPDFReports);

		if (request.getReports() == null || request.getReports().isEmpty())
			throw new GwtRpcException("No report was selected.");
		if (!request.isAll() && (request.getSubjectIds() == null || request.getSubjectIds().isEmpty()))
			throw new GwtRpcException("No subject area was selected.");

		Session session = SessionDAO.getInstance().get(context.getUser().getCurrentAcademicSessionId());
		if (session == null)
			throw new GwtRpcException("No academic session is selected.");

		AbstractReport.Mode mode;
		try {
			mode = AbstractReport.Mode.valueOf(request.getMode());
		} catch (Exception e) {
			mode = AbstractReport.Mode.LegacyPdfLetter;
		}
		int modeOrd = mode.ordinal();
		String ext = AbstractReport.getExtension(modeOrd);

		EnrollmentAuditPdfReportForm form = new EnrollmentAuditPdfReportForm();
		Map<String, File> output = new LinkedHashMap<String, File>();

		try {
			for (String reportKey : request.getReports()) {
				RegisteredReport rr = RegisteredReport.valueOf(reportKey);
				@SuppressWarnings("rawtypes")
				Class reportClass = rr.getImplementation();
				String reportName = form.getReportName(rr);
				String name = session.getAcademicTerm() + session.getAcademicYear() + "_" + reportKey;

				if (request.isAll()) {
					File file = ApplicationProperties.getTempFile(name, ext);
					PdfEnrollmentAuditReport report = (PdfEnrollmentAuditReport) reportClass
							.getConstructor(int.class, File.class, Session.class)
							.newInstance(modeOrd, file, session);
					report.setShowId(request.isShowExternalId());
					report.setShowName(request.isShowStudentName());
					report.printReport();
					report.close();
					output.put(reportName + ext, file);
				} else {
					TreeSet<SubjectArea> subjectAreas = new TreeSet<SubjectArea>();
					String subjAbbvs = "";
					for (Long subjectId : request.getSubjectIds()) {
						SubjectArea subject = SubjectAreaDAO.getInstance().get(subjectId);
						if (subject == null) continue;
						if (subjAbbvs.length() == 0)
							subjAbbvs = subject.getSubjectAreaAbbreviation();
						else if (subjAbbvs.length() < 40)
							subjAbbvs += "_" + subject.getSubjectAreaAbbreviation();
						else if (subjAbbvs.charAt(subjAbbvs.length() - 1) != '.')
							subjAbbvs += "_...";
						subjectAreas.add(subject);
					}
					File file = ApplicationProperties.getTempFile(name + subjAbbvs, ext);
					PdfEnrollmentAuditReport report = (PdfEnrollmentAuditReport) reportClass
							.getConstructor(int.class, File.class, Session.class, TreeSet.class, String.class)
							.newInstance(modeOrd, file, session, subjectAreas, subjAbbvs);
					report.setShowId(request.isShowExternalId());
					report.setShowName(request.isShowStudentName());
					report.printReport();
					report.close();
					output.put(subjAbbvs + "_" + reportName + ext, file);
				}
			}

			if (output.isEmpty())
				throw new GwtRpcException("No report was generated.");

			EnrollmentAuditReportResponse response = new EnrollmentAuditReportResponse();
			if (output.size() == 1) {
				Map.Entry<String, File> only = output.entrySet().iterator().next();
				response.setFileName(only.getKey());
				response.setContentType(contentType(ext));
				response.setContent(Files.readAllBytes(only.getValue().toPath()));
			} else {
				String zipName = session.getAcademicTerm() + session.getAcademicYear() + ".zip";
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(baos);
				byte[] buffer = new byte[32 * 1024];
				for (Map.Entry<String, File> entry : output.entrySet()) {
					zip.putNextEntry(new ZipEntry(entry.getKey()));
					FileInputStream fis = new FileInputStream(entry.getValue());
					int len;
					while ((len = fis.read(buffer)) > 0) zip.write(buffer, 0, len);
					fis.close();
					zip.closeEntry();
				}
				zip.finish();
				zip.close();
				response.setFileName(zipName);
				response.setContentType("application/zip");
				response.setContent(baos.toByteArray());
			}
			return response;
		} catch (GwtRpcException e) {
			throw e;
		} catch (Exception e) {
			throw new GwtRpcException("Unable to generate report: " + e.getMessage(), e);
		}
	}

	private static String contentType(String ext) {
		if (".pdf".equals(ext)) return "application/pdf";
		if (".csv".equals(ext)) return "text/csv";
		if (".xls".equals(ext)) return "application/vnd.ms-excel";
		if (".txt".equals(ext)) return "text/plain";
		return "application/octet-stream";
	}
}
