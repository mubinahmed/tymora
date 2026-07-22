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

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamEditInterface.ExamEditResponse;
import org.unitime.timetable.gwt.shared.ExamEditInterface.ExamEditUpdateRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Edit Examination page — persists the exam's scalar fields
 * (name, note, length, seating type, size, print offset, max rooms), mirroring the direct
 * setters of {@code ExamEditAction}. Exam owners, preferences and instructor assignment are
 * out of scope. Gated by {@link Right#ExaminationEdit}, transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamEditUpdateRequest.class)
public class ExamEditUpdateBackend implements GwtRpcImplementation<ExamEditUpdateRequest, ExamEditResponse> {

	@Override
	public ExamEditResponse execute(ExamEditUpdateRequest request, SessionContext context) {
		Long examId = request.getExamId();
		if (examId == null)
			throw new GwtRpcException("No examination was specified.");
		if (request.getLength() == null || request.getLength() <= 0)
			throw new GwtRpcException("Examination length must be a positive number of minutes.");

		context.checkPermission(examId, "Exam", Right.ExaminationEdit);

		ExamDAO dao = ExamDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		Exam exam = dao.get(examId);
		if (exam == null)
			throw new GwtRpcException("Examination " + examId + " was not found.");

		Transaction tx = hibSession.beginTransaction();
		try {
			String name = request.getName();
			if (name == null || name.trim().isEmpty() || name.trim().equals(exam.generateName()))
				exam.setName(exam.generateName());
			else
				exam.setName(name.trim());

			exam.setNote(request.getNote() == null || request.getNote().isEmpty() ? null : request.getNote());
			exam.setSeatingType(request.getSeatingType());
			exam.setLength(request.getLength());
			exam.setExamSize(request.getExamSize());
			exam.setPrintOffset(request.getPrintOffset() == null || request.getPrintOffset() == 0 ? null : request.getPrintOffset());
			exam.setMaxNbrRooms(request.getMaxNbrRooms() == null ? 0 : request.getMaxNbrRooms());

			ChangeLog.addChange(
					hibSession, context, exam,
					ChangeLog.Source.EXAM_EDIT,
					ChangeLog.Operation.UPDATE,
					null, null);

			hibSession.merge(exam);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save examination: " + e.getMessage(), e);
		}

		ExamEditResponse response = new ExamEditResponse();
		ExamDetailBackend.fill(response, exam);
		response.setSaved(true);
		return response;
	}
}
