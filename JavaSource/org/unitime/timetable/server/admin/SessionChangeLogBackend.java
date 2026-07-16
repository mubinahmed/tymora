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

import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.SessionChangeLogInterface.SessionChangeLogRequest;
import org.unitime.timetable.gwt.shared.SimpleListInterface.Row;
import org.unitime.timetable.gwt.shared.SimpleListInterface.SimpleListResponse;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read-only session-wide "Last Changes" (Change Log) report — the Angular
 * replacement for the legacy Struts {@code lastChanges.action}
 * (LastChangesAction). Lists the most recent {@link ChangeLog} entries for the
 * current academic session, projected to the generic {@link SimpleListResponse}
 * table shape (Date, Department, Subject, Manager, Page, Object, Operation).
 *
 * Additive: introduces no changes to existing behavior. Gated by
 * {@link Right#LastChanges} exactly as the legacy action did.
 *
 * @author Angular migration
 */
@GwtRpcImplements(SessionChangeLogRequest.class)
public class SessionChangeLogBackend implements GwtRpcImplementation<SessionChangeLogRequest, SimpleListResponse> {

	private static final int DEFAULT_LIMIT = 100;
	private static final int MAX_LIMIT = 1000;

	@Override
	public SimpleListResponse execute(SessionChangeLogRequest request, SessionContext context) {
		context.checkPermission(Right.LastChanges);

		Long sessionId = (context.getUser() == null ? null : context.getUser().getCurrentAcademicSessionId());
		if (sessionId == null)
			throw new GwtRpcException("No academic session is selected.");

		int n = DEFAULT_LIMIT;
		if (request.getLimit() != null && request.getLimit().intValue() > 0)
			n = request.getLimit().intValue();
		if (n > MAX_LIMIT) n = MAX_LIMIT;

		SimpleListResponse response = new SimpleListResponse();
		response.setTitle("Last Changes");
		for (String c : new String[] { "Date", "Department", "Subject", "Manager", "Page", "Object", "Operation" })
			response.addColumn(c);

		List<ChangeLog> changes = ChangeLog.findLastNChanges(sessionId, null, null, null, n);
		if (changes != null) {
			for (ChangeLog cl : changes) {
				Row r = response.addRow(cl.getUniqueId());
				String date = "";
				try { date = (cl.getTimeStamp() == null ? "" : ChangeLog.sDF.format(cl.getTimeStamp())); } catch (Exception e) {}
				r.add(date);
				String dept = "";
				try { dept = (cl.getDepartment() == null ? "" : cl.getDepartment().getShortLabel()); } catch (Exception e) {}
				r.add(dept);
				String subj = "";
				try { subj = (cl.getSubjectArea() == null ? "" : cl.getSubjectArea().getSubjectAreaAbbreviation()); } catch (Exception e) {}
				r.add(subj);
				String mgr = "";
				try {
					if (cl.getManager() != null && cl.getManager().getShortName() != null)
						mgr = cl.getManager().getShortName().replaceAll("&nbsp;", " ");
				} catch (Exception e) {}
				r.add(mgr);
				String page = "";
				try { page = cl.getSourceTitle(); } catch (Exception e) {}
				r.add(page);
				String object = "";
				try { object = cl.getObjectTitle(); } catch (Exception e) {}
				r.add(object);
				String operation = "";
				try { operation = cl.getOperationTitle(); } catch (Exception e) {}
				r.add(operation);
			}
		}

		return response;
	}
}
