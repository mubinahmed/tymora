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

import java.util.List;

import org.unitime.timetable.model.AssignmentChange;

public class AssignmentChangeDAO extends _RootDAO<AssignmentChange,Long> {
	private static AssignmentChangeDAO sInstance;

	public AssignmentChangeDAO() {}

	public static AssignmentChangeDAO getInstance() {
		if (sInstance == null) sInstance = new AssignmentChangeDAO();
		return sInstance;
	}

	public Class<AssignmentChange> getReferenceClass() {
		return AssignmentChange.class;
	}

	/** Changes of one academic session, newest first, optionally limited to one status and/or requester. */
	public List<AssignmentChange> find(org.hibernate.Session hibSession, Long sessionId, String status, String requestedBy, Long subjectId, int limit) {
		String q = "from AssignmentChange c where c.session.uniqueId = :sessionId" +
				(status == null ? "" : " and c.status = :status") +
				(requestedBy == null ? "" : " and c.requestedBy = :requestedBy") +
				(subjectId == null ? "" : " and c.subjectId = :subjectId") +
				" order by c.requestedTs desc, c.uniqueId desc";
		org.hibernate.query.Query<AssignmentChange> query = hibSession.createQuery(q, AssignmentChange.class).setParameter("sessionId", sessionId);
		if (status != null) query.setParameter("status", status);
		if (requestedBy != null) query.setParameter("requestedBy", requestedBy);
		if (subjectId != null) query.setParameter("subjectId", subjectId);
		if (limit > 0) query.setMaxResults(limit);
		return query.list();
	}

	public long count(org.hibernate.Session hibSession, Long sessionId, String status, String requestedBy, Boolean requesterSeen) {
		String q = "select count(c) from AssignmentChange c where c.session.uniqueId = :sessionId" +
				(status == null ? "" : " and c.status = :status") +
				(requestedBy == null ? "" : " and c.requestedBy = :requestedBy") +
				(requesterSeen == null ? "" : " and c.requesterSeen = :seen");
		org.hibernate.query.Query<Long> query = hibSession.createQuery(q, Long.class).setParameter("sessionId", sessionId);
		if (status != null) query.setParameter("status", status);
		if (requestedBy != null) query.setParameter("requestedBy", requestedBy);
		if (requesterSeen != null) query.setParameter("seen", requesterSeen);
		Long ret = query.uniqueResult();
		return ret == null ? 0 : ret;
	}
}
