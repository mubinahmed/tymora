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
import java.util.List;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.ExactTimeEditInterface.ExactTimeEditRequest;
import org.unitime.timetable.gwt.shared.ExactTimeEditInterface.ExactTimeEditResponse;
import org.unitime.timetable.gwt.shared.ExactTimeEditInterface.ExactTimeMinsRecord;
import org.unitime.timetable.gwt.shared.ExactTimeEditInterface.Operation;
import org.unitime.timetable.model.ExactTimeMins;
import org.unitime.timetable.model.dao.ExactTimeMinsDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * LOAD / SAVE backend for the Exact Time Pattern editor (legacy Struts
 * exactTimeEdit page, ExactTimeEditAction). LOAD returns all ExactTimeMins rows
 * sorted by their minutes-per-meeting range; SAVE merges the two editable fields
 * (number of time slots per meeting, break time) back onto each existing row.
 *
 * The legacy page never added, removed, or edited the minutes range, so this
 * backend does the same: it re-loads each row by id and sets only nrSlots and
 * breakTime (merge-on-update), leaving the range and any other state untouched.
 * ExactTimeMins is a small global (non session-scoped) reference table; every
 * operation is gated by Right.ExactTimes, matching the legacy action, which
 * recorded no ChangeLog entry, so none is written here either.
 *
 * @author Angular migration
 */
@GwtRpcImplements(ExactTimeEditRequest.class)
public class ExactTimeEditBackend implements GwtRpcImplementation<ExactTimeEditRequest, ExactTimeEditResponse> {

	@Override
	public ExactTimeEditResponse execute(ExactTimeEditRequest request, SessionContext context) {
		context.checkPermission(Right.ExactTimes);

		Operation op = (request.getOperation() == null ? Operation.LOAD : request.getOperation());
		if (op == Operation.SAVE)
			save(request.getRecords(), context);

		return load(context);
	}

	protected ExactTimeEditResponse load(SessionContext context) {
		ExactTimeEditResponse response = new ExactTimeEditResponse();
		response.setEditable(context.hasPermission(Right.ExactTimes));

		List<ExactTimeMins> all = new ArrayList<ExactTimeMins>(ExactTimeMinsDAO.getInstance().findAll());
		Collections.sort(all);
		for (ExactTimeMins ex : all) {
			ExactTimeMinsRecord r = new ExactTimeMinsRecord();
			try {
				r.setId(ex.getUniqueId());
				r.setMinsPerMtgMin(ex.getMinsPerMtgMin() == null ? 0 : ex.getMinsPerMtgMin().intValue());
				r.setMinsPerMtgMax(ex.getMinsPerMtgMax() == null ? 0 : ex.getMinsPerMtgMax().intValue());
				r.setNrSlots(ex.getNrSlots() == null ? 0 : ex.getNrSlots().intValue());
				r.setBreakTime(ex.getBreakTime() == null ? 0 : ex.getBreakTime().intValue());
			} catch (Exception e) {
				// lazy-init / projection safety: skip an unreadable row rather than fail the whole load
				continue;
			}
			response.addRecord(r);
		}
		return response;
	}

	protected void save(List<ExactTimeMinsRecord> records, SessionContext context) {
		if (records == null || records.isEmpty())
			return;

		Transaction tx = null;
		org.hibernate.Session hibSession = ExactTimeMinsDAO.getInstance().getSession();
		try {
			tx = hibSession.beginTransaction();

			for (ExactTimeMinsRecord r : records) {
				if (r == null || r.getId() == null)
					continue;
				if (r.getNrSlots() < 0)
					throw new GwtRpcException("Number of time slots must not be negative.");
				if (r.getBreakTime() < 0)
					throw new GwtRpcException("Break time must not be negative.");

				ExactTimeMins ex = ExactTimeMinsDAO.getInstance().get(r.getId(), hibSession);
				if (ex == null)
					continue;

				// Merge-on-update: only the two fields the legacy page rendered as editable.
				ex.setNrSlots(Integer.valueOf(r.getNrSlots()));
				ex.setBreakTime(Integer.valueOf(r.getBreakTime()));
				hibSession.merge(ex);
			}

			tx.commit(); tx = null;
		} catch (GwtRpcException e) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw e;
		} catch (Throwable t) {
			if (tx != null && tx.isActive()) tx.rollback();
			throw new GwtRpcException(t.getMessage(), t);
		}
	}
}
