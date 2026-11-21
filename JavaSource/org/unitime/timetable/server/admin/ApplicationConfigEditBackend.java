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
import java.util.List;
import java.util.TreeMap;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.ApplicationConfigEditRequest;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.ApplicationConfigEditResponse;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.ApplicationConfigItem;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.Operation;
import org.unitime.timetable.model.ApplicationConfig;
import org.unitime.timetable.model.SessionConfig;
import org.unitime.timetable.model.dao.ApplicationConfigDAO;
import org.unitime.timetable.model.dao.SessionConfigDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.service.SolverServerService;
import org.unitime.timetable.spring.SpringApplicationContextHolder;

/**
 * Application configuration editor (Angular migration of applicationConfig.action).
 *
 * LOAD (gated by {@link Right#ApplicationConfig}) returns every global
 * {@link ApplicationConfig} plus the {@link SessionConfig} rows of the current
 * academic session (a session row overrides a global row with the same key and is
 * flagged {@code allSessions=false}).
 *
 * SAVE / CREATE / DELETE are gated by {@link Right#ApplicationConfigEdit} and mirror
 * the legacy ApplicationConfigAction ("Update", "Add Setting" / "Save", "Delete"):
 * CREATE adds a global ApplicationConfig (all sessions) or a SessionConfig for the
 * current academic session; every change is pushed to the running application via
 * {@code SolverServerService.setApplicationProperty(sessionId, key, value)}, exactly
 * like the legacy action, so no restart is needed. The legacy page writes no
 * ChangeLog for configuration changes (ApplicationConfig has no numeric unique id),
 * so neither does this backend.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ApplicationConfigEditRequest.class)
public class ApplicationConfigEditBackend implements GwtRpcImplementation<ApplicationConfigEditRequest, ApplicationConfigEditResponse> {

	@Override
	public ApplicationConfigEditResponse execute(ApplicationConfigEditRequest request, SessionContext context) {
		Operation op = request.getOperation() == null ? Operation.LOAD : request.getOperation();
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());

		switch (op) {
		case SAVE:
			context.checkPermission(Right.ApplicationConfigEdit);
			update(request, sessionId);
			break;
		case CREATE:
			context.checkPermission(Right.ApplicationConfigEdit);
			create(request, sessionId);
			break;
		case DELETE:
			context.checkPermission(Right.ApplicationConfigEdit);
			delete(request, sessionId);
			break;
		case LOAD:
		default:
			context.checkPermission(Right.ApplicationConfig);
		}

		ApplicationConfigEditResponse response = new ApplicationConfigEditResponse();
		response.setEditable(context.hasPermission(Right.ApplicationConfigEdit));

		TreeMap<String, ApplicationConfigItem> items = new TreeMap<String, ApplicationConfigItem>(String.CASE_INSENSITIVE_ORDER);
		for (ApplicationConfig config : ApplicationConfigDAO.getInstance().findAll()) {
			if (config.getKey() == null) continue;
			ApplicationConfigItem item = new ApplicationConfigItem();
			item.setKey(config.getKey());
			item.setValue(config.getValue());
			item.setDescription(config.getDescription());
			item.setAllSessions(true);
			items.put(config.getKey(), item);
		}
		if (sessionId != null) {
			for (SessionConfig config : SessionConfig.findAll(sessionId)) {
				if (config.getKey() == null) continue;
				ApplicationConfigItem item = new ApplicationConfigItem();
				item.setKey(config.getKey());
				item.setValue(config.getValue());
				String desc = config.getDescription();
				if (desc == null || desc.isEmpty()) {
					ApplicationConfigItem global = items.get(config.getKey());
					if (global != null) desc = global.getDescription();
				}
				item.setDescription(desc);
				item.setAllSessions(false);
				items.put(config.getKey(), item);
			}
		}
		for (ApplicationConfigItem item : items.values())
			response.addItem(item);
		return response;
	}

	protected SolverServerService getSolverServerService() {
		return (SolverServerService) SpringApplicationContextHolder.getBean("solverServerService");
	}

	private static String key(ApplicationConfigEditRequest request) {
		String key = (request.getKey() == null ? "" : request.getKey().trim());
		if (key.isEmpty())
			throw new GwtRpcException("No configuration key specified.");
		return key;
	}

	/** SAVE: update value / description of an existing key (current-session row first, then global). */
	protected void update(ApplicationConfigEditRequest request, Long sessionId) {
		String key = key(request);
		org.hibernate.Session hibSession = SessionConfigDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();
			SessionConfig sessionConfig = (Boolean.TRUE.equals(request.getAllSessions()) ? null : findSessionConfig(hibSession, key, sessionId));
			if (sessionConfig != null) {
				sessionConfig.setValue(request.getValue());
				if (request.getDescription() != null)
					sessionConfig.setDescription(request.getDescription());
				hibSession.merge(sessionConfig);
				getSolverServerService().setApplicationProperty(sessionId, key, request.getValue());
			} else {
				ApplicationConfig config = ApplicationConfigDAO.getInstance().get(key, hibSession);
				if (config == null)
					throw new GwtRpcException("Unknown configuration key: " + key);
				config.setValue(request.getValue());
				if (request.getDescription() != null)
					config.setDescription(request.getDescription());
				hibSession.merge(config);
				getSolverServerService().setApplicationProperty(null, key, request.getValue());
			}
			hibSession.flush();
			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	/** CREATE: legacy "Add Setting" -> ApplicationConfig (all sessions) or SessionConfig (current session). */
	protected void create(ApplicationConfigEditRequest request, Long sessionId) {
		String key = key(request);
		if (key.matches(".*\\s.*"))
			throw new GwtRpcException("The configuration key must not contain spaces.");
		boolean allSessions = !Boolean.FALSE.equals(request.getAllSessions());

		org.hibernate.Session hibSession = SessionConfigDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();
			if (allSessions) {
				if (ApplicationConfigDAO.getInstance().get(key, hibSession) != null)
					throw new GwtRpcException("Configuration key " + key + " already exists.");
				ApplicationConfig config = new ApplicationConfig();
				config.setKey(key);
				config.setValue(request.getValue());
				config.setDescription(request.getDescription());
				hibSession.persist(config);
				getSolverServerService().setApplicationProperty(null, key, request.getValue());
			} else {
				if (sessionId == null)
					throw new GwtRpcException("No academic session is selected.");
				if (findSessionConfig(hibSession, key, sessionId) != null)
					throw new GwtRpcException("Configuration key " + key + " already exists for the current academic session.");
				SessionConfig config = new SessionConfig();
				config.setKey(key);
				config.setSession(SessionDAO.getInstance().get(sessionId, hibSession));
				config.setValue(request.getValue());
				config.setDescription(request.getDescription());
				hibSession.persist(config);
				getSolverServerService().setApplicationProperty(sessionId, key, request.getValue());
			}
			hibSession.flush();
			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	/** DELETE: legacy "Delete" -> remove the current-session row if any (plus same-valued session rows), else the global row. */
	protected void delete(ApplicationConfigEditRequest request, Long sessionId) {
		String key = key(request);
		org.hibernate.Session hibSession = SessionConfigDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();
			SessionConfig sessionConfig = (Boolean.TRUE.equals(request.getAllSessions()) ? null : findSessionConfig(hibSession, key, sessionId));
			if (sessionConfig == null) {
				ApplicationConfig appConfig = ApplicationConfigDAO.getInstance().get(key, hibSession);
				if (appConfig == null)
					throw new GwtRpcException("Unknown configuration key: " + key);
				hibSession.remove(appConfig);
				getSolverServerService().setApplicationProperty(null, key, null);
			} else {
				String oldValue = sessionConfig.getValue();
				hibSession.remove(sessionConfig);
				getSolverServerService().setApplicationProperty(sessionId, key, null);
				if (oldValue != null) {
					List<SessionConfig> others = new ArrayList<SessionConfig>(hibSession.createQuery(
							"from SessionConfig where key = :key and value = :value", SessionConfig.class)
							.setParameter("key", key).setParameter("value", oldValue).list());
					for (SessionConfig other : others) {
						if (other == sessionConfig) continue;
						getSolverServerService().setApplicationProperty(other.getSession().getUniqueId(), key, null);
						hibSession.remove(other);
					}
				}
			}
			hibSession.flush();
			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}

	private static SessionConfig findSessionConfig(org.hibernate.Session hibSession, String key, Long sessionId) {
		if (sessionId == null) return null;
		return hibSession.createQuery(
				"from SessionConfig where key = :key and session.uniqueId = :sessionId", SessionConfig.class)
				.setParameter("key", key).setParameter("sessionId", sessionId).uniqueResult();
	}
}
