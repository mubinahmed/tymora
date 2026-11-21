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
 * Synchronous import of a students / studentEnrollments XML file into the
 * current academic session (Angular "Student Import" screen). The XML is the
 * same format the legacy Data Exchange page accepts; only the student related
 * roots are allowed here.
 *
 * @author Angular migration
 */
public class StudentImportInterface implements IsSerializable {

	public static class StudentImportRequest implements GwtRpcRequest<StudentImportResponse> {
		private String iFileName;
		private String iContent;
		private Boolean iIncremental;

		public StudentImportRequest() {}

		public String getFileName() { return iFileName; }
		public void setFileName(String fileName) { iFileName = fileName; }
		public String getContent() { return iContent; }
		public void setContent(String content) { iContent = content; }
		/** Incremental import (students / enrollments not in the file are kept). Defaults to true when not set. */
		public boolean isIncremental() { return iIncremental == null || iIncremental.booleanValue(); }
		public void setIncremental(Boolean incremental) { iIncremental = incremental; }

		@Override
		public String toString() { return "StudentImport[" + iFileName + "," + (iContent == null ? 0 : iContent.length()) + "]"; }
	}

	public static class StudentImportResponse implements GwtRpcResponse {
		private boolean iSuccess;
		private String iMessage;
		private String iRootElement;
		private boolean iIncremental;
		private String iSessionLabel;
		private long iStudentsBefore;
		private long iStudentsAfter;
		private long iEnrollmentsBefore;
		private long iEnrollmentsAfter;
		private int iWarnings;
		private int iErrors;
		private boolean iLogTruncated;
		private List<String> iLog = new ArrayList<String>();

		public StudentImportResponse() {}

		public boolean isSuccess() { return iSuccess; }
		public void setSuccess(boolean success) { iSuccess = success; }
		public String getMessage() { return iMessage; }
		public void setMessage(String message) { iMessage = message; }
		public String getRootElement() { return iRootElement; }
		public void setRootElement(String rootElement) { iRootElement = rootElement; }
		public boolean isIncremental() { return iIncremental; }
		public void setIncremental(boolean incremental) { iIncremental = incremental; }
		public String getSessionLabel() { return iSessionLabel; }
		public void setSessionLabel(String sessionLabel) { iSessionLabel = sessionLabel; }
		public long getStudentsBefore() { return iStudentsBefore; }
		public void setStudentsBefore(long studentsBefore) { iStudentsBefore = studentsBefore; }
		public long getStudentsAfter() { return iStudentsAfter; }
		public void setStudentsAfter(long studentsAfter) { iStudentsAfter = studentsAfter; }
		public long getEnrollmentsBefore() { return iEnrollmentsBefore; }
		public void setEnrollmentsBefore(long enrollmentsBefore) { iEnrollmentsBefore = enrollmentsBefore; }
		public long getEnrollmentsAfter() { return iEnrollmentsAfter; }
		public void setEnrollmentsAfter(long enrollmentsAfter) { iEnrollmentsAfter = enrollmentsAfter; }
		public int getWarnings() { return iWarnings; }
		public void setWarnings(int warnings) { iWarnings = warnings; }
		public int getErrors() { return iErrors; }
		public void setErrors(int errors) { iErrors = errors; }
		public boolean isLogTruncated() { return iLogTruncated; }
		public void setLogTruncated(boolean logTruncated) { iLogTruncated = logTruncated; }
		public List<String> getLog() { return iLog; }
		public void addLog(String line) { iLog.add(line); }
	}
}
