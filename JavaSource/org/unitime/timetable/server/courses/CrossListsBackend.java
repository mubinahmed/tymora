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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossCourse;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossListsRequest;
import org.unitime.timetable.gwt.shared.CrossListsInterface.CrossListsResponse;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.comparators.CourseOfferingComparator;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Cross Lists page (legacy crossListsModify.action).
 * Lists the cross-listed courses of an offering with their controlling flag and
 * reservation limits. See {@link org.unitime.timetable.gwt.shared.CrossListsInterface};
 * gated by {@link Right#InstructionalOfferingCrossLists}. The companion
 * {@code CrossListsUpdateBackend} saves controlling course + reservations.
 *
 * @author Angular migration
 */
@GwtRpcImplements(CrossListsRequest.class)
public class CrossListsBackend implements GwtRpcImplementation<CrossListsRequest, CrossListsResponse> {

	@Override
	public CrossListsResponse execute(CrossListsRequest request, SessionContext context) {
		Long offeringId = request.getOfferingId();
		if (offeringId == null)
			throw new GwtRpcException("No instructional offering was specified.");

		context.checkPermission(offeringId, "InstructionalOffering", Right.InstructionalOfferingCrossLists);

		InstructionalOffering io = InstructionalOfferingDAO.getInstance().get(offeringId);
		if (io == null)
			throw new GwtRpcException("Instructional offering " + offeringId + " was not found.");

		CrossListsResponse response = new CrossListsResponse();
		fill(response, io, context);
		return response;
	}

	/** Populate the response's read state from the offering (shared with the save bean). */
	static void fill(CrossListsResponse r, InstructionalOffering io, SessionContext context) {
		r.setOfferingId(io.getUniqueId());
		r.setControllingCourseId(io.getControllingCourseOffering().getUniqueId());
		r.setOfferingName(io.getCourseNameWithTitle());
		r.setIoLimit(io.getLimit());
		r.setUnlimited(io.hasUnlimitedEnrollment());
		r.setSingleCourseLimit(ApplicationProperty.ModifyCrossListSingleCourseLimit.isTrue());

		List<CourseOffering> offerings = new ArrayList<CourseOffering>(io.getCourseOfferings());
		Collections.sort(offerings, new CourseOfferingComparator(CourseOfferingComparator.COMPARE_BY_CTRL_CRS));
		for (CourseOffering co : offerings) {
			CrossCourse c = new CrossCourse();
			c.setCourseId(co.getUniqueId());
			c.setCourseName(co.getCourseName());
			c.setTitle(co.getTitle() == null ? "" : co.getTitle().trim());
			c.setControlling(Boolean.TRUE.equals(co.getIsControl()));
			c.setReservation(co.getReservation());
			c.setCanDelete(context.hasPermission(co, Right.CourseOfferingDeleteFromCrossList));
			r.addCourse(c);
		}
	}
}
