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

import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolverGroupEditInterface.Operation;
import org.unitime.timetable.gwt.shared.SolverGroupEditInterface.SolverGroupEditRequest;
import org.unitime.timetable.gwt.shared.SolverGroupEditInterface.SolverGroupEditResponse;
import org.unitime.timetable.gwt.shared.SolverGroupEditInterface.SolverGroupInfo;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.Solution;
import org.unitime.timetable.model.SolverGroup;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.SolverGroupDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Create / edit / delete command bean for solver groups of the current academic
 * session (migration of solverGroupEdit.action). Only the name and abbreviation
 * are managed here; department membership, timetable managers and solutions are
 * deferred and left untouched on update (see SolverGroupEditInterface).
 *
 * Every operation is gated by the session-scoped {@link Right#SolverGroups}
 * permission, matching the legacy Struts action.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SolverGroupEditRequest.class)
public class SolverGroupEditBackend implements GwtRpcImplementation<SolverGroupEditRequest, SolverGroupEditResponse> {

	@Override
	public SolverGroupEditResponse execute(SolverGroupEditRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		context.checkPermission(sessionId, "Session", Right.SolverGroups);

		Operation op = request.getOperation();
		if (op == null) op = Operation.LOAD;

		switch (op) {
		case SAVE:
			save(request, context, sessionId);
			break;
		case DELETE:
			delete(request, context, sessionId);
			break;
		case LOAD:
		default:
			break;
		}

		return list(context, sessionId);
	}

	protected SolverGroupEditResponse list(SessionContext context, Long sessionId) {
		SolverGroupEditResponse response = new SolverGroupEditResponse();
		response.setCanAdd(true);
		for (SolverGroup group: SolverGroup.findBySessionId(sessionId)) {
			SolverGroupInfo info = new SolverGroupInfo();
			info.setUniqueId(group.getUniqueId());
			info.setName(group.getName());
			info.setAbbv(group.getAbbv());
			TreeSet<String> depts = new TreeSet<String>();
			if (group.getDepartments() != null)
				for (Department d: group.getDepartments())
					depts.add(d.getDeptCode());
			StringBuilder sb = new StringBuilder();
			for (String d: depts)
				sb.append(sb.length() > 0 ? ", " : "").append(d);
			info.setDepartments(sb.toString());
			boolean hasSolutions = (group.getSolutions() != null && !group.getSolutions().isEmpty());
			info.setCommitted(group.getCommittedSolution() != null);
			info.setCanEdit(true);
			// Deleting a group with solutions would cascade-remove those solutions;
			// the legacy screen forbids it, so we mirror that here.
			info.setCanDelete(!hasSolutions);
			response.addGroup(info);
		}
		return response;
	}

	protected void save(SolverGroupEditRequest request, SessionContext context, Long sessionId) {
		String name = request.getName() == null ? "" : request.getName().trim();
		String abbv = request.getAbbv() == null ? "" : request.getAbbv().trim();
		if (name.isEmpty())
			throw new GwtRpcException("Name is required.");
		if (abbv.isEmpty())
			throw new GwtRpcException("Abbreviation is required.");
		if (name.length() > 50)
			throw new GwtRpcException("Name must be at most 50 characters.");
		if (abbv.length() > 50)
			throw new GwtRpcException("Abbreviation must be at most 50 characters.");

		org.hibernate.Session hibSession = SolverGroupDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();

			// Duplicate name / abbreviation guard within the session (excluding self).
			SolverGroup byName = SolverGroup.findBySessionIdName(sessionId, name);
			if (byName != null && !byName.getUniqueId().equals(request.getUniqueId()))
				throw new GwtRpcException("A solver group named '" + name + "' already exists.");
			SolverGroup byAbbv = SolverGroup.findBySessionIdAbbv(sessionId, abbv);
			if (byAbbv != null && !byAbbv.getUniqueId().equals(request.getUniqueId()))
				throw new GwtRpcException("A solver group with abbreviation '" + abbv + "' already exists.");

			SolverGroup group = null;
			boolean create = (request.getUniqueId() == null);
			if (!create) {
				group = SolverGroupDAO.getInstance().get(request.getUniqueId(), hibSession);
				if (group == null)
					throw new GwtRpcException("The solver group no longer exists.");
				if (group.getSession() == null || !sessionId.equals(group.getSession().getUniqueId()))
					throw new GwtRpcException("The solver group belongs to a different academic session.");
			}

			if (create) {
				Session session = SessionDAO.getInstance().get(sessionId, hibSession);
				group = new SolverGroup();
				group.setSession(session);
				group.setName(name);
				group.setAbbv(abbv);
				group.setDepartments(new HashSet<Department>());
				group.setTimetableManagers(new HashSet<TimetableManager>());
				group.setSolutions(new HashSet<Solution>());
				hibSession.persist(group);
				ChangeLog.addChange(hibSession, context, group,
						ChangeLog.Source.SOLVER_GROUP_EDIT, ChangeLog.Operation.CREATE, null, null);
			} else {
				// MERGE update: touch only the edited fields; leave departments,
				// timetable managers and solutions exactly as they are.
				group.setName(name);
				group.setAbbv(abbv);
				hibSession.merge(group);
				ChangeLog.addChange(hibSession, context, group,
						ChangeLog.Source.SOLVER_GROUP_EDIT, ChangeLog.Operation.UPDATE, null, null);
			}

			hibSession.flush();
			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			throw e;
		} catch (Throwable t) {
			throw new GwtRpcException(t.getMessage(), t);
		} finally {
			if (tx != null && tx.isActive()) tx.rollback();
		}
	}

	protected void delete(SolverGroupEditRequest request, SessionContext context, Long sessionId) {
		if (request.getUniqueId() == null)
			throw new GwtRpcException("No solver group specified.");

		org.hibernate.Session hibSession = SolverGroupDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();

			SolverGroup group = SolverGroupDAO.getInstance().get(request.getUniqueId(), hibSession);
			if (group == null)
				throw new GwtRpcException("The solver group no longer exists.");
			if (group.getSession() == null || !sessionId.equals(group.getSession().getUniqueId()))
				throw new GwtRpcException("The solver group belongs to a different academic session.");
			if (group.getSolutions() != null && !group.getSolutions().isEmpty())
				throw new GwtRpcException("The solver group has one or more solutions and cannot be deleted.");

			// Detach owned departments and timetable managers before removing the
			// group so no foreign key is left dangling (matches the legacy delete).
			if (group.getDepartments() != null) {
				for (Iterator<Department> i = group.getDepartments().iterator(); i.hasNext(); ) {
					Department dept = i.next();
					dept.setSolverGroup(null);
					hibSession.merge(dept);
				}
			}
			if (group.getTimetableManagers() != null) {
				for (Iterator<TimetableManager> i = group.getTimetableManagers().iterator(); i.hasNext(); ) {
					TimetableManager mgr = i.next();
					mgr.getSolverGroups().remove(group);
					hibSession.merge(mgr);
				}
			}

			ChangeLog.addChange(hibSession, context, group,
					ChangeLog.Source.SOLVER_GROUP_EDIT, ChangeLog.Operation.DELETE, null, null);
			hibSession.remove(group);

			hibSession.flush();
			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			throw e;
		} catch (Throwable t) {
			throw new GwtRpcException(t.getMessage(), t);
		} finally {
			if (tx != null && tx.isActive()) tx.rollback();
		}
	}
}
