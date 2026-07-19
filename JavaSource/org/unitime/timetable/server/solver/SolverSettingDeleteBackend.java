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

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingDeleteRequest;
import org.unitime.timetable.gwt.shared.SolverSettingsInterface.SolverSettingListResponse;
import org.unitime.timetable.model.SolverPredefinedSetting;
import org.unitime.timetable.model.dao.SolverPredefinedSettingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Delete backend for a solver configuration (legacy solverSettings.action delete). Gated by
 * {@link Right#SolverConfigurations}. Returns the refreshed list.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SolverSettingDeleteRequest.class)
public class SolverSettingDeleteBackend implements GwtRpcImplementation<SolverSettingDeleteRequest, SolverSettingListResponse> {

	@Override
	public SolverSettingListResponse execute(SolverSettingDeleteRequest request, SessionContext context) {
		context.checkPermission(Right.SolverConfigurations);
		if (request.getSettingId() == null)
			throw new GwtRpcException("No solver configuration was specified.");

		SolverPredefinedSettingDAO dao = SolverPredefinedSettingDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		SolverPredefinedSetting setting = dao.get(request.getSettingId());
		if (setting == null)
			throw new GwtRpcException("Solver configuration " + request.getSettingId() + " was not found.");

		Transaction tx = hibSession.beginTransaction();
		try {
			hibSession.remove(setting);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to delete solver configuration: " + e.getMessage(), e);
		}

		SolverSettingListResponse response = new SolverSettingListResponse();
		SolverSettingListBackend.fill(response);
		return response;
	}
}
