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
import org.unitime.timetable.gwt.shared.DistributionTypeEditInterface.DistTypeInfo;
import org.unitime.timetable.gwt.shared.DistributionTypeEditInterface.DistTypeListRequest;
import org.unitime.timetable.gwt.shared.DistributionTypeEditInterface.DistTypeListResponse;
import org.unitime.timetable.gwt.shared.DistributionTypeEditInterface.IdName;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DistributionType;
import org.unitime.timetable.model.PreferenceLevel;
import org.unitime.timetable.model.dao.DistributionTypeDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Distribution Type Edit page (legacy distributionTypeEdit.action).
 * Returns every distribution type plus the shared option lists (session departments, preference
 * levels). See {@link org.unitime.timetable.gwt.shared.DistributionTypeEditInterface}; gated by
 * {@link Right#DistributionTypeEdit}. Its {@code fill()} is shared with the save bean.
 *
 * @author Angular migration
 */
@GwtRpcImplements(DistTypeListRequest.class)
public class DistributionTypeListBackend implements GwtRpcImplementation<DistTypeListRequest, DistTypeListResponse> {

	@Override
	public DistTypeListResponse execute(DistTypeListRequest request, SessionContext context) {
		context.checkPermission(Right.DistributionTypeEdit);
		DistTypeListResponse response = new DistTypeListResponse();
		fill(response, context);
		return response;
	}

	@SuppressWarnings("unchecked")
	static void fill(DistTypeListResponse r, SessionContext context) {
		Long sessionId = context.getUser().getCurrentAcademicSessionId();

		for (PreferenceLevel pl : PreferenceLevel.getPreferenceLevelList())
			r.addPrefLevel(new IdName(pl.getUniqueId(), pl.getPrefName()));

		List<Department> depts = DistributionTypeDAO.getInstance().getSession()
				.createQuery("from Department where session.uniqueId = :sessionId order by deptCode", Department.class)
				.setParameter("sessionId", sessionId).list();
		for (Department d : depts)
			r.addDepartment(new IdName(d.getUniqueId(), d.getLabel()));

		List<DistributionType> types = new ArrayList<DistributionType>(DistributionType.findAll(false, false, null));
		Collections.sort(types, new Comparator<DistributionType>() {
			@Override public int compare(DistributionType a, DistributionType b) {
				String ra = a.getReference() == null ? "" : a.getReference();
				String rb = b.getReference() == null ? "" : b.getReference();
				return ra.compareToIgnoreCase(rb);
			}
		});
		for (DistributionType t : types) {
			DistTypeInfo info = new DistTypeInfo();
			info.setId(t.getUniqueId());
			info.setReference(t.getReference());
			info.setLabel(t.getLabel());
			info.setAbbreviation(t.getAbbreviation());
			info.setDescr(t.getDescr());
			info.setInstructorPref(Boolean.TRUE.equals(t.isInstructorPref()));
			info.setExamPref(Boolean.TRUE.equals(t.isExamPref()));
			info.setSurvey(t.effectiveSurvey());
			info.setVisible(Boolean.TRUE.equals(t.isVisible()));
			String allowed = t.getAllowedPref();
			for (PreferenceLevel pl : PreferenceLevel.getPreferenceLevelList())
				if (allowed == null || allowed.indexOf(PreferenceLevel.prolog2char(pl.getPrefProlog())) >= 0)
					info.addAllowedPrefId(pl.getUniqueId());
			for (Department d : (java.util.Set<Department>) t.getDepartments())
				if (sessionId.equals(d.getSessionId()))
					info.addDepartmentId(d.getUniqueId());
			r.addType(info);
		}
	}
}
