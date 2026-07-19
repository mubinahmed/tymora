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
import java.util.List;
import java.util.TreeSet;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamEditInterface.ExamDetailRequest;
import org.unitime.timetable.gwt.shared.ExamEditInterface.ExamEditResponse;
import org.unitime.timetable.gwt.shared.ExamEditInterface.IdName;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Examination Detail page (legacy examDetail.action).
 * See {@link org.unitime.timetable.gwt.shared.ExamEditInterface}; gated by
 * {@link Right#ExaminationDetail}. Its {@code fill()} is shared by the edit + save beans.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamDetailRequest.class)
public class ExamDetailBackend implements GwtRpcImplementation<ExamDetailRequest, ExamEditResponse> {

	@Override
	public ExamEditResponse execute(ExamDetailRequest request, SessionContext context) {
		Long examId = request.getExamId();
		if (examId == null)
			throw new GwtRpcException("No examination was specified.");

		context.checkPermission(examId, "Exam", Right.ExaminationDetail);

		Exam exam = ExamDAO.getInstance().get(examId);
		if (exam == null)
			throw new GwtRpcException("Examination " + examId + " was not found.");

		ExamEditResponse response = new ExamEditResponse();
		fill(response, exam);
		return response;
	}

	/** Populate the response from the exam (shared with the edit + save beans). */
	static void fill(ExamEditResponse r, Exam exam) {
		r.setExamId(exam.getUniqueId());
		r.setLabel(exam.getLabel());
		String generated = exam.generateName();
		r.setName(generated != null && generated.equals(exam.getName()) ? "" : str(exam.getName()));
		r.setNote(str(exam.getNote()));
		r.setLength(exam.getLength());
		r.setExamSize(exam.getExamSize());
		r.setSizeText(String.valueOf(exam.getSize()));
		r.setPrintOffset(exam.getPrintOffset());
		r.setSeatingType(exam.getSeatingType());
		r.setSeatingTypeLabel(Exam.getSeatingTypeLabel(exam.getSeatingType()));
		r.setMaxNbrRooms(exam.getMaxNbrRooms());
		if (exam.getExamType() != null) {
			r.setExamTypeId(exam.getExamType().getUniqueId());
			r.setExamTypeLabel(exam.getExamType().getLabel());
		}
		ExamPeriod assigned = exam.getAssignedPeriod();
		if (assigned != null) r.setAssignedPeriod(assigned.getName());
		try {
			ExamPeriod avg = exam.getAveragePeriod();
			if (avg != null) r.setAvgPeriod(avg.getName());
		} catch (Exception e) {}

		List<String> instructors = new ArrayList<String>();
		for (Object o : new TreeSet<Object>(exam.getInstructors())) {
			DepartmentalInstructor di = (DepartmentalInstructor) o;
			instructors.add(di.getNameLastFirst());
		}
		for (String s : instructors) r.addInstructor(s);

		List<String> owners = new ArrayList<String>();
		for (Object o : new TreeSet<Object>(exam.getOwners())) {
			ExamOwner eo = (ExamOwner) o;
			owners.add(eo.getLabel());
		}
		for (String s : owners) r.addOwner(s);

		r.addSeatingOption(new IdName(Long.valueOf(Exam.sSeatingTypeNormal), Exam.getSeatingTypeLabel(Exam.sSeatingTypeNormal)));
		r.addSeatingOption(new IdName(Long.valueOf(Exam.sSeatingTypeExam), Exam.getSeatingTypeLabel(Exam.sSeatingTypeExam)));
	}

	static String str(String s) { return s == null ? "" : s.trim(); }
}
