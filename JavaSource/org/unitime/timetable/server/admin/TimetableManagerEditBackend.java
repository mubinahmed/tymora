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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.ManagerLine;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.TimetableManagerEditRequest;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.TimetableManagerEditResponse;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.SolverGroup;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.TimetableManagerDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Create/Edit command bean for Timetabling Managers (legacy
 * timetableManagerList.action). Edits only the manager's core identity fields;
 * roles, department, settings and solver-group assignments are DEFERRED and left
 * untouched on save. Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(TimetableManagerEditRequest.class)
public class TimetableManagerEditBackend implements GwtRpcImplementation<TimetableManagerEditRequest, TimetableManagerEditResponse> {

	@Override
	public TimetableManagerEditResponse execute(TimetableManagerEditRequest request, SessionContext context) {
		if (request.getOperation() == null)
			throw new GwtRpcException("No operation specified.");

		switch (request.getOperation()) {
		case LIST:
			return list(context);
		case LOAD:
			return load(request, context);
		case SAVE:
			return save(request, context);
		case DELETE:
			return delete(request, context);
		default:
			throw new GwtRpcException("Unsupported operation: " + request.getOperation());
		}
	}

	protected TimetableManagerEditResponse list(SessionContext context) {
		context.checkPermission(Right.TimetableManagers);
		TimetableManagerEditResponse response = new TimetableManagerEditResponse();
		response.setCanAdd(context.hasPermission(Right.TimetableManagerAdd));
		List<TimetableManager> managers = new ArrayList<TimetableManager>(TimetableManagerDAO.getInstance().findAll());
		Collections.sort(managers, new Comparator<TimetableManager>() {
			@Override
			public int compare(TimetableManager m1, TimetableManager m2) {
				int cmp = str(m1.getLastName()).compareToIgnoreCase(str(m2.getLastName()));
				if (cmp != 0) return cmp;
				cmp = str(m1.getFirstName()).compareToIgnoreCase(str(m2.getFirstName()));
				if (cmp != 0) return cmp;
				return valueOf(m1.getUniqueId()).compareTo(valueOf(m2.getUniqueId()));
			}
		});
		for (TimetableManager m : managers) {
			ManagerLine line = new ManagerLine();
			line.setUniqueId(m.getUniqueId());
			line.setExternalUniqueId(m.getExternalUniqueId());
			line.setName(m.getName());
			line.setEmail(m.getEmailAddress());
			line.setCanEdit(context.hasPermission(m, Right.TimetableManagerEdit));
			line.setCanDelete(context.hasPermission(m, Right.TimetableManagerDelete));
			response.addManager(line);
		}
		return response;
	}

	protected TimetableManagerEditResponse load(TimetableManagerEditRequest request, SessionContext context) {
		context.checkPermission(Right.TimetableManagers);
		if (request.getUniqueId() == null)
			throw new GwtRpcException("No manager specified.");
		TimetableManager manager = TimetableManagerDAO.getInstance().get(request.getUniqueId());
		if (manager == null)
			throw new GwtRpcException("Manager no longer exists.");
		context.checkPermission(manager, Right.TimetableManagerEdit);
		TimetableManagerEditResponse response = new TimetableManagerEditResponse();
		response.setUniqueId(manager.getUniqueId());
		response.setExternalUniqueId(manager.getExternalUniqueId());
		response.setFirstName(manager.getFirstName());
		response.setMiddleName(manager.getMiddleName());
		response.setLastName(manager.getLastName());
		response.setAcademicTitle(manager.getAcademicTitle());
		response.setEmailAddress(manager.getEmailAddress());
		return response;
	}

	protected TimetableManagerEditResponse save(TimetableManagerEditRequest request, SessionContext context) {
		String lastName = trimToNull(request.getLastName());
		if (lastName == null)
			throw new GwtRpcException("Last name is required.");
		String firstName = trimToNull(request.getFirstName());

		org.hibernate.Session hibSession = TimetableManagerDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();

			boolean create = (request.getUniqueId() == null);
			TimetableManager manager;
			if (create) {
				context.checkPermission(Right.TimetableManagerAdd);
				manager = new TimetableManager();
			} else {
				// MERGE-ON-UPDATE: load the existing entity and set ONLY the core
				// fields we render. Roles, departments, settings and solver groups
				// are intentionally left untouched.
				manager = TimetableManagerDAO.getInstance().get(request.getUniqueId(), hibSession);
				if (manager == null)
					throw new GwtRpcException("Manager no longer exists.");
				context.checkPermission(manager, Right.TimetableManagerEdit);
			}

			manager.setExternalUniqueId(trimToNull(request.getExternalUniqueId()));
			// first_name / last_name are NOT NULL in the schema; default to "".
			manager.setFirstName(firstName == null ? "" : firstName);
			manager.setMiddleName(trimToNull(request.getMiddleName()));
			manager.setLastName(lastName);
			manager.setAcademicTitle(trimToNull(request.getAcademicTitle()));
			manager.setEmailAddress(trimToNull(request.getEmailAddress()));

			if (create)
				hibSession.persist(manager);
			else
				hibSession.merge(manager);

			ChangeLog.addChange(hibSession, context, manager,
					ChangeLog.Source.MANAGER_EDIT,
					create ? ChangeLog.Operation.CREATE : ChangeLog.Operation.UPDATE,
					null, null);

			hibSession.flush();
			tx.commit(); tx = null;

			TimetableManagerEditResponse response = new TimetableManagerEditResponse();
			response.setUniqueId(manager.getUniqueId());
			response.setExternalUniqueId(manager.getExternalUniqueId());
			response.setFirstName(manager.getFirstName());
			response.setMiddleName(manager.getMiddleName());
			response.setLastName(manager.getLastName());
			response.setAcademicTitle(manager.getAcademicTitle());
			response.setEmailAddress(manager.getEmailAddress());
			return response;
		} catch (GwtRpcException e) {
			throw e;
		} catch (Throwable t) {
			throw new GwtRpcException(t.getMessage(), t);
		} finally {
			if (tx != null && tx.isActive()) tx.rollback();
		}
	}

	protected TimetableManagerEditResponse delete(TimetableManagerEditRequest request, SessionContext context) {
		if (request.getUniqueId() == null)
			throw new GwtRpcException("No manager specified.");
		org.hibernate.Session hibSession = TimetableManagerDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();
			TimetableManager manager = TimetableManagerDAO.getInstance().get(request.getUniqueId(), hibSession);
			if (manager == null)
				throw new GwtRpcException("Manager no longer exists.");
			context.checkPermission(manager, Right.TimetableManagerDelete);

			// Detach the many-to-many joins owned by Department / SolverGroup so the
			// links do not block the delete. Settings and roles are removed via the
			// entity's own cascade (orphanRemoval).
			if (manager.getDepartments() != null) {
				for (Department d : new ArrayList<Department>(manager.getDepartments())) {
					if (d.getTimetableManagers() != null && d.getTimetableManagers().remove(manager))
						hibSession.merge(d);
				}
				manager.getDepartments().clear();
			}
			if (manager.getSolverGroups() != null) {
				for (SolverGroup g : new ArrayList<SolverGroup>(manager.getSolverGroups())) {
					if (g.getTimetableManagers() != null && g.getTimetableManagers().remove(manager))
						hibSession.merge(g);
				}
				manager.getSolverGroups().clear();
			}

			ChangeLog.addChange(hibSession, context, manager,
					ChangeLog.Source.MANAGER_EDIT, ChangeLog.Operation.DELETE, null, null);

			hibSession.remove(manager);
			hibSession.flush();
			tx.commit(); tx = null;

			return new TimetableManagerEditResponse();
		} catch (GwtRpcException e) {
			throw e;
		} catch (Throwable t) {
			throw new GwtRpcException(t.getMessage(), t);
		} finally {
			if (tx != null && tx.isActive()) tx.rollback();
		}
	}

	private static String str(String s) { return s == null ? "" : s; }

	private static Long valueOf(Long id) { return id == null ? Long.valueOf(-1) : id; }

	private static String trimToNull(String s) {
		if (s == null) return null;
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
