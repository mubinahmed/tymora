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

import java.util.Iterator;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorDeleteRequest;
import org.unitime.timetable.gwt.shared.InstructorEditInterface.InstructorEditResponse;
import org.unitime.timetable.model.Assignment;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ClassInstructor;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Delete backend for the migrated Edit Instructor page (legacy instructorInfoEdit.action
 * delete). Mirrors {@code InstructorInfoEditAction.doDelete()}: detaches the instructor
 * from its classes, exams and assignments, then removes it. Gated by
 * {@link Right#InstructorDelete}, transactional, and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(InstructorDeleteRequest.class)
public class InstructorDeleteBackend implements GwtRpcImplementation<InstructorDeleteRequest, InstructorEditResponse> {

	@Override
	public InstructorEditResponse execute(InstructorDeleteRequest request, SessionContext context) {
		Long instructorId = request.getInstructorId();
		if (instructorId == null)
			throw new GwtRpcException("No instructor was specified.");

		context.checkPermission(instructorId, "DepartmentalInstructor", Right.InstructorDelete);

		DepartmentalInstructorDAO idao = DepartmentalInstructorDAO.getInstance();
		org.hibernate.Session hibSession = idao.getSession();
		DepartmentalInstructor inst = idao.get(instructorId);
		if (inst == null)
			throw new GwtRpcException("Instructor " + instructorId + " was not found.");

		Long departmentId = inst.getDepartment() == null ? null : inst.getDepartment().getUniqueId();

		Transaction tx = hibSession.beginTransaction();
		try {
			ChangeLog.addChange(
					hibSession, context, inst,
					ChangeLog.Source.INSTRUCTOR_EDIT,
					ChangeLog.Operation.DELETE,
					null, inst.getDepartment());

			for (Iterator<?> i = inst.getClasses().iterator(); i.hasNext(); ) {
				ClassInstructor ci = (ClassInstructor) i.next();
				ci.getClassInstructing().getClassInstructors().remove(ci);
				hibSession.remove(ci);
			}
			for (Iterator<?> i = inst.getExams().iterator(); i.hasNext(); ) {
				Exam exam = (Exam) i.next();
				exam.getInstructors().remove(inst);
				hibSession.merge(exam);
			}
			for (Iterator<?> i = inst.getAssignments().iterator(); i.hasNext(); ) {
				Assignment a = (Assignment) i.next();
				a.getInstructors().remove(inst);
				hibSession.merge(a);
			}
			Department d = inst.getDepartment();
			if (d != null) d.getInstructors().remove(inst);

			hibSession.remove(inst);
			tx.commit();
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to delete instructor: " + e.getMessage(), e);
		}

		InstructorEditResponse response = new InstructorEditResponse();
		response.setDeleted(true);
		response.setDepartmentId(departmentId);
		return response;
	}
}
