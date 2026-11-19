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

import org.unitime.timetable.model.HandoverNote;

public class HandoverNoteDAO extends _RootDAO<HandoverNote,Long> {
	private static HandoverNoteDAO sInstance;

	public HandoverNoteDAO() {}

	public static HandoverNoteDAO getInstance() {
		if (sInstance == null) sInstance = new HandoverNoteDAO();
		return sInstance;
	}

	public Class<HandoverNote> getReferenceClass() {
		return HandoverNote.class;
	}

	/** All handover notes for one absence, oldest first. */
	public List<HandoverNote> findByAbsence(org.hibernate.Session hibSession, Long absenceId) {
		return hibSession.createQuery(
				"from HandoverNote n where n.absence.uniqueId = :absenceId order by n.timeStamp, n.uniqueId", HandoverNote.class)
				.setParameter("absenceId", absenceId)
				.list();
	}
}
