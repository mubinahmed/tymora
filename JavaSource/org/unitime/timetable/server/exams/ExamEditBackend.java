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

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamEditInterface.ExamEditRequest;
import org.unitime.timetable.gwt.shared.ExamEditInterface.ExamEditResponse;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Edit Examination page (legacy examEdit.action) — the
 * editable scalar fields. See {@link org.unitime.timetable.gwt.shared.ExamEditInterface};
 * gated by {@link Right#ExaminationEdit}. Save via {@code ExamEditUpdateBackend}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamEditRequest.class)
public class ExamEditBackend implements GwtRpcImplementation<ExamEditRequest, ExamEditResponse> {

	@Override
	public ExamEditResponse execute(ExamEditRequest request, SessionContext context) {
		Long examId = request.getExamId();
		if (examId == null)
			throw new GwtRpcException("No examination was specified.");

		context.checkPermission(examId, "Exam", Right.ExaminationEdit);

		Exam exam = ExamDAO.getInstance().get(examId);
		if (exam == null)
			throw new GwtRpcException("Examination " + examId + " was not found.");

		ExamEditResponse response = new ExamEditResponse();
		ExamDetailBackend.fill(response, exam);
		return response;
	}
}
