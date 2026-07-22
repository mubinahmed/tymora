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

import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.IdName;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorAddInitRequest;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorEditResponse;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Add Instructor page (legacy instructorAdd.action).
 * Returns the departments the user may add instructors to plus the position-type
 * options. See {@link org.unitime.timetable.gwt.shared.InstructorEditInterface}.
 * Gated per-department by {@link Right#InstructorAdd}.
 *
 * @author Angular migration
 */
@GwtRpcImplements(InstructorAddInitRequest.class)
public class InstructorAddInitBackend implements GwtRpcImplementation<InstructorAddInitRequest, InstructorEditResponse> {

	@Override
	public InstructorEditResponse execute(InstructorAddInitRequest request, SessionContext context) {
		InstructorEditResponse response = new InstructorEditResponse();
		for (Department d : Department.getUserDepartments(context.getUser())) {
			if (context.hasPermission(d, Right.InstructorAdd))
				response.addDepartment(new IdName(d.getUniqueId(), d.getDeptCode() + " - " + d.getName()));
		}
		InstructorEditBackend.fillPositionTypes(response);
		return response;
	}
}
