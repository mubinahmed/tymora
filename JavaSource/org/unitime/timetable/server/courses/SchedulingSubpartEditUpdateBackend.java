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
package org.unitime.timetable.server.courses;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface.SubpartEditResponse;
import org.unitime.timetable.gwt.shared.SchedulingSubpartEditInterface.SubpartEditUpdateRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ItypeDesc;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.dao.DatePatternDAO;
import org.unitime.timetable.model.dao.ItypeDescDAO;
import org.unitime.timetable.model.dao.SchedulingSubpartDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Scheduling Subpart Edit page (legacy
 * schedulingSubpartEdit.action). Persists the subpart-row subset that
 * {@code SchedulingSubpartEditAction.doUpdate()} saves (instructional type, date
 * pattern, auto-spread-in-time, student-allow-overlap); credit configuration and
 * preferences are out of scope. Gated by {@link Right#SchedulingSubpartEdit},
 * transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SubpartEditUpdateRequest.class)
public class SchedulingSubpartEditUpdateBackend implements GwtRpcImplementation<SubpartEditUpdateRequest, SubpartEditResponse> {

	@Override
	public SubpartEditResponse execute(SubpartEditUpdateRequest request, SessionContext context) {
		Long subpartId = request.getSubpartId();
		if (subpartId == null)
			throw new GwtRpcException("No scheduling subpart was specified.");

		context.checkPermission(subpartId, "SchedulingSubpart", Right.SchedulingSubpartEdit);

		SchedulingSubpartDAO dao = SchedulingSubpartDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		SchedulingSubpart ss = dao.get(subpartId);
		if (ss == null)
			throw new GwtRpcException("Scheduling subpart " + subpartId + " was not found.");

		Transaction tx = hibSession.beginTransaction();
		try {
			ss.setAutoSpreadInTime(request.isAutoSpreadInTime());
			ss.setStudentAllowOverlap(request.isStudentAllowOverlap());

			Long dpId = request.getDatePatternId();
			if (dpId == null || dpId.intValue() < 0)
				ss.setDatePattern(null);
			else
				ss.setDatePattern(DatePatternDAO.getInstance().get(dpId));

			if (request.getInstructionalType() != null) {
				ItypeDesc newItype = ItypeDescDAO.getInstance().get(request.getInstructionalType());
				if (newItype != null)
					ss.setItype(newItype);
			}

			ChangeLog.addChange(
					hibSession,
					context,
					ss,
					ChangeLog.Source.SCHEDULING_SUBPART_EDIT,
					ChangeLog.Operation.UPDATE,
					ss.getInstrOfferingConfig().getControllingCourseOffering().getSubjectArea(),
					ss.getManagingDept());

			hibSession.merge(ss);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save scheduling subpart: " + e.getMessage(), e);
		}

		SubpartEditResponse response = new SubpartEditResponse();
		SchedulingSubpartEditBackend.fill(response, ss, context);
		response.setSaved(true);
		return response;
	}
}
