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

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.unitime.timetable.model.base.BaseAssignmentChange;

/**
 * A class or examination assignment change made from the Angular assignment dialogs, with the
 * reason given and the approval decision. Pending changes are applied only when an approver
 * approves them; changes by approvers are applied directly and recorded as self-approved.
 * The table doubles as the audit trail of these changes (see dbupdate.xml version 277).
 */
@Entity
@Table(name = "assignment_change")
public class AssignmentChange extends BaseAssignmentChange {
	private static final long serialVersionUID = 1L;

	public static enum Type { CLASS, EXAM }
	/** SOLVER: change to an in-memory solver solution (solver_key names it); DATABASE: written to the database */
	public static enum Mode { SOLVER, DATABASE }
	public static enum Status { PENDING, APPLIED, REJECTED, CANCELLED, FAILED }

	public AssignmentChange() {
		super();
	}

	public AssignmentChange(Long uniqueId) {
		super(uniqueId);
	}

	@Transient
	public Type getTypeEnum() { return getChangeType() == null ? null : Type.valueOf(getChangeType()); }
	@Transient
	public Mode getModeEnum() { return getChangeMode() == null ? null : Mode.valueOf(getChangeMode()); }
	@Transient
	public Status getStatusEnum() { return getStatus() == null ? null : Status.valueOf(getStatus()); }
	@Transient
	public boolean isPending() { return Status.PENDING.name().equals(getStatus()); }
}
