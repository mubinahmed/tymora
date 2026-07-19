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
package org.unitime.timetable.server.courses;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.DispDatePatternInterface.DispDatePatternRequest;
import org.unitime.timetable.gwt.shared.DispDatePatternInterface.DispDatePatternResponse;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DatePattern;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.DatePatternDAO;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;

/**
 * Read backend for the migrated Display Date Pattern page (legacy dispDatePattern.action).
 * Resolves the date pattern the same way as {@code DispDatePatternAction.getDatePattern()}
 * (by date-pattern id, else class/subpart effective pattern, else the session default) and
 * projects its 0/1 pattern string into a list of active dates for the calendar view.
 *
 * @author Angular migration
 */
@GwtRpcImplements(DispDatePatternRequest.class)
public class DispDatePatternBackend implements GwtRpcImplementation<DispDatePatternRequest, DispDatePatternResponse> {

	@Override
	public DispDatePatternResponse execute(DispDatePatternRequest request, SessionContext context) {
		DatePattern dp = resolve(request, context);
		if (dp == null)
			throw new GwtRpcException("No date pattern could be resolved.");

		DispDatePatternResponse response = new DispDatePatternResponse();
		response.setId(dp.getUniqueId());
		response.setName(dp.getName());
		response.setNumberOfWeeks(dp.getNumberOfWeeks());

		SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		Date start = dp.getStartDate();
		if (start != null) response.setStartDate(iso.format(start));
		if (dp.getEndDate() != null) response.setEndDate(iso.format(dp.getEndDate()));

		String pattern = dp.getPattern();
		if (pattern != null && start != null) {
			Calendar cal = Calendar.getInstance(Locale.US);
			for (int i = 0; i < pattern.length(); i++) {
				if (pattern.charAt(i) == '1') {
					cal.setTime(start);
					cal.add(Calendar.DAY_OF_YEAR, i);
					response.addActiveDate(iso.format(cal.getTime()));
				}
			}
		}

		return response;
	}

	private static DatePattern resolve(DispDatePatternRequest request, SessionContext context) {
		if (request.getDatePatternId() != null) {
			DatePattern dp = DatePatternDAO.getInstance().get(request.getDatePatternId());
			if (dp != null) return dp;
		}
		if (request.getClassId() != null) {
			Class_ c = Class_DAO.getInstance().get(request.getClassId());
			if (c != null) return c.effectiveDatePattern();
		}
		if (request.getSubpartId() != null) {
			SchedulingSubpart ss = SchedulingSubpartDAO.getInstance().get(request.getSubpartId());
			if (ss != null) return ss.effectiveDatePattern();
		}
		Long sessionId = context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId();
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		return SessionDAO.getInstance().get(sessionId).getDefaultDatePatternNotNull();
	}
}
