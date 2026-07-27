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
package org.unitime.timetable.model.dao;

import java.util.Date;
import java.util.List;

import org.unitime.timetable.model.StaffAbsence;

public class StaffAbsenceDAO extends _RootDAO<StaffAbsence,Long> {
	private static StaffAbsenceDAO sInstance;

	public StaffAbsenceDAO() {}

	public static StaffAbsenceDAO getInstance() {
		if (sInstance == null) sInstance = new StaffAbsenceDAO();
		return sInstance;
	}

	public Class<StaffAbsence> getReferenceClass() {
		return StaffAbsence.class;
	}

	/** All absences in a session overlapping the [from, to] date range (inclusive). */
	public List<StaffAbsence> findBySessionAndRange(org.hibernate.Session hibSession, Long sessionId, Date from, Date to) {
		return hibSession.createQuery(
				"from StaffAbsence a where a.session.uniqueId = :sessionId " +
				"and a.startDate <= :to and a.endDate >= :from order by a.startDate, a.name", StaffAbsence.class)
				.setParameter("sessionId", sessionId)
				.setParameter("from", from)
				.setParameter("to", to)
				.list();
	}

	/** Approved absences in a session that cover the given date. */
	public List<StaffAbsence> findApprovedOnDate(org.hibernate.Session hibSession, Long sessionId, Date date) {
		return hibSession.createQuery(
				"from StaffAbsence a where a.session.uniqueId = :sessionId and a.status = 1 " +
				"and a.startDate <= :date and a.endDate >= :date order by a.name", StaffAbsence.class)
				.setParameter("sessionId", sessionId)
				.setParameter("date", date)
				.list();
	}
}
