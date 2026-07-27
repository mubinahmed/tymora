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

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ReliefConfigInterface.Operation;
import org.unitime.timetable.gwt.shared.ReliefConfigInterface.ReliefConfigRequest;
import org.unitime.timetable.gwt.shared.ReliefConfigInterface.ReliefConfigResponse;
import org.unitime.timetable.gwt.shared.ReliefConfigInterface.StaffOption;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.ReliefConfiguration;
import org.unitime.timetable.model.dao.ReliefConfigurationDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Backing bean for the Relief Planning rules-configuration screen (see
 * {@link ReliefConfigInterface}). Loads/saves the single {@link ReliefConfiguration}
 * row for the current academic session. Additive — introduces no changes to existing
 * behavior.
 *
 * @author Angular migration (Relief Planning)
 */
@GwtRpcImplements(ReliefConfigRequest.class)
public class ReliefConfigBackend implements GwtRpcImplementation<ReliefConfigRequest, ReliefConfigResponse> {

	@Override
	public ReliefConfigResponse execute(ReliefConfigRequest request, SessionContext context) {
		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");
		context.checkPermission(Right.ReliefPlanning);
		boolean canManage = context.hasPermission(Right.ReliefPlanningEdit);

		org.hibernate.Session hibSession = ReliefConfigurationDAO.getInstance().getSession();
		Transaction tx = null;
		try {
			tx = hibSession.beginTransaction();
			if (request.getOperation() == Operation.SAVE) {
				if (!canManage) throw new GwtRpcException("You are not allowed to change the relief configuration.");
				save(request, sessionId, hibSession);
			}
			ReliefConfigResponse response = load(sessionId, canManage, hibSession);
			tx.commit();
			return response;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Exception e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException("Failed to load/save relief configuration: " + e.getMessage(), e);
		}
	}

	private ReliefConfigResponse load(Long sessionId, boolean canManage, org.hibernate.Session hibSession) {
		ReliefConfiguration cfg = ReliefConfiguration.getOrDefault(hibSession, sessionId);
		ReliefConfigResponse r = new ReliefConfigResponse();
		r.setCanManage(canManage);
		r.setWeeklyCap(cfg.weeklyCap());
		r.setPreferContinuity(cfg.preferContinuity());
		r.setExcludeNonTeaching(cfg.excludeNonTeaching());
		r.setSameDeptFirst(cfg.sameDeptFirst());
		for (String uid: cfg.exemptUidSet())
			r.addExemptUid(uid);

		Map<String, String> staff = new LinkedHashMap<String, String>();
		for (DepartmentalInstructor di: DepartmentalInstructor.findInstructorsForSession(sessionId)) {
			if (di.getExternalUniqueId() == null || di.getExternalUniqueId().isEmpty()) continue;
			staff.putIfAbsent(di.getExternalUniqueId(), di.nameLastNameFirst());
		}
		for (Map.Entry<String, String> e: staff.entrySet())
			r.addStaff(new StaffOption(e.getKey(), e.getValue()));
		return r;
	}

	private void save(ReliefConfigRequest request, Long sessionId, org.hibernate.Session hibSession) {
		ReliefConfiguration cfg = ReliefConfigurationDAO.getInstance().findBySession(hibSession, sessionId);
		boolean create = (cfg == null);
		if (create) {
			cfg = new ReliefConfiguration();
			cfg.setSession(SessionDAO.getInstance().get(sessionId, hibSession));
		}
		cfg.setWeeklyCap(request.getWeeklyCap());
		cfg.setPreferContinuity(request.isPreferContinuity());
		cfg.setExcludeNonTeaching(request.isExcludeNonTeaching());
		cfg.setSameDeptFirst(request.isSameDeptFirst());
		StringBuilder sb = new StringBuilder();
		if (request.getExemptUids() != null)
			for (String uid: request.getExemptUids())
				if (uid != null && !uid.trim().isEmpty())
					sb.append(sb.length() > 0 ? "," : "").append(uid.trim());
		cfg.setExemptUids(sb.length() == 0 ? null : sb.toString());
		if (create) hibSession.persist(cfg); else hibSession.merge(cfg);
	}
}
