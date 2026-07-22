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
import java.util.List;
import java.util.Map;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.GroupInfo;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.ParamInfo;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingEditRequest;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingEditResponse;
import org.unitime.timetable.model.SolverParameter;
import org.unitime.timetable.model.SolverParameterDef;
import org.unitime.timetable.model.SolverParameterGroup;
import org.unitime.timetable.model.SolverPredefinedSetting;
import org.unitime.timetable.model.dao.SolverParameterGroupDAO;
import org.unitime.timetable.model.dao.SolverPredefinedSettingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend loading one solver configuration for editing (legacy solverSettings.action Edit).
 * Returns its visible parameters grouped by the appearance's solver type, each with its current
 * value + use-default flag. Gated by {@link Right#SolverConfigurations}. {@code fill()} is shared
 * with the save bean.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SolverSettingEditRequest.class)
public class SolverSettingEditBackend implements GwtRpcImplementation<SolverSettingEditRequest, SolverSettingEditResponse> {

	@Override
	public SolverSettingEditResponse execute(SolverSettingEditRequest request, SessionContext context) {
		context.checkPermission(Right.SolverConfigurations);
		if (request.getSettingId() == null)
			throw new GwtRpcException("No solver configuration was specified.");
		SolverPredefinedSetting setting = SolverPredefinedSettingDAO.getInstance().get(request.getSettingId());
		if (setting == null)
			throw new GwtRpcException("Solver configuration " + request.getSettingId() + " was not found.");
		SolverSettingEditResponse response = new SolverSettingEditResponse();
		fill(response, setting);
		return response;
	}

	static void fill(SolverSettingEditResponse r, SolverPredefinedSetting setting) {
		r.setId(setting.getUniqueId());
		r.setName(setting.getName());
		r.setDescription(setting.getDescription());
		r.setAppearanceLabel(setting.getAppearanceType() == null ? "" : setting.getAppearanceType().getLabel());

		SolverParameterGroup.SolverType solverType = setting.getAppearanceType() == null ? null : setting.getAppearanceType().getSolverType();

		// Current parameter overrides (defId -> value).
		Map<Long, String> current = new HashMap<Long, String>();
		if (setting.getParameters() != null)
			for (SolverParameter p : setting.getParameters())
				if (p.getDefinition() != null)
					current.put(p.getDefinition().getUniqueId(), p.getValue());

		List<SolverParameterGroup> groups = SolverParameterGroupDAO.getInstance().getSession()
				.createQuery("from SolverParameterGroup order by order", SolverParameterGroup.class).list();
		for (SolverParameterGroup g : groups) {
			if (solverType != null && g.getSolverType() != solverType) continue;
			GroupInfo gi = new GroupInfo();
			gi.setName(g.getName());
			gi.setDescription(g.getDescription());
			boolean any = false;
			java.util.List<SolverParameterDef> defs = new java.util.ArrayList<SolverParameterDef>(g.getParameters());
			defs.sort((a, b) -> {
				int oa = a.getOrder() == null ? 0 : a.getOrder();
				int ob = b.getOrder() == null ? 0 : b.getOrder();
				return oa - ob;
			});
			for (SolverParameterDef def : defs) {
				if (!Boolean.TRUE.equals(def.isVisible())) continue;
				ParamInfo pi = new ParamInfo();
				pi.setDefId(def.getUniqueId());
				pi.setName(def.getName());
				pi.setDescription(def.getDescription());
				pi.setType(def.getType());
				pi.setDefaultValue(def.getDefault());
				if (current.containsKey(def.getUniqueId())) {
					String value = current.get(def.getUniqueId());
					if ("boolean".equals(def.getType()) && "on".equals(value)) value = "true";
					pi.setValue(value);
					pi.setUseDefault(false);
				} else {
					pi.setValue(def.getDefault());
					pi.setUseDefault(true);
				}
				gi.addParam(pi);
				any = true;
			}
			if (any) r.addGroup(gi);
		}
	}
}
