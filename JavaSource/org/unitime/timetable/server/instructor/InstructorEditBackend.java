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

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.IdName;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorEditRequest;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorEditResponse;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.PositionType;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Edit Instructor page (legacy instructorInfoEdit.action).
 * Loads one instructor's identity fields plus the position-type options. See
 * {@link org.unitime.timetable.gwt.shared.InstructorEditInterface}; gated by
 * {@link Right#InstructorEdit}. Companions: {@code InstructorAddInitBackend} (add form),
 * {@code InstructorSaveBackend} (create/update), {@code InstructorDeleteBackend}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(InstructorEditRequest.class)
public class InstructorEditBackend implements GwtRpcImplementation<InstructorEditRequest, InstructorEditResponse> {

	@Override
	public InstructorEditResponse execute(InstructorEditRequest request, SessionContext context) {
		Long instructorId = request.getInstructorId();
		if (instructorId == null)
			throw new GwtRpcException("No instructor was specified.");

		context.checkPermission(instructorId, "DepartmentalInstructor", Right.InstructorEdit);

		DepartmentalInstructor inst = DepartmentalInstructorDAO.getInstance().get(instructorId);
		if (inst == null)
			throw new GwtRpcException("Instructor " + instructorId + " was not found.");

		InstructorEditResponse response = new InstructorEditResponse();
		fillInstructor(response, inst);
		fillPositionTypes(response);
		return response;
	}

	/** Copy the instructor's editable identity fields into the response. */
	static void fillInstructor(InstructorEditResponse r, DepartmentalInstructor inst) {
		r.setInstructorId(inst.getUniqueId());
		r.setFname(trim(inst.getFirstName()));
		r.setMname(trim(inst.getMiddleName()));
		r.setLname(trim(inst.getLastName()));
		r.setTitle(trim(inst.getAcademicTitle()));
		r.setExternalId(trim(inst.getExternalUniqueId()));
		r.setCareerAcct(trim(inst.getCareerAcct()));
		r.setEmail(trim(inst.getEmail()));
		r.setNote(trim(inst.getNote()));
		r.setIgnoreTooFar(Boolean.TRUE.equals(inst.isIgnoreToFar()));
		if (inst.getPositionType() != null)
			r.setPositionTypeId(inst.getPositionType().getUniqueId());
		if (inst.getDepartment() != null) {
			r.setDepartmentId(inst.getDepartment().getUniqueId());
			r.setDeptName(trim(inst.getDepartment().getName()));
			r.setDeptCode(trim(inst.getDepartment().getDeptCode()));
		}
	}

	/** Add the session's position-type options to the response. */
	static void fillPositionTypes(InstructorEditResponse r) {
		try {
			for (Object o : PositionType.findAll()) {
				PositionType pt = (PositionType) o;
				r.addPositionType(new IdName(pt.getUniqueId(), pt.getLabel()));
			}
		} catch (Exception e) {}
	}

	static String trim(String s) { return s == null ? "" : s.trim(); }
}
