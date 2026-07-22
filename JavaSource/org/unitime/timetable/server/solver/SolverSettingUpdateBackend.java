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
package org.unitime.timetable.server.solver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.ParamValue;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingEditResponse;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingUpdateRequest;
import org.unitime.timetable.model.SolverParameter;
import org.unitime.timetable.model.SolverParameterDef;
import org.unitime.timetable.model.SolverPredefinedSetting;
import org.unitime.timetable.model.dao.SolverParameterDefDAO;
import org.unitime.timetable.model.dao.SolverPredefinedSettingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for one solver configuration (legacy solverSettings.action update). Updates the
 * name/description and reconciles the parameter overrides for the parameters sent by the client
 * (use-default removes the override; otherwise create/update it), mirroring the legacy save loop.
 * Gated by {@link Right#SolverConfigurations}, transactional.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SolverSettingUpdateRequest.class)
public class SolverSettingUpdateBackend implements GwtRpcImplementation<SolverSettingUpdateRequest, SolverSettingEditResponse> {

	@Override
	public SolverSettingEditResponse execute(SolverSettingUpdateRequest request, SessionContext context) {
		context.checkPermission(Right.SolverConfigurations);
		if (request.getSettingId() == null)
			throw new GwtRpcException("No solver configuration was specified.");

		SolverPredefinedSettingDAO dao = SolverPredefinedSettingDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		SolverPredefinedSetting setting = dao.get(request.getSettingId());
		if (setting == null)
			throw new GwtRpcException("Solver configuration " + request.getSettingId() + " was not found.");

		Map<Long, ParamValue> reqParams = new HashMap<Long, ParamValue>();
		for (ParamValue pv : request.getParams())
			if (pv.getDefId() != null) reqParams.put(pv.getDefId(), pv);

		Transaction tx = hibSession.beginTransaction();
		try {
			setting.setName(request.getName());
			setting.setDescription(request.getDescription());

			Set<SolverParameter> params = setting.getParameters();
			if (params == null) { params = new HashSet<SolverParameter>(); setting.setParameters(params); }

			for (SolverParameterDef def : SolverParameterDefDAO.getInstance().findAll(hibSession)) {
				ParamValue rp = reqParams.get(def.getUniqueId());
				if (rp == null) continue; // parameter not sent (invisible / other appearance) — leave as-is
				SolverParameter existing = null;
				for (SolverParameter p : params)
					if (def.equals(p.getDefinition())) { existing = p; break; }
				if (rp.isUseDefault()) {
					if (existing != null) { params.remove(existing); hibSession.remove(existing); }
				} else {
					String value = rp.getValue() == null ? def.getDefault() : rp.getValue();
					if (existing == null) {
						existing = new SolverParameter();
						existing.setDefinition(def);
						existing.setValue(value);
						hibSession.persist(existing);
						params.add(existing);
					} else {
						existing.setValue(value);
						hibSession.merge(existing);
					}
				}
			}

			hibSession.merge(setting);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save solver configuration: " + e.getMessage(), e);
		}

		SolverPredefinedSetting reloaded = SolverPredefinedSettingDAO.getInstance().get(request.getSettingId());
		SolverSettingEditResponse response = new SolverSettingEditResponse();
		SolverSettingEditBackend.fill(response, reloaded);
		response.setSaved(true);
		return response;
	}
}
