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
package org.unitime.timetable.server.admin;

import java.io.StringReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.unitime.timetable.dataexchange.DataExchangeHelper;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.StudentImportInterface.StudentImportRequest;
import org.unitime.timetable.gwt.shared.StudentImportInterface.StudentImportResponse;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao._RootDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Synchronous student import for the Angular "Student Import" screen.
 * Accepts the XML text of a {@code <students>} or {@code <studentEnrollments>}
 * Data Exchange file, checks that it targets the current academic session and
 * runs it through {@link DataExchangeHelper#importDocument} (the same code path
 * the legacy Data Exchange page uses, gated by the same {@link Right#DataExchange}).
 * The import log and the student / enrollment counts of the session before and
 * after the import are returned.
 *
 * Unless the request explicitly asks for a full (non-incremental) import, the
 * root element's {@code incremental} attribute is forced to {@code true}, so
 * students (or enrollments) that are not in the file are kept.
 *
 * @author Angular migration
 */
@GwtRpcImplements(StudentImportRequest.class)
public class StudentImportBackend implements GwtRpcImplementation<StudentImportRequest, StudentImportResponse> {
	private static Log sLog = LogFactory.getLog(StudentImportBackend.class);
	private static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
	private static final int MAX_LOG_LINES = 5000;

	@Override
	public StudentImportResponse execute(StudentImportRequest request, SessionContext context) {
		context.checkPermission(Right.DataExchange);

		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		Session session = SessionDAO.getInstance().get(sessionId);
		if (session == null)
			throw new GwtRpcException("The current academic session does not exist.");
		String sessionLabel = session.getAcademicTerm() + " " + session.getAcademicYear() + " (" + session.getAcademicInitiative() + ")";

		String content = request.getContent();
		if (content == null || content.trim().isEmpty())
			throw new GwtRpcException("No file content was provided.");
		if (content.length() > MAX_CONTENT_LENGTH)
			throw new GwtRpcException("The file is too large (limit is 10 MB). Use the Data Exchange page for large files.");

		Document document;
		try {
			SAXReader reader = SAXReader.createDefault();
			try {
				reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			} catch (Exception e) {}
			document = reader.read(new StringReader(content));
		} catch (DocumentException e) {
			throw new GwtRpcException("The file is not a valid XML document: " + e.getMessage());
		}

		Element root = document.getRootElement();
		String rootName = root.getName();
		if (!"studentEnrollments".equals(rootName) && !"students".equals(rootName))
			throw new GwtRpcException("Unsupported file: the root element is <" + rootName + ">, but only <students> and <studentEnrollments> files can be imported here. Use the Data Exchange page for other imports.");

		String campus = root.attributeValue("campus");
		String year = root.attributeValue("year");
		String term = root.attributeValue("term");
		if (campus == null || year == null || term == null)
			throw new GwtRpcException("The <" + rootName + "> element must have campus, year and term attributes (current session: campus=\""
					+ session.getAcademicInitiative() + "\" year=\"" + session.getAcademicYear() + "\" term=\"" + session.getAcademicTerm() + "\").");
		if (!campus.equals(session.getAcademicInitiative()) || !year.equals(session.getAcademicYear()) || !term.equals(session.getAcademicTerm()))
			throw new GwtRpcException("The file is for campus=\"" + campus + "\" year=\"" + year + "\" term=\"" + term + "\", but the current academic session is "
					+ "campus=\"" + session.getAcademicInitiative() + "\" year=\"" + session.getAcademicYear() + "\" term=\"" + session.getAcademicTerm()
					+ "\". Switch to the matching session or fix the attributes in the file.");

		boolean incremental = request.isIncremental();
		root.addAttribute("incremental", incremental ? "true" : "false");

		StudentImportResponse response = new StudentImportResponse();
		response.setRootElement(rootName);
		response.setIncremental(incremental);
		response.setSessionLabel(sessionLabel);
		response.setStudentsBefore(countStudents(sessionId));
		response.setEnrollmentsBefore(countEnrollments(sessionId));

		ResponseLog log = new ResponseLog(response);
		log.info("Importing " + (request.getFileName() == null ? "file" : request.getFileName()) + " (<" + rootName + ">, "
				+ (incremental ? "incremental" : "full") + " import) into " + sessionLabel + "...");
		try {
			DataExchangeHelper.importDocument(document, context.getUser().getExternalUserId(), log);
			response.setSuccess(true);
		} catch (Exception e) {
			sLog.warn("Student import failed: " + e.getMessage(), e);
			log.error("Import failed: " + e.getMessage());
			response.setSuccess(false);
		}

		response.setStudentsAfter(countStudents(sessionId));
		response.setEnrollmentsAfter(countEnrollments(sessionId));

		if (response.isSuccess()) {
			response.setMessage("Import finished: " + response.getStudentsAfter() + " students (" + signed(response.getStudentsAfter() - response.getStudentsBefore())
					+ ") and " + response.getEnrollmentsAfter() + " class enrollments (" + signed(response.getEnrollmentsAfter() - response.getEnrollmentsBefore())
					+ ") in " + sessionLabel + "." + (response.getWarnings() > 0 ? " " + response.getWarnings() + " warning(s), see the log." : ""));
		} else {
			response.setMessage("Import failed, no changes were saved. See the log for details.");
		}
		return response;
	}

	private static String signed(long delta) {
		return (delta >= 0 ? "+" : "") + delta;
	}

	private static long countStudents(Long sessionId) {
		org.hibernate.Session hibSession = new _RootDAO().createNewSession();
		try {
			Long count = hibSession.createQuery(
					"select count(s) from Student s where s.session.uniqueId = :sessionId", Long.class)
					.setParameter("sessionId", sessionId).uniqueResult();
			return count == null ? 0l : count.longValue();
		} finally {
			hibSession.close();
		}
	}

	private static long countEnrollments(Long sessionId) {
		org.hibernate.Session hibSession = new _RootDAO().createNewSession();
		try {
			Long count = hibSession.createQuery(
					"select count(e) from StudentClassEnrollment e where e.student.session.uniqueId = :sessionId", Long.class)
					.setParameter("sessionId", sessionId).uniqueResult();
			return count == null ? 0l : count.longValue();
		} finally {
			hibSession.close();
		}
	}

	/**
	 * Commons-logging {@link Log} that collects info / warn / error / fatal lines
	 * into the response (debug and trace are ignored).
	 */
	private static class ResponseLog implements Log {
		private StudentImportResponse iResponse;

		ResponseLog(StudentImportResponse response) { iResponse = response; }

		private void add(String level, Object message, Throwable t) {
			String line = level + ": " + (message == null ? "" : message.toString());
			if (t != null && t.getMessage() != null && (message == null || !message.toString().contains(t.getMessage())))
				line += " (" + t.getMessage() + ")";
			if (iResponse.getLog().size() < MAX_LOG_LINES)
				iResponse.addLog(line);
			else
				iResponse.setLogTruncated(true);
		}

		@Override
		public boolean isDebugEnabled() { return false; }
		@Override
		public boolean isErrorEnabled() { return true; }
		@Override
		public boolean isFatalEnabled() { return true; }
		@Override
		public boolean isInfoEnabled() { return true; }
		@Override
		public boolean isTraceEnabled() { return false; }
		@Override
		public boolean isWarnEnabled() { return true; }

		@Override
		public void trace(Object message) {}
		@Override
		public void trace(Object message, Throwable t) {}
		@Override
		public void debug(Object message) {}
		@Override
		public void debug(Object message, Throwable t) {}
		@Override
		public void info(Object message) { add("INFO", message, null); }
		@Override
		public void info(Object message, Throwable t) { add("INFO", message, t); }
		@Override
		public void warn(Object message) { iResponse.setWarnings(iResponse.getWarnings() + 1); add("WARN", message, null); }
		@Override
		public void warn(Object message, Throwable t) { iResponse.setWarnings(iResponse.getWarnings() + 1); add("WARN", message, t); }
		@Override
		public void error(Object message) { iResponse.setErrors(iResponse.getErrors() + 1); add("ERROR", message, null); }
		@Override
		public void error(Object message, Throwable t) { iResponse.setErrors(iResponse.getErrors() + 1); add("ERROR", message, t); }
		@Override
		public void fatal(Object message) { iResponse.setErrors(iResponse.getErrors() + 1); add("FATAL", message, null); }
		@Override
		public void fatal(Object message, Throwable t) { iResponse.setErrors(iResponse.getErrors() + 1); add("FATAL", message, t); }
	}
}
