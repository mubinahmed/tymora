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

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.AbsenceInfo;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.Operation;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.Option;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.StaffAbsenceRequest;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.StaffAbsenceResponse;
import org.unitime.timetable.model.AbsenceReason;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.dao.AbsenceReasonDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.StaffAbsenceDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Backing bean for the Relief Planning "Staff Absences" and teacher self-service
 * "My Leave" screens. When {@code mineOnly} is set the caller is a teacher managing
 * only their own absences (right {@code MyLeaveRequests}); otherwise the caller is an
 * administrator (right {@code StaffAbsences}, mutations need {@code StaffAbsenceEdit}).
 * Additive — introduces no changes to existing behavior.
 *
 * @author Angular migration (Relief Planning)
 */
@GwtRpcImplements(StaffAbsenceRequest.class)
public class StaffAbsenceBackend implements GwtRpcImplementation<StaffAbsenceRequest, StaffAbsenceResponse> {

	@Override
	public StaffAbsenceResponse execute(StaffAbsenceRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		boolean mineOnly = request.isMineOnly();
		if (mineOnly)
			context.checkPermission(Right.MyLeaveRequests);
		else
			context.checkPermission(Right.StaffAbsences);
		boolean canManage = context.hasPermission(Right.StaffAbsenceEdit);
		String myUid = context.getUser().getExternalUserId();

		org.hibernate.Session hibSession = StaffAbsenceDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();
			switch (request.getOperation()) {
				case SAVE: save(request, context, sessionId, mineOnly, canManage, myUid, hibSession); break;
				case DELETE: delete(request, sessionId, mineOnly, canManage, myUid, hibSession); break;
				case APPROVE: setStatus(request.getId(), StaffAbsence.Status.APPROVED, sessionId, canManage, hibSession); break;
				case REJECT: setStatus(request.getId(), StaffAbsence.Status.REJECTED, sessionId, canManage, hibSession); break;
				default: break;
			}
			StaffAbsenceResponse response = load(request, sessionId, mineOnly, canManage, myUid, hibSession);
			tx.commit();
			return response;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Exception e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException("Failed to process staff absence: " + e.getMessage(), e);
		}
	}

	private StaffAbsenceResponse load(StaffAbsenceRequest request, Long sessionId, boolean mineOnly,
			boolean canManage, String myUid, org.hibernate.Session hibSession) {
		StaffAbsenceResponse r = new StaffAbsenceResponse();
		r.setCanManage(canManage);
		r.setMyUid(myUid);

		Date from = parse(request.getFrom(), new Date(0L));
		Date to = parse(request.getTo(), new Date(Long.MAX_VALUE / 2));

		for (StaffAbsence a: StaffAbsenceDAO.getInstance().findBySessionAndRange(hibSession, sessionId, from, to)) {
			if (mineOnly && !myUid.equals(a.getExternalUniqueId())) continue;
			AbsenceInfo info = new AbsenceInfo();
			info.setId(a.getUniqueId());
			info.setUid(a.getExternalUniqueId());
			info.setName(a.getName());
			if (a.getReason() != null) { info.setReasonId(a.getReason().getUniqueId()); info.setReasonLabel(a.getReason().getLabel()); }
			info.setStartDate(fmt(a.getStartDate()));
			info.setEndDate(fmt(a.getEndDate()));
			info.setNote(a.getNote());
			info.setStatus(a.getStatus() == null ? 0 : a.getStatus());
			info.setStatusLabel(a.status().name());
			info.setCanEdit(canManage || (myUid.equals(a.getExternalUniqueId()) && a.status() == StaffAbsence.Status.REQUESTED));
			r.addAbsence(info);
		}

		for (AbsenceReason reason: AbsenceReasonDAO.getInstance().findAll())
			r.addReason(new Option(reason.getUniqueId().toString(), reason.getLabel()));

		// Staff picker (admins only need it; a teacher only ever files for themselves).
		if (!mineOnly) {
			Map<String, String> staff = new LinkedHashMap<String, String>();
			for (DepartmentalInstructor di: DepartmentalInstructor.findInstructorsForSession(sessionId)) {
				if (di.getExternalUniqueId() == null || di.getExternalUniqueId().isEmpty()) continue;
				staff.putIfAbsent(di.getExternalUniqueId(), di.nameLastNameFirst());
			}
			for (Map.Entry<String, String> e: staff.entrySet())
				r.addStaff(new Option(e.getKey(), e.getValue()));
		}
		return r;
	}

	private void save(StaffAbsenceRequest request, SessionContext context, Long sessionId, boolean mineOnly,
			boolean canManage, String myUid, org.hibernate.Session hibSession) {
		AbsenceInfo in = request.getAbsence();
		if (in == null) throw new GwtRpcException("No absence data.");

		Date start = parse(in.getStartDate(), null);
		Date end = parse(in.getEndDate(), null);
		if (start == null || end == null) throw new GwtRpcException("Start and end dates are required.");
		if (end.before(start)) throw new GwtRpcException("End date cannot be before start date.");

		StaffAbsence a;
		if (in.getId() != null) {
			a = StaffAbsenceDAO.getInstance().get(in.getId(), hibSession);
			if (a == null || !sessionId.equals(a.getSession().getUniqueId()))
				throw new GwtRpcException("The absence no longer exists.");
			if (!canManage && !(myUid.equals(a.getExternalUniqueId()) && a.status() == StaffAbsence.Status.REQUESTED))
				throw new GwtRpcException("You may only edit your own pending leave requests.");
		} else {
			a = new StaffAbsence();
			a.setSession(SessionDAO.getInstance().get(sessionId, hibSession));
			a.setRequestorUid(myUid);
		}

		// A teacher may only file for themselves and cannot self-approve.
		String uid = (mineOnly || !canManage) ? myUid : (in.getUid() == null ? myUid : in.getUid());
		a.setExternalUniqueId(uid);
		a.setName(resolveName(uid, in.getName(), sessionId));
		a.setReason(in.getReasonId() == null ? null : AbsenceReasonDAO.getInstance().get(in.getReasonId(), hibSession));
		a.setStartDate(start);
		a.setEndDate(end);
		a.setNote(in.getNote());
		if (in.getId() == null)
			a.setStatus((mineOnly || !canManage) ? StaffAbsence.Status.REQUESTED.value() : StaffAbsence.Status.APPROVED.value());
		else if (canManage)
			a.setStatus(in.getStatus());
		a.setTimeStamp(new Date());

		if (a.getUniqueId() == null) hibSession.persist(a); else hibSession.merge(a);
	}

	private void delete(StaffAbsenceRequest request, Long sessionId, boolean mineOnly, boolean canManage,
			String myUid, org.hibernate.Session hibSession) {
		if (request.getId() == null) return;
		StaffAbsence a = StaffAbsenceDAO.getInstance().get(request.getId(), hibSession);
		if (a == null || !sessionId.equals(a.getSession().getUniqueId())) return;
		if (!canManage && !(myUid.equals(a.getExternalUniqueId()) && a.status() == StaffAbsence.Status.REQUESTED))
			throw new GwtRpcException("You may only withdraw your own pending leave requests.");
		hibSession.remove(a); // relief_assignment rows cascade on the FK
	}

	private void setStatus(Long id, StaffAbsence.Status status, Long sessionId, boolean canManage, org.hibernate.Session hibSession) {
		if (!canManage) throw new GwtRpcException("You are not allowed to approve or reject absences.");
		if (id == null) return;
		StaffAbsence a = StaffAbsenceDAO.getInstance().get(id, hibSession);
		if (a == null || !sessionId.equals(a.getSession().getUniqueId())) return;
		a.setStatus(status.value());
		hibSession.merge(a);
	}

	private String resolveName(String uid, String fallback, Long sessionId) {
		if (uid != null)
			for (DepartmentalInstructor di: DepartmentalInstructor.findInstructorsForSession(sessionId))
				if (uid.equals(di.getExternalUniqueId())) return di.nameLastNameFirst();
		return fallback;
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
