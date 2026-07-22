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
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Enrollment Audit PDF Reports (legacy enrollmentAuditPdfReport.action) migrated to GwtRpc
 * command beans. {@code EnrollmentAuditInitRequest} loads the option lists (the four registered
 * audit reports, the output modes, and this session's subject areas); {@code
 * EnrollmentAuditGenerateRequest} runs the selected reports server-side and returns the resulting
 * file (a single report file, or a zip when several are produced) as bytes for the browser to
 * download. The e-mail delivery path from the legacy screen is not migrated. Additive.
 *
 * @author Angular migration
 */
public class EnrollmentAuditReportInterface implements IsSerializable {

	public static class Option implements IsSerializable {
		private String iValue;
		private String iLabel;
		public Option() {}
		public Option(String value, String label) { iValue = value; iLabel = label; }
		public String getValue() { return iValue; }
		public void setValue(String value) { iValue = value; }
		public String getLabel() { return iLabel; }
		public void setLabel(String label) { iLabel = label; }
	}

	public static class SubjectAreaOption implements IsSerializable {
		private Long iId;
		private String iAbbreviation;
		private String iTitle;
		public SubjectAreaOption() {}
		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }
		public String getAbbreviation() { return iAbbreviation; }
		public void setAbbreviation(String abbreviation) { iAbbreviation = abbreviation; }
		public String getTitle() { return iTitle; }
		public void setTitle(String title) { iTitle = title; }
	}

	public static class EnrollmentAuditInitResponse implements GwtRpcResponse {
		private List<Option> iReports = new ArrayList<Option>();
		private List<Option> iModes = new ArrayList<Option>();
		private List<SubjectAreaOption> iSubjectAreas = new ArrayList<SubjectAreaOption>();
		private String iDefaultMode;

		public EnrollmentAuditInitResponse() {}

		public List<Option> getReports() { return iReports; }
		public void addReport(Option report) { iReports.add(report); }
		public List<Option> getModes() { return iModes; }
		public void addMode(Option mode) { iModes.add(mode); }
		public List<SubjectAreaOption> getSubjectAreas() { return iSubjectAreas; }
		public void addSubjectArea(SubjectAreaOption sa) { iSubjectAreas.add(sa); }
		public String getDefaultMode() { return iDefaultMode; }
		public void setDefaultMode(String defaultMode) { iDefaultMode = defaultMode; }
	}

	public static class EnrollmentAuditInitRequest implements GwtRpcRequest<EnrollmentAuditInitResponse> {
		public EnrollmentAuditInitRequest() {}
		@Override public String toString() { return "EnrollmentAuditInit[]"; }
	}

	public static class EnrollmentAuditReportResponse implements GwtRpcResponse {
		private String iFileName;
		private String iContentType;
		private byte[] iContent;

		public EnrollmentAuditReportResponse() {}

		public String getFileName() { return iFileName; }
		public void setFileName(String fileName) { iFileName = fileName; }
		public String getContentType() { return iContentType; }
		public void setContentType(String contentType) { iContentType = contentType; }
		public byte[] getContent() { return iContent; }
		public void setContent(byte[] content) { iContent = content; }
	}

	public static class EnrollmentAuditGenerateRequest implements GwtRpcRequest<EnrollmentAuditReportResponse> {
		private List<String> iReports = new ArrayList<String>();
		private String iMode;
		private boolean iAll = true;
		private List<Long> iSubjectIds = new ArrayList<Long>();
		private boolean iShowExternalId = false;
		private boolean iShowStudentName = false;

		public EnrollmentAuditGenerateRequest() {}

		public List<String> getReports() { return iReports; }
		public void setReports(List<String> reports) { iReports = reports; }
		public String getMode() { return iMode; }
		public void setMode(String mode) { iMode = mode; }
		public boolean isAll() { return iAll; }
		public void setAll(boolean all) { iAll = all; }
		public List<Long> getSubjectIds() { return iSubjectIds; }
		public void setSubjectIds(List<Long> subjectIds) { iSubjectIds = subjectIds; }
		public boolean isShowExternalId() { return iShowExternalId; }
		public void setShowExternalId(boolean showExternalId) { iShowExternalId = showExternalId; }
		public boolean isShowStudentName() { return iShowStudentName; }
		public void setShowStudentName(boolean showStudentName) { iShowStudentName = showStudentName; }
		@Override public String toString() { return "EnrollmentAuditGenerate[" + iReports + "]"; }
	}
}
