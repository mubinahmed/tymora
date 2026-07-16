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

import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExamPeriodListInterface.ExamPeriodListRequest;
import org.unitime.timetable.gwt.shared.ExamPeriodListInterface.ExamPeriodListResponse;
import org.unitime.timetable.gwt.shared.ExamPeriodListInterface.ExamTypeInfo;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * Read-only backing bean for the legacy examPeriodEdit.action (Examination
 * Periods) Struts page. Returns all examination types (to populate a selector)
 * and the examination periods of the current academic session projected to
 * string rows (type, date, start/end time, exam length, event start/stop
 * offset, preference), optionally filtered by the selected exam type.
 * Permission-gated by {@link Right#ExaminationPeriods} (Session qualified).
 * Add / Edit / Delete and the auto-setup wizard remain on the legacy page
 * (deferred). Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExamPeriodListRequest.class)
public class ExamPeriodListBackend implements GwtRpcImplementation<ExamPeriodListRequest, ExamPeriodListResponse> {

	@Override
	public ExamPeriodListResponse execute(ExamPeriodListRequest request, SessionContext context) {
		Long sessionId = request.getSessionId();
		if (sessionId == null)
			sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		context.checkPermission(sessionId, "Session", Right.ExaminationPeriods);

		ExamPeriodListResponse response = new ExamPeriodListResponse();
		response.setTitle("Examination Periods");

		// All examination types (mirror the legacy examPeriodEdit.action selector).
		List<ExamType> types = ExamType.findAll();
		for (ExamType t : types)
			response.addExamType(new ExamTypeInfo(t.getUniqueId(), t.getLabel()));

		// Resolve the selected exam type (null == all types).
		Long examTypeId = request.getExamTypeId();
		if (examTypeId != null) {
			boolean ok = false;
			for (ExamType t : types)
				if (t.getUniqueId().equals(examTypeId)) { ok = true; break; }
			if (!ok) examTypeId = null;
		}
		response.setExamTypeId(examTypeId);

		for (String c : new String[] { "Type", "Date", "Start Time", "End Time", "Exam Length",
				"Event Start Offset", "Event Stop Offset", "Preference" })
			response.addColumn(c);

		for (ExamPeriod ep : ExamPeriod.findAll(sessionId, examTypeId)) {
			Row r = response.addRow(ep.getUniqueId());

			// Wrap each projection defensively (lazy-init / transient computation safety).
			try {
				r.add(ep.getExamType() == null ? "" : ep.getExamType().getLabel());
			} catch (Exception e) { r.add(""); }

			try {
				r.add(ep.getStartDateLabel());
			} catch (Exception e) { r.add(""); }

			try {
				r.add(ep.getStartTimeLabel());
			} catch (Exception e) { r.add(""); }

			try {
				r.add(ep.getEndTimeLabel());
			} catch (Exception e) { r.add(""); }

			try {
				r.add(ep.getLength() == null ? "" : String.valueOf(Constants.SLOT_LENGTH_MIN * ep.getLength()));
			} catch (Exception e) { r.add(""); }

			try {
				r.add(ep.getEventStartOffset() == null ? "" : String.valueOf(Constants.SLOT_LENGTH_MIN * ep.getEventStartOffset()));
			} catch (Exception e) { r.add(""); }

			try {
				r.add(ep.getEventStopOffset() == null ? "" : String.valueOf(Constants.SLOT_LENGTH_MIN * ep.getEventStopOffset()));
			} catch (Exception e) { r.add(""); }

			try {
				PreferenceLevel pref = ep.getPrefLevel();
				if (pref == null || PreferenceLevel.sNeutral.equals(pref.getPrefProlog()))
					r.add("");
				else
					r.add(pref.getPrefName());
			} catch (Exception e) { r.add(""); }
		}

		return response;
	}
}
