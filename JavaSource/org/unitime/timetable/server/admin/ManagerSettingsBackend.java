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

import java.util.StringTokenizer;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ManagerSettingsInterface.ManagerSettingsRequest;
import org.unitime.timetable.gwt.shared.ManagerSettingsInterface.ManagerSettingsResponse;
import org.unitime.timetable.gwt.shared.ManagerSettingsInterface.Operation;
import org.unitime.timetable.gwt.shared.ManagerSettingsInterface.SettingOption;
import org.unitime.timetable.gwt.shared.ManagerSettingsInterface.SettingRecord;
import org.unitime.timetable.model.Settings;
import org.unitime.timetable.model.dao.SettingsDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Backend for the legacy Struts managerSettings page (personal settings for the
 * signed-in user). LOAD returns every defined {@link Settings} key with the
 * current user's chosen value + the allowed value/label choices parsed from
 * Settings.allowedValues ("value:label,value:label"). SAVE persists a single
 * value against the signed-in user only via UserContext.setProperty(key, value)
 * -- exactly what the legacy ManagerSettingsAction did on its "Update" op -- so
 * no global reference data is ever mutated.
 *
 * Every operation is gated by Right.SettingsUser, matching the legacy action.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ManagerSettingsRequest.class)
public class ManagerSettingsBackend implements GwtRpcImplementation<ManagerSettingsRequest, ManagerSettingsResponse> {

	@Override
	public ManagerSettingsResponse execute(ManagerSettingsRequest request, SessionContext context) {
		context.checkPermission(Right.SettingsUser);

		if ((request.getOperation() == null ? Operation.LOAD : request.getOperation()) == Operation.SAVE)
			save(request, context);

		return load(context);
	}

	protected void save(ManagerSettingsRequest request, SessionContext context) {
		if (request.getKey() == null || request.getKey().trim().isEmpty())
			throw new GwtRpcException("No setting key provided.");

		String key = request.getKey().trim();
		Settings setting = Settings.getSetting(key);
		if (setting == null)
			throw new GwtRpcException("Setting '" + key + "' does not exist.");

		String value = request.getValue();
		// Restrict the value to one of the setting's allowed values, mirroring the
		// legacy form's validation (the value is stored raw, without a label).
		if (value != null && !isAllowed(setting, value))
			throw new GwtRpcException("Value '" + value + "' is not allowed for setting '" + setting.getDescription() + "'.");

		// Writes only the signed-in user's own property (user_data row).
		context.getUser().setProperty(key, value);
	}

	protected boolean isAllowed(Settings setting, String value) {
		String allowed = setting.getAllowedValues();
		if (allowed == null || allowed.trim().isEmpty()) return true;
		for (StringTokenizer k = new StringTokenizer(allowed, ","); k.hasMoreTokens(); ) {
			String v = k.nextToken().trim();
			int idx = v.indexOf(':');
			String optValue = (idx < 0 ? v : v.substring(0, idx)).trim();
			if (optValue.equals(value)) return true;
		}
		return false;
	}

	protected ManagerSettingsResponse load(SessionContext context) {
		ManagerSettingsResponse response = new ManagerSettingsResponse();
		response.setEditable(context.hasPermission(Right.SettingsUser));

		for (Settings s : SettingsDAO.getInstance().getSession()
				.createQuery("from Settings order by key", Settings.class).setCacheable(true).list()) {
			SettingRecord r = new SettingRecord();
			try {
				r.setId(s.getUniqueId());
				r.setKey(s.getKey());
				r.setName(s.getDescription());
				r.setDefaultValue(s.getDefaultValue());
				r.setValue(context.getUser().getProperty(s.getKey(), s.getDefaultValue()));
				String allowed = s.getAllowedValues();
				if (allowed != null) {
					for (StringTokenizer k = new StringTokenizer(allowed, ","); k.hasMoreTokens(); ) {
						String v = k.nextToken().trim();
						int idx = v.indexOf(':');
						String optValue = (idx < 0 ? v : v.substring(0, idx)).trim();
						String optLabel = (idx < 0 ? v : v.substring(idx + 1)).trim();
						r.addOption(new SettingOption(optValue, optLabel));
					}
				}
			} catch (Exception e) {
				// Lazy-init / projection safety: skip any setting that fails to render.
				continue;
			}
			response.addRecord(r);
		}
		return response;
	}
}
