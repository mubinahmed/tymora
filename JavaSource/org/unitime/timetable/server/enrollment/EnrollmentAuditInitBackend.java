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

import java.util.TreeSet;

import org.unitime.timetable.form.EnrollmentAuditPdfReportForm;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface.EnrollmentAuditInitRequest;
import org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface.EnrollmentAuditInitResponse;
import org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface.Option;
import org.unitime.timetable.gwt.shared.EnrollmentAuditReportInterface.SubjectAreaOption;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.dao.SubjectAreaDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.ComboBoxLookup;

/**
 * Init backend for the Enrollment Audit PDF Reports screen (legacy enrollmentAuditPdfReport.action
 * load). Returns the registered reports and output modes (reusing {@link EnrollmentAuditPdfReportForm}
 * for their localized labels) plus this academic session's subject areas. Gated by
 * {@link Right#EnrollmentAuditPDFReports}. Additive.
 *
 * @author Angular migration
 */
@GwtRpcImplements(EnrollmentAuditInitRequest.class)
public class EnrollmentAuditInitBackend implements GwtRpcImplementation<EnrollmentAuditInitRequest, EnrollmentAuditInitResponse> {

	@Override
	@SuppressWarnings("unchecked")
	public EnrollmentAuditInitResponse execute(EnrollmentAuditInitRequest request, SessionContext context) {
		context.checkPermission(Right.EnrollmentAuditPDFReports);

		EnrollmentAuditInitResponse response = new EnrollmentAuditInitResponse();
		EnrollmentAuditPdfReportForm form = new EnrollmentAuditPdfReportForm();

		for (ComboBoxLookup r : form.getAllReports())
			response.addReport(new Option(r.getValue(), r.getLabel()));
		for (ComboBoxLookup m : form.getModes())
			response.addMode(new Option(m.getValue(), m.getLabel()));
		if (!form.getModes().isEmpty())
			response.setDefaultMode(form.getModes().get(0).getValue());

		TreeSet<SubjectArea> subjectAreas = new TreeSet<SubjectArea>(
				SubjectAreaDAO.getInstance().getSession().createQuery(
						"select distinct co.subjectArea from CourseOffering co where " +
						"co.subjectArea.session.uniqueId=:sessionId", SubjectArea.class)
						.setParameter("sessionId", context.getUser().getCurrentAcademicSessionId())
						.setCacheable(true).list());
		for (SubjectArea sa : subjectAreas) {
			SubjectAreaOption o = new SubjectAreaOption();
			o.setId(sa.getUniqueId());
			o.setAbbreviation(sa.getSubjectAreaAbbreviation());
			o.setTitle(sa.getTitle());
			response.addSubjectArea(o);
		}

		return response;
	}
}
