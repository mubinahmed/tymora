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
package org.unitime.timetable.model;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import org.unitime.timetable.model.base.BaseStaffAbsence;

/**
 * A staff member's declared absence for a date range in a given academic session.
 * Self-service submissions start as {@link Status#REQUESTED} and become
 * {@link Status#APPROVED} once an administrator confirms; the relief-generation
 * engine only considers approved absences.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "staff_absence")
public class StaffAbsence extends BaseStaffAbsence {
	private static final long serialVersionUID = 1L;

	public static enum Status {
		REQUESTED,	// 0 - submitted (e.g. by the teacher), awaiting approval
		APPROVED,	// 1 - confirmed; eligible for relief generation
		REJECTED,	// 2 - declined
		;
		public int value() { return ordinal(); }
		public static Status fromValue(Integer v) {
			return (v == null || v < 0 || v >= values().length) ? REQUESTED : values()[v];
		}
	}

	public StaffAbsence() {
		super();
	}

	public StaffAbsence(Long uniqueId) {
		super(uniqueId);
	}

	public Status status() { return Status.fromValue(getStatus()); }
	public boolean isApproved() { return status() == Status.APPROVED; }

	/** True when this absence covers the given calendar date (inclusive of both ends). */
	public boolean coversDate(Date date) {
		if (date == null || getStartDate() == null || getEndDate() == null) return false;
		return !date.before(getStartDate()) && !date.after(getEndDate());
	}
}
