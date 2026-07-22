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
import org.unitime.timetable.gwt.shared.ClassEditInterface.ClassEditResponse;
import org.unitime.timetable.gwt.shared.ClassEditInterface.ClassEditUpdateRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.dao.Class_DAO;
import org.unitime.timetable.model.dao.DatePatternDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Class Edit page (legacy classEdit.action). Persists
 * the class-data subset that {@code ClassEditAction.doUpdate()} saves for the class
 * row itself (capacity, rooms, date pattern, scheduling flags, notes); instructor
 * assignment and preferences are handled by their own screens. Gated by
 * {@link Right#ClassEdit}, transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ClassEditUpdateRequest.class)
public class ClassEditUpdateBackend implements GwtRpcImplementation<ClassEditUpdateRequest, ClassEditResponse> {

	@Override
	public ClassEditResponse execute(ClassEditUpdateRequest request, SessionContext context) {
		Long classId = request.getClassId();
		if (classId == null)
			throw new GwtRpcException("No class was specified.");

		context.checkPermission(classId, "Class_", Right.ClassEdit);

		Class_DAO dao = Class_DAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		Class_ c = dao.get(classId);
		if (c == null)
			throw new GwtRpcException("Class " + classId + " was not found.");

		Transaction tx = hibSession.beginTransaction();
		try {
			c.setExpectedCapacity(request.getExpectedCapacity());
			c.setMaxExpectedCapacity(request.getMaxExpectedCapacity());
			c.setRoomRatio(request.getRoomRatio());

			int nbrRooms = request.getNbrRooms() == null ? 0 : request.getNbrRooms();
			c.setNbrRooms(nbrRooms);
			c.setRoomsSplitAttendance(nbrRooms > 1 && request.isSplitAttendance());

			Long dpId = request.getDatePatternId();
			if (dpId == null || dpId.intValue() < 0)
				c.setDatePattern(null);
			else
				c.setDatePattern(DatePatternDAO.getInstance().get(dpId));

			c.setNotes(request.getNotes());
			c.setSchedulePrintNote(request.getSchedulePrintNote());
			c.setEnabledForStudentScheduling(Boolean.valueOf(request.isEnabledForStudentScheduling()));
			c.setDisplayInstructor(Boolean.valueOf(request.isDisplayInstructor()));

			ChangeLog.addChange(
					hibSession,
					context,
					c,
					ChangeLog.Source.CLASS_EDIT,
					ChangeLog.Operation.UPDATE,
					c.getSchedulingSubpart().getInstrOfferingConfig().getControllingCourseOffering().getSubjectArea(),
					c.getManagingDept());

			hibSession.merge(c);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save class: " + e.getMessage(), e);
		}

		ClassEditResponse response = new ClassEditResponse();
		ClassEditBackend.fill(response, c, context);
		response.setSaved(true);
		return response;
	}
}
