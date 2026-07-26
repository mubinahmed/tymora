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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolverGroupEditInterface.DepartmentInfo;
import org.unitime.timetable.gwt.shared.SolverGroupEditInterface.ManagerInfo;
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
import org.unitime.timetable.model.dao.DepartmentDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.SolverGroupDAO;
import org.unitime.timetable.model.dao.TimetableManagerDAO;
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

		// Department pool: the departments being used in this session (same source as
		// the legacy form), de-duplicated by id and keyed for a stable label order.
		// Any department already assigned to a group is added too, so an assigned but
		// otherwise-unused department still has a selectable option on the client.
		Map<Long, Department> pool = new LinkedHashMap<Long, Department>();
		for (Department d: Department.findAllBeingUsed(sessionId))
			pool.put(d.getUniqueId(), d);

		// Manager pool: every manager with a department in this session (same source
		// as the legacy form). A group's own managers are added below in case one
		// somehow lacks a current session department but is still assigned.
		Map<Long, TimetableManager> mgrPool = new LinkedHashMap<Long, TimetableManager>();
		for (TimetableManager m: TimetableManagerDAO.getInstance().findAll())
			if (!m.departmentsForSession(sessionId).isEmpty())
				mgrPool.put(m.getUniqueId(), m);

		for (SolverGroup group: SolverGroup.findBySessionId(sessionId)) {
			SolverGroupInfo info = new SolverGroupInfo();
			info.setUniqueId(group.getUniqueId());
			info.setName(group.getName());
			info.setAbbv(group.getAbbv());
			TreeSet<String> depts = new TreeSet<String>();
			if (group.getDepartments() != null)
				for (Department d: group.getDepartments()) {
					depts.add(d.getDeptCode());
					info.addDepartmentId(d.getUniqueId());
					pool.put(d.getUniqueId(), d);
				}
			StringBuilder sb = new StringBuilder();
			for (String d: depts)
				sb.append(sb.length() > 0 ? ", " : "").append(d);
			info.setDepartments(sb.toString());
			if (group.getTimetableManagers() != null)
				for (TimetableManager m: group.getTimetableManagers()) {
					info.addManagerId(m.getUniqueId());
					mgrPool.put(m.getUniqueId(), m);
				}
			boolean hasSolutions = (group.getSolutions() != null && !group.getSolutions().isEmpty());
			info.setCommitted(group.getCommittedSolution() != null);
			info.setCanEdit(true);
			// The legacy form disables department editing once the group has solutions.
			info.setDepartmentsEditable(!hasSolutions);
			// Deleting a group with solutions would cascade-remove those solutions;
			// the legacy screen forbids it, so we mirror that here.
			info.setCanDelete(!hasSolutions);
			response.addGroup(info);
		}

		List<Department> sorted = new ArrayList<Department>(pool.values());
		sorted.sort(new Comparator<Department>() {
			@Override
			public int compare(Department a, Department b) {
				int cmp = a.getLabel().compareToIgnoreCase(b.getLabel());
				if (cmp != 0) return cmp;
				return a.getUniqueId().compareTo(b.getUniqueId());
			}
		});
		for (Department d: sorted) {
			DepartmentInfo di = new DepartmentInfo();
			di.setUniqueId(d.getUniqueId());
			di.setLabel(d.getLabel());
			di.setSolverGroupId(d.getSolverGroup() == null ? null : d.getSolverGroup().getUniqueId());
			response.addDepartment(di);
		}

		List<TimetableManager> sortedMgrs = new ArrayList<TimetableManager>(mgrPool.values());
		sortedMgrs.sort(new Comparator<TimetableManager>() {
			@Override
			public int compare(TimetableManager a, TimetableManager b) {
				int cmp = managerLabel(a, sessionId).compareToIgnoreCase(managerLabel(b, sessionId));
				if (cmp != 0) return cmp;
				return a.getUniqueId().compareTo(b.getUniqueId());
			}
		});
		for (TimetableManager m: sortedMgrs) {
			ManagerInfo mi = new ManagerInfo();
			mi.setUniqueId(m.getUniqueId());
			mi.setLabel(managerLabel(m, sessionId));
			response.addManager(mi);
		}
		return response;
	}

	/** "Name (DEPT, DEPT)" — manager name plus its department codes in this session. */
	private static String managerLabel(TimetableManager m, Long sessionId) {
		StringBuilder codes = new StringBuilder();
		for (Department d: new TreeSet<Department>(m.departmentsForSession(sessionId)))
			codes.append(codes.length() > 0 ? ", " : "").append(d.getDeptCode());
		return m.getName() + (codes.length() == 0 ? "" : " (" + codes + ")");
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

			// Resolve and validate the selected departments before mutating anything:
			// each must belong to this session and to no other solver group (a
			// department belongs to at most one solver group per session).
			List<Department> selected = resolveDepartments(request.getDepartmentIds(), hibSession, sessionId, group);
			List<TimetableManager> selectedManagers = resolveManagers(request.getManagerIds(), hibSession);

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
				// Assign the selected departments (mirror of the legacy create).
				for (Department dept: selected) {
					group.getDepartments().add(dept);
					dept.setSolverGroup(group);
					hibSession.merge(dept);
				}
				// Assign the selected timetable managers (many-to-many).
				for (TimetableManager mgr: selectedManagers) {
					group.getTimetableManagers().add(mgr);
					mgr.getSolverGroups().add(group);
					hibSession.merge(mgr);
				}
				ChangeLog.addChange(hibSession, context, group,
						ChangeLog.Source.SOLVER_GROUP_EDIT, ChangeLog.Operation.CREATE, null, null);
			} else {
				// MERGE update: touch only the edited fields and department membership;
				// leave timetable managers and solutions exactly as they are.
				group.setName(name);
				group.setAbbv(abbv);
				boolean departmentsEditable = (group.getSolutions() == null || group.getSolutions().isEmpty());
				if (departmentsEditable) {
					if (group.getDepartments() == null)
						group.setDepartments(new HashSet<Department>());
					// Reassign only the departments involved: add newly-selected ones,
					// remove departments the user cleared; never touch anything else.
					HashSet<Department> old = new HashSet<Department>(group.getDepartments());
					for (Department dept: selected) {
						if (old.remove(dept)) {
							// already a member -> unchanged
						} else {
							group.getDepartments().add(dept);
							dept.setSolverGroup(group);
							hibSession.merge(dept);
						}
					}
					for (Department dept: old) {
						group.getDepartments().remove(dept);
						dept.setSolverGroup(null);
						hibSession.merge(dept);
					}
				}
				// Reconcile timetable managers (always editable, mirroring the legacy
				// form which updates managers even when departments are locked).
				if (group.getTimetableManagers() == null)
					group.setTimetableManagers(new HashSet<TimetableManager>());
				HashSet<TimetableManager> oldManagers = new HashSet<TimetableManager>(group.getTimetableManagers());
				for (TimetableManager mgr: selectedManagers) {
					if (oldManagers.remove(mgr)) {
						// already a member -> unchanged
					} else {
						group.getTimetableManagers().add(mgr);
						mgr.getSolverGroups().add(group);
						hibSession.merge(mgr);
					}
				}
				for (TimetableManager mgr: oldManagers) {
					group.getTimetableManagers().remove(mgr);
					mgr.getSolverGroups().remove(group);
					hibSession.merge(mgr);
				}
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

	/**
	 * Load the selected departments and validate that each one belongs to the current
	 * session and is not already assigned to a different solver group. Duplicate ids
	 * are collapsed. {@code group} is the group being edited (null on create); a
	 * department already belonging to it is allowed.
	 */
	protected List<Department> resolveDepartments(List<Long> departmentIds, org.hibernate.Session hibSession,
			Long sessionId, SolverGroup group) {
		List<Department> result = new ArrayList<Department>();
		if (departmentIds == null) return result;
		HashSet<Long> seen = new HashSet<Long>();
		for (Long deptId: departmentIds) {
			if (deptId == null || !seen.add(deptId)) continue;
			Department dept = DepartmentDAO.getInstance().get(deptId, hibSession);
			if (dept == null)
				throw new GwtRpcException("One of the selected departments no longer exists.");
			if (dept.getSession() == null || !sessionId.equals(dept.getSession().getUniqueId()))
				throw new GwtRpcException("Department " + dept.getDeptCode() + " belongs to a different academic session.");
			SolverGroup current = dept.getSolverGroup();
			if (current != null && (group == null || !current.getUniqueId().equals(group.getUniqueId())))
				throw new GwtRpcException("Department " + dept.getDeptCode()
						+ " is already assigned to solver group " + current.getAbbv() + ".");
			result.add(dept);
		}
		return result;
	}

	/**
	 * Load the selected timetable managers, collapsing duplicate ids. Managers are a
	 * many-to-many relation (a manager may serve several solver groups), so there is
	 * no exclusivity check — only existence.
	 */
	protected List<TimetableManager> resolveManagers(List<Long> managerIds, org.hibernate.Session hibSession) {
		List<TimetableManager> result = new ArrayList<TimetableManager>();
		if (managerIds == null) return result;
		HashSet<Long> seen = new HashSet<Long>();
		for (Long mgrId: managerIds) {
			if (mgrId == null || !seen.add(mgrId)) continue;
			TimetableManager mgr = TimetableManagerDAO.getInstance().get(mgrId, hibSession);
			if (mgr == null)
				throw new GwtRpcException("One of the selected managers no longer exists.");
			result.add(mgr);
		}
		return result;
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
