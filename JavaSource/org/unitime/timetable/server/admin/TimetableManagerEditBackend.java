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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.IdName;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.ManagerLine;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.TimetableManagerEditRequest;
import org.unitime.timetable.gwt.shared.TimetableManagerEditInterface.TimetableManagerEditResponse;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.ManagerRole;
import org.unitime.timetable.model.Roles;
import org.unitime.timetable.model.SolverGroup;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.DepartmentDAO;
import org.unitime.timetable.model.dao.RolesDAO;
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
		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		TimetableManagerEditResponse response = new TimetableManagerEditResponse();

		if (request.getUniqueId() == null) {
			// Create-dialog case: no manager yet, return only the assignable
			// options so the new-manager form can offer roles / departments.
			context.checkPermission(Right.TimetableManagerAdd);
			fillAvailableRoles(response, context);
			fillAvailableDepartments(response, sessionId);
			return response;
		}

		TimetableManager manager = TimetableManagerDAO.getInstance().get(request.getUniqueId());
		if (manager == null)
			throw new GwtRpcException("Manager no longer exists.");
		context.checkPermission(manager, Right.TimetableManagerEdit);
		response.setUniqueId(manager.getUniqueId());
		response.setExternalUniqueId(manager.getExternalUniqueId());
		response.setFirstName(manager.getFirstName());
		response.setMiddleName(manager.getMiddleName());
		response.setLastName(manager.getLastName());
		response.setAcademicTitle(manager.getAcademicTitle());
		response.setEmailAddress(manager.getEmailAddress());

		// Current roles (each with primary flag) - see loadForm() in the legacy action.
		if (manager.getManagerRoles() != null) {
			for (ManagerRole mr : manager.getManagerRoles()) {
				Roles role = mr.getRole();
				if (role == null) continue;
				response.addRoleId(role.getRoleId());
				if (mr.isPrimary() != null && mr.isPrimary().booleanValue())
					response.setPrimaryRoleId(role.getRoleId());
			}
		}
		// Current-session departments only (loadForm() filters by session id).
		if (manager.getDepartments() != null) {
			for (Department d : manager.getDepartments()) {
				if (d.getSessionId() != null && d.getSessionId().equals(sessionId))
					response.addDepartmentId(d.getUniqueId());
			}
		}

		fillAvailableRoles(response, context);
		fillAvailableDepartments(response, sessionId);
		return response;
	}

	/**
	 * Assignable manager roles (Roles.findAll(true)); roles carrying the
	 * SessionIndependent right are hidden from callers who lack it - mirrors
	 * setupRoles() in the legacy action.
	 */
	protected void fillAvailableRoles(TimetableManagerEditResponse response, SessionContext context) {
		boolean sessionIndependent = context.hasPermission(Right.SessionIndependent);
		for (Roles role : Roles.findAll(true)) {
			if (!sessionIndependent && role.hasRight(Right.SessionIndependent)) continue;
			response.addAvailableRole(new IdName(role.getRoleId(), role.getAbbv()));
		}
	}

	/** Departments of the current academic session (LookupTables.setupDepts). */
	protected void fillAvailableDepartments(TimetableManagerEditResponse response, Long sessionId) {
		if (sessionId == null) return;
		for (Department d : Department.findAll(sessionId))
			response.addAvailableDepartment(new IdName(d.getUniqueId(), d.getLabel()));
	}

	/** Role ids the current user may assign - Roles.findAll(true) minus SessionIndependent when not held. */
	protected Set<Long> assignableRoleIds(SessionContext context) {
		Set<Long> ids = new HashSet<Long>();
		boolean sessionIndependent = context.hasPermission(Right.SessionIndependent);
		for (Roles role : Roles.findAll(true)) {
			if (!sessionIndependent && role.hasRight(Right.SessionIndependent)) continue;
			ids.add(role.getRoleId());
		}
		return ids;
	}

	/**
	 * Upsert the manager's roles (add/remove ManagerRole, set exactly one primary)
	 * - faithful merge of updateManager()/addManager() in the legacy action. Roles
	 * already on the manager are preserved even when hidden from the caller; new
	 * roles must be assignable. receive_emails on existing rows is left untouched
	 * (not managed here); new rows default to false, as the legacy action does.
	 */
	protected void applyRoles(TimetableManager manager, TimetableManagerEditRequest request, SessionContext context, org.hibernate.Session hibSession) {
		List<Long> requested = new ArrayList<Long>();
		if (request.getRoleIds() != null)
			for (Long id : request.getRoleIds())
				if (id != null && !requested.contains(id)) requested.add(id);

		Set<ManagerRole> mgrRoles = manager.getManagerRoles();
		if (mgrRoles == null) { mgrRoles = new HashSet<ManagerRole>(); manager.setManagerRoles(mgrRoles); }

		Set<Long> existingIds = new HashSet<Long>();
		for (ManagerRole mr : mgrRoles)
			if (mr.getRole() != null) existingIds.add(mr.getRole().getRoleId());

		// Guard: only assignable roles may be newly added; pre-existing roles stay.
		Set<Long> assignable = assignableRoleIds(context);
		for (Long id : requested)
			if (!assignable.contains(id) && !existingIds.contains(id))
				throw new GwtRpcException("You are not allowed to assign one of the selected roles.");

		// Determine the single primary role.
		Long primary = request.getPrimaryRoleId();
		if (requested.isEmpty())
			primary = null;
		else if (primary == null || !requested.contains(primary))
			primary = requested.get(0);

		// Add / update.
		for (Long roleId : requested) {
			ManagerRole existing = null;
			for (ManagerRole mr : mgrRoles)
				if (mr.getRole() != null && roleId.equals(mr.getRole().getRoleId())) { existing = mr; break; }
			if (existing != null) {
				existing.setPrimary(roleId.equals(primary));
			} else {
				Roles role = RolesDAO.getInstance().get(roleId, hibSession);
				if (role == null) throw new GwtRpcException("Selected role no longer exists.");
				ManagerRole mr = new ManagerRole();
				mr.setRole(role);
				mr.setTimetableManager(manager);
				mr.setPrimary(roleId.equals(primary));
				mr.setReceiveEmails(Boolean.FALSE);
				manager.addToManagerRoles(mr);
			}
		}

		// Remove roles no longer requested (orphanRemoval deletes the join row).
		for (Iterator<ManagerRole> it = mgrRoles.iterator(); it.hasNext(); ) {
			ManagerRole mr = it.next();
			Long rid = (mr.getRole() == null ? null : mr.getRole().getRoleId());
			if (rid == null || !requested.contains(rid))
				it.remove();
		}
	}

	/**
	 * Upsert the manager's CURRENT-SESSION departments (add/remove) - faithful
	 * merge of updateManager()/addManager(). Departments of OTHER academic sessions
	 * are never touched. The many-to-many join is owned by Department, so both sides
	 * are updated and each changed Department is merged.
	 */
	protected void applyDepartments(TimetableManager manager, TimetableManagerEditRequest request, Long sessionId, org.hibernate.Session hibSession) {
		List<Long> requested = new ArrayList<Long>();
		if (request.getDepartmentIds() != null)
			for (Long id : request.getDepartmentIds())
				if (id != null && !requested.contains(id)) requested.add(id);

		Set<Department> mgrDepts = manager.getDepartments();
		if (mgrDepts == null) { mgrDepts = new HashSet<Department>(); manager.setDepartments(mgrDepts); }

		Set<Long> existingIds = new HashSet<Long>();
		for (Department d : mgrDepts) existingIds.add(d.getUniqueId());

		// Add departments not yet linked.
		for (Long deptId : requested) {
			if (existingIds.contains(deptId)) continue;
			Department dept = DepartmentDAO.getInstance().get(deptId, hibSession);
			if (dept == null) throw new GwtRpcException("Selected department no longer exists.");
			if (dept.getSessionId() == null || !dept.getSessionId().equals(sessionId))
				throw new GwtRpcException("Selected department does not belong to the current academic session.");
			mgrDepts.add(dept);
			dept.addToTimetableManagers(manager);
			hibSession.merge(dept);
		}

		// Remove current-session departments no longer requested; SKIP other sessions.
		for (Iterator<Department> it = mgrDepts.iterator(); it.hasNext(); ) {
			Department d = it.next();
			if (d.getSessionId() == null || !d.getSessionId().equals(sessionId)) continue;
			if (requested.contains(d.getUniqueId())) continue;
			it.remove();
			if (d.getTimetableManagers() != null) d.getTimetableManagers().remove(manager);
			hibSession.merge(d);
		}
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

			// Persist core identity first so that (a) role rows can reference a
			// managed manager and (b) the Department many-to-many join is valid.
			if (create)
				hibSession.persist(manager);

			Long sessionId = context.getUser().getCurrentAcademicSessionId();
			applyRoles(manager, request, context, hibSession);
			applyDepartments(manager, request, sessionId, hibSession);

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
