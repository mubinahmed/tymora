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
package org.unitime.timetable.server.relief;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.MyReliefDutiesInterface.DutyInfo;
import org.unitime.timetable.gwt.shared.MyReliefDutiesInterface.MyReliefDutiesRequest;
import org.unitime.timetable.gwt.shared.MyReliefDutiesInterface.MyReliefDutiesResponse;
import org.unitime.timetable.model.HandoverNote;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.dao.HandoverNoteDAO;
import org.unitime.timetable.model.dao.ReliefAssignmentDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * Backing bean for the teacher self-service "My Relief Duties" screen (see
 * {@link org.unitime.timetable.gwt.shared.MyReliefDutiesInterface}). Read-only:
 * lists the current user's own covering assignments and the absent teacher's
 * shared handover for each. Requires {@code MyReliefDuties}. Additive —
 * introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
@GwtRpcImplements(MyReliefDutiesRequest.class)
public class MyReliefDutiesBackend implements GwtRpcImplementation<MyReliefDutiesRequest, MyReliefDutiesResponse> {

	@Override
	public MyReliefDutiesResponse execute(MyReliefDutiesRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		context.checkPermission(Right.MyReliefDuties);
		String myUid = context.getUser().getExternalUserId();

		Date today = today();
		Date from = parse(request.getFrom(), today);
		Date to = parse(request.getTo(), addDays(today, 13));

		org.hibernate.Session hibSession = ReliefAssignmentDAO.getInstance().getSession();
		MyReliefDutiesResponse response = new MyReliefDutiesResponse();
		for (ReliefAssignment a: ReliefAssignmentDAO.getInstance().findBySessionAndReliefUidAndRange(hibSession, sessionId, myUid, from, to)) {
			DutyInfo d = new DutyInfo();
			d.setId(a.getUniqueId());
			d.setMeetingDate(fmt(a.getMeetingDate()));
			d.setAbsentName(a.getAbsence() == null ? "" : a.getAbsence().getName());
			d.setReasonLabel(a.getAbsence() != null && a.getAbsence().getReason() != null ? a.getAbsence().getReason().getLabel() : "");
			d.setClassName(a.getClazz() == null ? "" : a.getClazz().getClassLabel());
			d.setTimeText(timeText(a.getStartPeriod(), a.getStopPeriod()));
			d.setRoomName(roomName(a.getLocationPermanentId(), sessionId, hibSession));
			if (a.getAbsence() != null)
				for (HandoverNote n: HandoverNoteDAO.getInstance().findByAbsence(hibSession, a.getAbsence().getUniqueId()))
					d.addHandoverNote(ReliefBoardBackend.toHandoverNoteInfo(n));
			response.addDuty(d);
		}
		return response;
	}

	private String roomName(Long permId, Long sessionId, org.hibernate.Session hibSession) {
		if (permId == null) return "";
		Location loc = hibSession.createQuery(
				"from Location l where l.permanentId = :pid and l.session.uniqueId = :sid", Location.class)
				.setParameter("pid", permId).setParameter("sid", sessionId).setMaxResults(1).uniqueResult();
		return loc == null ? "" : loc.getLabel();
	}

	private static String timeText(int start, int stop) {
		return Constants.toTime(start * Constants.SLOT_LENGTH_MIN) + " - " + Constants.toTime(stop * Constants.SLOT_LENGTH_MIN);
	}

	private static Date today() {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
		return c.getTime();
	}

	private static Date addDays(Date date, int days) {
		Calendar c = Calendar.getInstance();
		c.setTime(date);
		c.add(Calendar.DAY_OF_MONTH, days);
		return c.getTime();
	}

	private static Date parse(String s, Date dflt) {
		if (s == null || s.trim().isEmpty()) return dflt;
		try { return new SimpleDateFormat("yyyy-MM-dd").parse(s.trim()); }
		catch (Exception e) { return dflt; }
	}

	private static String fmt(Date d) {
		return d == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(d);
	}
}
