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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ReliefReportInterface.DetailRow;
import org.unitime.timetable.gwt.shared.ReliefReportInterface.ReliefReportRequest;
import org.unitime.timetable.gwt.shared.ReliefReportInterface.ReliefReportResponse;
import org.unitime.timetable.gwt.shared.ReliefReportInterface.SummaryRow;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.dao.ReliefAssignmentDAO;
import org.unitime.timetable.model.dao.StaffAbsenceDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * Backing bean for the Relief Planning report (see {@link ReliefReportInterface}):
 * absence and relief-coverage figures over a date range. Requires {@code ReliefPlanning}.
 * Additive — introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
@GwtRpcImplements(ReliefReportRequest.class)
public class ReliefReportBackend implements GwtRpcImplementation<ReliefReportRequest, ReliefReportResponse> {

	@Override
	public ReliefReportResponse execute(ReliefReportRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		context.checkPermission(Right.ReliefPlanning);

		Date from = parse(request.getFrom());
		Date to = parse(request.getTo());
		if (from == null || to == null)
			throw new GwtRpcException("A date range is required.");
		if (to.before(from))
			throw new GwtRpcException("The end date cannot be before the start date.");

		org.hibernate.Session hibSession = ReliefAssignmentDAO.getInstance().getSession();
		ReliefReportResponse r = new ReliefReportResponse();

		// Absences by reason.
		Map<String, Integer> byReason = new LinkedHashMap<String, Integer>();
		for (StaffAbsence a: StaffAbsenceDAO.getInstance().findBySessionAndRange(hibSession, sessionId, from, to)) {
			String reason = a.getReason() == null ? "(none)" : a.getReason().getLabel();
			byReason.merge(reason, 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> e: byReason.entrySet())
			r.addByReason(new SummaryRow(e.getKey(), e.getValue()));

		// Relief coverage detail + load per relief teacher.
		Map<String, Integer> byRelief = new LinkedHashMap<String, Integer>();
		for (ReliefAssignment a: ReliefAssignmentDAO.getInstance().findBySessionAndRange(hibSession, sessionId, from, to)) {
			DetailRow d = new DetailRow();
			d.setDate(fmt(a.getMeetingDate()));
			d.setAbsentName(a.getAbsence() == null ? "" : a.getAbsence().getName());
			d.setReasonLabel(a.getAbsence() != null && a.getAbsence().getReason() != null ? a.getAbsence().getReason().getLabel() : "");
			d.setTimeText(timeText(a.getStartPeriod(), a.getStopPeriod()));
			d.setClassName(a.getClazz() == null ? "" : a.getClazz().getClassLabel());
			d.setRoomName(roomName(a.getLocationPermanentId(), sessionId, hibSession));
			d.setReliefName(a.getReliefName() == null ? "" : a.getReliefName());
			d.setAssignedBy(a.getAssignedBy() == null ? "" : a.getAssignedBy());
			d.setStatusLabel(a.status().name());
			r.addDetail(d);
			if (a.getReliefName() != null && !a.getReliefName().isEmpty())
				byRelief.merge(a.getReliefName(), 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> e: byRelief.entrySet())
			r.addByRelief(new SummaryRow(e.getKey(), e.getValue()));

		return r;
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

	private static Date parse(String s) {
		if (s == null || s.trim().isEmpty()) return null;
		try { return new SimpleDateFormat("yyyy-MM-dd").parse(s.trim()); }
		catch (Exception e) { return null; }
	}

	private static String fmt(Date d) {
		return d == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(d);
	}
}
