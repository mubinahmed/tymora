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

import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ApplicationConfigListInterface.ApplicationConfigItem;
import org.unitime.timetable.gwt.shared.ApplicationConfigListInterface.ApplicationConfigListRequest;
import org.unitime.timetable.gwt.shared.ApplicationConfigListInterface.ApplicationConfigListResponse;
import org.unitime.timetable.model.ApplicationConfig;
import org.unitime.timetable.model.dao.ApplicationConfigDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only listing of the persisted {@link ApplicationConfig} entries
 * (Angular migration of the legacy applicationConfig.action list view). Gated
 * by {@link Right#ApplicationConfig}. Editing application settings is
 * intentionally not offered here; the legacy page remains the place to mutate
 * configuration until a verified editor is ported. Additive: introduces no
 * changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ApplicationConfigListRequest.class)
public class ApplicationConfigListBackend implements GwtRpcImplementation<ApplicationConfigListRequest, ApplicationConfigListResponse> {

	@Override
	public ApplicationConfigListResponse execute(ApplicationConfigListRequest request, SessionContext context) {
		context.checkPermission(Right.ApplicationConfig);

		ApplicationConfigListResponse response = new ApplicationConfigListResponse();

		List<ApplicationConfig> configs = new ArrayList<ApplicationConfig>(ApplicationConfigDAO.getInstance().findAll());
		Collections.sort(configs, new Comparator<ApplicationConfig>() {
			@Override
			public int compare(ApplicationConfig a, ApplicationConfig b) {
				return str(a.getKey()).compareToIgnoreCase(str(b.getKey()));
			}
		});

		for (ApplicationConfig config : configs) {
			ApplicationConfigItem item = new ApplicationConfigItem();
			try {
				item.setKey(config.getKey());
			} catch (Exception e) {}
			try {
				item.setValue(config.getValue());
			} catch (Exception e) {}
			try {
				item.setDescription(config.getDescription());
			} catch (Exception e) {}
			response.addItem(item);
		}

		return response;
	}

	private static String str(String s) { return s == null ? "" : s; }
}
