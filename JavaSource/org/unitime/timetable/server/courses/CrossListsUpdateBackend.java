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

import java.util.HashMap;
import java.util.Map;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CourseReservation;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossListsResponse;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossListsUpdateRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Cross Lists page — persists ONLY the safe case-2 of
 * {@code CrossListsModifyAction.doUpdate()}: the controlling course flag and the
 * per-course reservation limits for the EXISTING set of cross-listed courses. It does
 * NOT add or remove courses (that path splits/merges instructional offerings). Gated by
 * {@link Right#InstructionalOfferingCrossLists}, transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(CrossListsUpdateRequest.class)
public class CrossListsUpdateBackend implements GwtRpcImplementation<CrossListsUpdateRequest, CrossListsResponse> {

	@Override
	public CrossListsResponse execute(CrossListsUpdateRequest request, SessionContext context) {
		Long offeringId = request.getOfferingId();
		if (offeringId == null)
			throw new GwtRpcException("No instructional offering was specified.");

		context.checkPermission(offeringId, "InstructionalOffering", Right.InstructionalOfferingCrossLists);

		InstructionalOfferingDAO dao = InstructionalOfferingDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		InstructionalOffering io = dao.get(offeringId);
		if (io == null)
			throw new GwtRpcException("Instructional offering " + offeringId + " was not found.");

		Map<Long, Integer> reservations = new HashMap<Long, Integer>();
		for (CourseReservation cr : request.getCourses())
			if (cr.getCourseId() != null)
				reservations.put(cr.getCourseId(), cr.getReservation());

		Long controllingId = request.getControllingCourseId();
		boolean matchesExisting = false;
		for (CourseOffering co : io.getCourseOfferings())
			if (co.getUniqueId().equals(controllingId)) { matchesExisting = true; break; }
		if (!matchesExisting)
			throw new GwtRpcException("The controlling course must be one of the cross-listed courses.");

		boolean singleCourseLimit = ApplicationProperty.ModifyCrossListSingleCourseLimit.isTrue();
		int count = io.getCourseOfferings().size();

		Transaction tx = hibSession.beginTransaction();
		try {
			for (CourseOffering co : io.getCourseOfferings()) {
				co.setIsControl(co.getUniqueId().equals(controllingId));
				Integer r = reservations.get(co.getUniqueId());
				if (singleCourseLimit)
					co.setReservation(r);
				else
					co.setReservation(count > 1 ? r : null);
				hibSession.merge(co);
			}

			ChangeLog.addChange(
					hibSession,
					context,
					io,
					ChangeLog.Source.CROSS_LIST,
					ChangeLog.Operation.UPDATE,
					io.getControllingCourseOffering().getSubjectArea(),
					null);

			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save cross lists: " + e.getMessage(), e);
		}

		CrossListsResponse response = new CrossListsResponse();
		CrossListsBackend.fill(response, io, context);
		response.setSaved(true);
		return response;
	}
}
