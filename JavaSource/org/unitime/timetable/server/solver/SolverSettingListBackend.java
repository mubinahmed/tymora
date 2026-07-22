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

import java.util.List;

import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SettingInfo;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingListRequest;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingListResponse;
import org.unitime.timetable.model.SolverPredefinedSetting;
import org.unitime.timetable.model.dao.SolverPredefinedSettingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend listing the solver configurations (legacy solverSettings.action list). See
 * {@link org.unitime.timetable.gwt.shared.SolverSettingsInterface}; gated by
 * {@link Right#SolverConfigurations}. Its {@code fill()} is shared with the delete bean.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SolverSettingListRequest.class)
public class SolverSettingListBackend implements GwtRpcImplementation<SolverSettingListRequest, SolverSettingListResponse> {

	@Override
	public SolverSettingListResponse execute(SolverSettingListRequest request, SessionContext context) {
		context.checkPermission(Right.SolverConfigurations);
		SolverSettingListResponse response = new SolverSettingListResponse();
		fill(response);
		return response;
	}

	static void fill(SolverSettingListResponse r) {
		List<SolverPredefinedSetting> settings = SolverPredefinedSettingDAO.getInstance().getSession()
				.createQuery("from SolverPredefinedSetting order by appearance, name", SolverPredefinedSetting.class).list();
		for (SolverPredefinedSetting s : settings) {
			SettingInfo info = new SettingInfo();
			info.setId(s.getUniqueId());
			info.setName(s.getName());
			info.setDescription(s.getDescription());
			info.setAppearanceLabel(s.getAppearanceType() == null ? "" : s.getAppearanceType().getLabel());
			r.addSetting(info);
		}
	}
}
