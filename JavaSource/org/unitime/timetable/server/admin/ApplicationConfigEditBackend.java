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

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.ApplicationConfigEditRequest;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.ApplicationConfigEditResponse;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.ApplicationConfigItem;
import org.unitime.timetable.gwt.shared.ApplicationConfigEditInterface.Operation;
import org.unitime.timetable.model.ApplicationConfig;
import org.unitime.timetable.model.dao.ApplicationConfigDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Edit the value / description of existing global {@link ApplicationConfig}
 * entries. LOAD (gated by {@link Right#ApplicationConfig}) returns every config
 * plus an editable flag; SAVE (gated by {@link Right#ApplicationConfigEdit})
 * updates the value/description of an existing key in place and returns the
 * refreshed list.
 *
 * Conservative scope: only the value/description of an existing key is changed
 * (merge-on-update). Creating/deleting keys and the legacy per-session config
 * overrides are intentionally not offered here. NOTE: a running server may cache
 * configuration; changes persist to the database but can require a
 * configuration refresh / restart to take effect. Additive: no existing file
 * modified.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ApplicationConfigEditRequest.class)
public class ApplicationConfigEditBackend implements GwtRpcImplementation<ApplicationConfigEditRequest, ApplicationConfigEditResponse> {

	@Override
	public ApplicationConfigEditResponse execute(ApplicationConfigEditRequest request, SessionContext context) {
		Operation op = request.getOperation() == null ? Operation.LOAD : request.getOperation();

		if (op == Operation.SAVE) {
			context.checkPermission(Right.ApplicationConfigEdit);
			String key = request.getKey();
			if (key == null || key.isEmpty())
				throw new GwtRpcException("No configuration key specified.");
			org.hibernate.Session hibSession = ApplicationConfigDAO.getInstance().getSession();
			ApplicationConfig config = ApplicationConfigDAO.getInstance().get(key, hibSession);
			if (config == null)
				throw new GwtRpcException("Unknown configuration key: " + key);
			// Edit an existing key in place; never create new keys here.
			config.setValue(request.getValue());
			if (request.getDescription() != null)
				config.setDescription(request.getDescription());
			hibSession.merge(config);
			hibSession.flush();
		} else {
			context.checkPermission(Right.ApplicationConfig);
		}

		ApplicationConfigEditResponse response = new ApplicationConfigEditResponse();
		response.setEditable(context.hasPermission(Right.ApplicationConfigEdit));

		List<ApplicationConfig> configs = new ArrayList<ApplicationConfig>(ApplicationConfigDAO.getInstance().findAll());
		Collections.sort(configs, new Comparator<ApplicationConfig>() {
			@Override
			public int compare(ApplicationConfig a, ApplicationConfig b) {
				return str(a.getKey()).compareToIgnoreCase(str(b.getKey()));
			}
		});
		for (ApplicationConfig config : configs) {
			ApplicationConfigItem item = new ApplicationConfigItem();
			try { item.setKey(config.getKey()); } catch (Exception e) {}
			try { item.setValue(config.getValue()); } catch (Exception e) {}
			try { item.setDescription(config.getDescription()); } catch (Exception e) {}
			response.addItem(item);
		}
		return response;
	}

	private static String str(String s) { return s == null ? "" : s; }
}
