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

import java.util.HashSet;
import java.util.Set;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.DistributionTypeEditInterface.DistTypeListResponse;
import org.unitime.timetable.gwt.shared.DistributionTypeEditInterface.DistTypeUpdateRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DistributionType;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.dao.DepartmentDAO;
import org.unitime.timetable.model.dao.DistributionTypeDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Distribution Type Edit page (legacy distributionTypeEdit.action).
 * Updates one distribution type's label/abbreviation/description/flags, rebuilds its encoded
 * {@code allowedPref} from the selected preference levels (in preference-level order), and
 * reconciles its department set within the current session (mirroring the legacy action). Gated
 * by {@link Right#DistributionTypeEdit}, transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(DistTypeUpdateRequest.class)
public class DistributionTypeUpdateBackend implements GwtRpcImplementation<DistTypeUpdateRequest, DistTypeListResponse> {

	@Override
	public DistTypeListResponse execute(DistTypeUpdateRequest request, SessionContext context) {
		context.checkPermission(Right.DistributionTypeEdit);
		if (request.getId() == null)
			throw new GwtRpcException("No distribution type was specified.");

		Long sessionId = context.getUser().getCurrentAcademicSessionId();
		DistributionTypeDAO dao = DistributionTypeDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		DistributionType type = dao.get(request.getId());
		if (type == null)
			throw new GwtRpcException("Distribution type " + request.getId() + " was not found.");

		Transaction tx = hibSession.beginTransaction();
		try {
			type.setLabel(request.getLabel());
			type.setAbbreviation(request.getAbbreviation());
			type.setDescr(request.getDescr());
			type.setInstructorPref(Boolean.valueOf(request.isInstructorPref()));
			type.setSurvey(Boolean.valueOf(request.isSurvey()));
			type.setVisible(Boolean.valueOf(request.isVisible()));

			// Rebuild the encoded allowed-preference string in preference-level order.
			Set<Long> allowed = new HashSet<Long>(request.getAllowedPrefIds());
			StringBuilder ap = new StringBuilder();
			for (PreferenceLevel pl : PreferenceLevel.getPreferenceLevelList())
				if (allowed.contains(pl.getUniqueId()))
					ap.append(PreferenceLevel.prolog2char(pl.getPrefProlog()));
			type.setAllowedPref(ap.toString());

			// Reconcile departments (current session only; other-session links are preserved).
			Set<Department> oldDepts = new HashSet<Department>(type.getDepartments());
			for (Long deptId : request.getDepartmentIds()) {
				Department d = DepartmentDAO.getInstance().get(deptId, hibSession);
				if (d == null) continue;
				if (!oldDepts.remove(d)) type.getDepartments().add(d);
			}
			for (Department d : oldDepts)
				if (sessionId.equals(d.getSessionId()))
					type.getDepartments().remove(d);

			ChangeLog.addChange(hibSession, context, type,
					ChangeLog.Source.DIST_TYPE_EDIT, ChangeLog.Operation.UPDATE, null, null);

			hibSession.merge(type);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save distribution type: " + e.getMessage(), e);
		}

		DistTypeListResponse response = new DistTypeListResponse();
		DistributionTypeListBackend.fill(response, context);
		response.setSaved(true);
		return response;
	}
}
