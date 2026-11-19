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

import org.apache.commons.fileupload.FileItem;
import org.hibernate.Transaction;
import org.unitime.timetable.defaults.SessionAttribute;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.AbsenceInfo;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.HandoverNoteInfo;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.Operation;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.Option;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.StaffAbsenceRequest;
import org.unitime.timetable.gwt.shared.StaffAbsenceInterface.StaffAbsenceResponse;
import org.unitime.timetable.model.AbsenceReason;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.HandoverNote;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StaffAbsence;
import org.unitime.timetable.model.dao.AbsenceReasonDAO;
import org.unitime.timetable.model.dao.HandoverNoteDAO;
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
				case ADD_NOTE: addNote(request, context, sessionId, canManage, myUid, hibSession); break;
				case DELETE_NOTE: deleteNote(request, sessionId, canManage, myUid, hibSession); break;
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
			boolean owner = myUid.equals(a.getExternalUniqueId());
			boolean canShare = canManage || (owner && a.status() != StaffAbsence.Status.REJECTED);
			info.setCanShareHandover(canShare);
			for (HandoverNote n: HandoverNoteDAO.getInstance().findByAbsence(hibSession, a.getUniqueId())) {
				HandoverNoteInfo ni = new HandoverNoteInfo();
				ni.setId(n.getUniqueId());
				ni.setTextNote(n.getTextNote());
				ni.setLinkUrl(n.getLinkUrl());
				ni.setLinkLabel(n.getLinkLabel());
				ni.setAttachedName(n.getAttachedName());
				ni.setAttachedSize(n.getAttachedSize());
				ni.setCreatedByName(n.getCreatedByName());
				ni.setTimeStamp(fmtTime(n.getTimeStamp()));
				ni.setCanDelete(canManage || myUid.equals(n.getCreatedBy()));
				info.addNote(ni);
			}
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

	/** Absent teacher (or admin on their behalf) shares one instruction/link/attachment for the reliever. */
	private void addNote(StaffAbsenceRequest request, SessionContext context, Long sessionId, boolean canManage,
			String myUid, org.hibernate.Session hibSession) {
		Long absenceId = request.getAbsenceId();
		if (absenceId == null) throw new GwtRpcException("No absence specified.");
		StaffAbsence a = StaffAbsenceDAO.getInstance().get(absenceId, hibSession);
		if (a == null || !sessionId.equals(a.getSession().getUniqueId()))
			throw new GwtRpcException("The absence no longer exists.");
		boolean owner = myUid.equals(a.getExternalUniqueId());
		if (!canManage && !(owner && a.status() != StaffAbsence.Status.REJECTED))
			throw new GwtRpcException("You may only share handover instructions for your own absence.");

		HandoverNoteInfo in = request.getNote();
		String text = (in == null || in.getTextNote() == null) ? null : in.getTextNote().trim();
		String linkUrl = (in == null || in.getLinkUrl() == null) ? null : in.getLinkUrl().trim();
		String linkLabel = (in == null || in.getLinkLabel() == null) ? null : in.getLinkLabel().trim();
		if (text != null && text.length() > 4000) text = text.substring(0, 4000);
		if (linkLabel != null && linkLabel.length() > 200) linkLabel = linkLabel.substring(0, 200);
		if (linkUrl != null && !linkUrl.isEmpty() && !linkUrl.matches("(?i)https?://.+"))
			throw new GwtRpcException("The link must be a valid http(s):// URL.");

		FileItem file = null;
		if (request.isUseUploadedFile()) {
			file = (FileItem)context.getAttribute(SessionAttribute.LastUploadedFile);
			if (file == null || file.getSize() <= 0)
				throw new GwtRpcException("No file was uploaded.");
		}
		if ((text == null || text.isEmpty()) && (linkUrl == null || linkUrl.isEmpty()) && file == null)
			throw new GwtRpcException("Provide instructions, a link, or an attachment.");

		HandoverNote n = new HandoverNote();
		n.setSession(a.getSession());
		n.setAbsence(a);
		n.setTextNote(text == null || text.isEmpty() ? null : text);
		n.setLinkUrl(linkUrl == null || linkUrl.isEmpty() ? null : linkUrl);
		n.setLinkLabel(linkLabel == null || linkLabel.isEmpty() ? null : linkLabel);
		if (file != null) {
			String name = file.getName();
			if (name != null) {
				int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
				if (slash >= 0) name = name.substring(slash + 1);
				if (name.length() > 260) name = name.substring(name.length() - 260);
			}
			n.setAttachedName(name);
			n.setAttachedContentType(file.getContentType());
			n.setAttachedSize((int)file.getSize());
			n.setAttachedFile(file.get());
		}
		n.setCreatedBy(myUid);
		n.setCreatedByName(resolveName(myUid, myUid, sessionId));
		n.setTimeStamp(new Date());
		hibSession.persist(n);

		if (request.isUseUploadedFile())
			context.setAttribute(SessionAttribute.LastUploadedFile, null);
	}

	private void deleteNote(StaffAbsenceRequest request, Long sessionId, boolean canManage, String myUid, org.hibernate.Session hibSession) {
		if (request.getNoteId() == null) return;
		HandoverNote n = HandoverNoteDAO.getInstance().get(request.getNoteId(), hibSession);
		if (n == null || !sessionId.equals(n.getSession().getUniqueId())) return;
		if (!canManage && !myUid.equals(n.getCreatedBy()))
			throw new GwtRpcException("You may only remove your own handover notes.");
		hibSession.remove(n);
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

	private static String fmtTime(Date d) {
		return d == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(d);
	}
}
