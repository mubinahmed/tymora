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

import org.unitime.timetable.model.ReliefAssignment;

public class ReliefAssignmentDAO extends _RootDAO<ReliefAssignment,Long> {
	private static ReliefAssignmentDAO sInstance;

	public ReliefAssignmentDAO() {}

	public static ReliefAssignmentDAO getInstance() {
		if (sInstance == null) sInstance = new ReliefAssignmentDAO();
		return sInstance;
	}

	public Class<ReliefAssignment> getReferenceClass() {
		return ReliefAssignment.class;
	}

	/** All relief assignments in a session on a given date, ordered by period. */
	public List<ReliefAssignment> findBySessionAndDate(org.hibernate.Session hibSession, Long sessionId, Date date) {
		return hibSession.createQuery(
				"from ReliefAssignment r where r.session.uniqueId = :sessionId and r.meetingDate = :date " +
				"order by r.startPeriod, r.uniqueId", ReliefAssignment.class)
				.setParameter("sessionId", sessionId)
				.setParameter("date", date)
				.list();
	}

	/** All relief assignments in a session within the [from, to] date range (inclusive). */
	public List<ReliefAssignment> findBySessionAndRange(org.hibernate.Session hibSession, Long sessionId, Date from, Date to) {
		return hibSession.createQuery(
				"from ReliefAssignment r where r.session.uniqueId = :sessionId " +
				"and r.meetingDate >= :from and r.meetingDate <= :to order by r.meetingDate, r.startPeriod", ReliefAssignment.class)
				.setParameter("sessionId", sessionId)
				.setParameter("from", from)
				.setParameter("to", to)
				.list();
	}
}
