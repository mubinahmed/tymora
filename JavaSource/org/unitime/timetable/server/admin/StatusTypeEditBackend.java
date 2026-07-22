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

import java.util.Iterator;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.StatusTypeEditInterface.StatusTypeEditRequest;
import org.unitime.timetable.gwt.shared.StatusTypeEditInterface.StatusTypeEditResponse;
import org.unitime.timetable.gwt.shared.StatusTypeEditInterface.StatusTypeRecord;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.DepartmentStatusTypeDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Create / Edit backend for DepartmentStatusType (legacy Struts
 * deptStatusTypeEdit page). LOAD returns all status types with their editable
 * fields (reference, label, apply bitmask, status/rights bitmask, ord); SAVE
 * upserts one record; DELETE removes one record and preserves referential
 * integrity (re-assigning academic sessions to another session status,
 * clearing department status references, and re-ordering the remaining types).
 *
 * Every operation is gated by Right.StatusTypes, matching the legacy action.
 * DepartmentStatusType is a global (non session-scoped) reference table and the
 * legacy action recorded no ChangeLog entry, and there is no matching
 * ChangeLog.Source, so no ChangeLog entry is written here either.
 *
 * @author Angular migration
 */
@GwtRpcImplements(StatusTypeEditRequest.class)
public class StatusTypeEditBackend implements GwtRpcImplementation<StatusTypeEditRequest, StatusTypeEditResponse> {

	@Override
	public StatusTypeEditResponse execute(StatusTypeEditRequest request, SessionContext context) {
		context.checkPermission(Right.StatusTypes);

		switch (request.getOperation() == null ? org.unitime.timetable.gwt.shared.StatusTypeEditInterface.Operation.LOAD : request.getOperation()) {
		case SAVE:
			save(request.getRecord(), context);
			break;
		case DELETE:
			delete(request.getRecord(), context);
			break;
		case LOAD:
		default:
			break;
		}

		return load(context);
	}

	protected StatusTypeEditResponse load(SessionContext context) {
		StatusTypeEditResponse response = new StatusTypeEditResponse();
		boolean canEdit = context.hasPermission(Right.StatusTypes);
		response.setEditable(canEdit);
		response.setAddable(canEdit);
		response.setDeletable(canEdit);
		for (Object o : DepartmentStatusType.findAll()) {
			DepartmentStatusType st = (DepartmentStatusType) o;
			StatusTypeRecord r = new StatusTypeRecord();
			r.setId(st.getUniqueId());
			r.setReference(st.getReference());
			r.setLabel(st.getLabel());
			r.setApply(st.getApply() == null ? 0 : st.getApply().intValue());
			r.setStatus(st.getStatus() == null ? 0 : st.getStatus().intValue());
			r.setOrd(st.getOrd());
			response.addRecord(r);
		}
		return response;
	}

	protected void save(StatusTypeRecord record, SessionContext context) {
		if (record == null)
			throw new GwtRpcException("No status type provided.");
		if (record.getReference() == null || record.getReference().trim().isEmpty())
			throw new GwtRpcException("Reference is required.");
		if (record.getLabel() == null || record.getLabel().trim().isEmpty())
			throw new GwtRpcException("Label is required.");
		if (record.getApply() < 0)
			throw new GwtRpcException("Apply is required.");

		String reference = record.getReference().trim();
		DepartmentStatusType existingByRef = DepartmentStatusType.findByRef(reference);
		if (existingByRef != null && !existingByRef.getUniqueId().equals(record.getId()))
			throw new GwtRpcException("A status type with reference '" + reference + "' already exists.");

		Transaction tx = null;
		org.hibernate.Session hibSession = DepartmentStatusTypeDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			DepartmentStatusType st = null;
			if (record.getId() != null && record.getId() >= 0)
				st = DepartmentStatusTypeDAO.getInstance().get(record.getId(), hibSession);
			boolean isNew = (st == null);
			if (isNew)
				st = new DepartmentStatusType();

			st.setReference(reference);
			st.setLabel(record.getLabel().trim());
			st.setApply(record.getApply());
			st.setStatus(record.getStatus());
			if (record.getOrd() != null)
				st.setOrd(record.getOrd());
			else if (st.getOrd() == null)
				st.setOrd(DepartmentStatusType.findAll().size());

			if (isNew)
				hibSession.persist(st);
			else
				hibSession.merge(st);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	protected void delete(StatusTypeRecord record, SessionContext context) {
		if (record == null || record.getId() == null || record.getId() < 0)
			throw new GwtRpcException("No status type provided.");

		Transaction tx = null;
		org.hibernate.Session hibSession = DepartmentStatusTypeDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			DepartmentStatusType st = DepartmentStatusTypeDAO.getInstance().get(record.getId(), hibSession);
			if (st == null)
				throw new GwtRpcException("The status type no longer exists.");

			// Re-assign any academic session using this status to another session status.
			for (Session session : hibSession.createQuery(
					"select s from Session s where s.statusType.uniqueId = :id", Session.class)
					.setParameter("id", st.getUniqueId()).list()) {
				DepartmentStatusType other = null;
				for (Iterator<?> j = DepartmentStatusType.findAll().iterator(); j.hasNext(); ) {
					DepartmentStatusType x = (DepartmentStatusType) j.next();
					if (!x.getUniqueId().equals(st.getUniqueId()) && x.applySession()) {
						other = x; break;
					}
				}
				if (other == null)
					throw new GwtRpcException("Unable to delete session status " + st.getReference() + ", no other session status available.");
				session.setStatusType(other);
				hibSession.merge(session);
			}

			// Clear the status reference from any department using it.
			for (Department dept : hibSession.createQuery(
					"select d from Department d where d.statusType.uniqueId = :id", Department.class)
					.setParameter("id", st.getUniqueId()).list()) {
				dept.setStatusType(null);
				hibSession.merge(dept);
			}

			// Re-order the remaining status types.
			for (Iterator<?> i = DepartmentStatusType.findAll().iterator(); i.hasNext(); ) {
				DepartmentStatusType x = (DepartmentStatusType) i.next();
				if (x.getOrd() != null && st.getOrd() != null && x.getOrd() > st.getOrd()) {
					x.setOrd(x.getOrd() - 1);
					hibSession.merge(x);
				}
			}

			hibSession.remove(st);

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}
}
