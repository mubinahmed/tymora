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
package org.unitime.timetable.server.instructor;

import java.util.HashSet;
import java.util.Set;

import org.unitime.timetable.defaults.UserProperty;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.InstructorDetailInterface.InstructorDetailRequest;
import org.unitime.timetable.gwt.shared.InstructorDetailInterface.InstructorDetailResponse;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.NameFormat;

/**
 * Read-only detail of one DepartmentalInstructor (legacy Struts
 * instructorDetail.action). Loads the instructor by uniqueId, gates access with
 * Right.InstructorDetail (the same permission the legacy action enforced on the
 * "DepartmentalInstructor" target), then projects the display fields: formatted
 * name (using the current user's name-format preference), department, position,
 * contact fields, note and the count of assigned classes.
 *
 * Solver-dependent columns of the legacy screen (per-class assigned time / room
 * / conflicts, and instructor unavailability from RoomAvailability) require an
 * in-memory solver proxy / external service and are intentionally omitted here.
 * Additive: introduces no changes to existing behavior.
 *
 * @author Angular migration
 */
@GwtRpcImplements(InstructorDetailRequest.class)
public class InstructorDetailBackend implements GwtRpcImplementation<InstructorDetailRequest, InstructorDetailResponse> {

	@Override
	public InstructorDetailResponse execute(InstructorDetailRequest request, SessionContext context) {
		Long instructorId = request.getInstructorId();
		if (instructorId == null)
			throw new GwtRpcException("No instructor specified.");

		context.checkPermission(instructorId, "DepartmentalInstructor", Right.InstructorDetail);

		DepartmentalInstructor inst = DepartmentalInstructorDAO.getInstance().get(instructorId);
		if (inst == null)
			throw new GwtRpcException("Instructor not found.");

		InstructorDetailResponse response = new InstructorDetailResponse();
		response.setId(inst.getUniqueId());

		try {
			NameFormat nameFormat = NameFormat.fromReference(context.getUser().getProperty(UserProperty.NameFormat));
			response.setName(nameFormat.format(inst));
		} catch (Throwable t) {
			response.setName(inst.getName(DepartmentalInstructor.sNameFormatLastFirstMiddle));
		}

		try { response.setEmail(inst.getEmail()); } catch (Throwable t) {}
		try { response.setExternalId(inst.getExternalUniqueId()); } catch (Throwable t) {}
		try { response.setAccountName(inst.getCareerAcct() == null ? null : inst.getCareerAcct().trim()); } catch (Throwable t) {}
		try { response.setAcademicTitle(inst.getAcademicTitle()); } catch (Throwable t) {}
		try {
			if (inst.getPositionType() != null)
				response.setPosition(inst.getPositionType().getLabel() == null ? null : inst.getPositionType().getLabel().trim());
		} catch (Throwable t) {}
		try {
			if (inst.getDepartment() != null) {
				response.setDeptCode(inst.getDepartment().getDeptCode());
				response.setDeptName(inst.getDepartment().getLabel());
			}
		} catch (Throwable t) {}
		try { response.setNote(inst.getNote() == null ? null : inst.getNote().trim()); } catch (Throwable t) {}

		try {
			// Assigned classes across all DepartmentalInstructor rows that share the
			// same external id in this session (matches the legacy detail screen).
			Set<Object> allClasses = new HashSet<Object>();
			for (DepartmentalInstructor di : DepartmentalInstructor.getAllForInstructor(inst, inst.getDepartment().getSession().getUniqueId())) {
				if (di.getClasses() != null)
					allClasses.addAll(di.getClasses());
			}
			response.setAssignedClasses(allClasses.size());
		} catch (Throwable t) {
			try {
				response.setAssignedClasses(inst.getClasses() == null ? 0 : inst.getClasses().size());
			} catch (Throwable t2) {
				response.setAssignedClasses(0);
			}
		}

		return response;
	}
}
