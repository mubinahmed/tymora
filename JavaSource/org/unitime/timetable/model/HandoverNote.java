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

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import org.unitime.timetable.model.base.BaseHandoverNote;

/**
 * A piece of relief handover shared by the absent teacher (or an administrator on
 * their behalf) for use by whoever covers their lessons: free-form instructions,
 * an optional single link, and/or an optional single file attachment. Several
 * notes may be attached to one {@link StaffAbsence} — teachers add one note per
 * item they want to share rather than editing a single blob.
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Table(name = "handover_note")
public class HandoverNote extends BaseHandoverNote {
	private static final long serialVersionUID = 1L;

	public HandoverNote() {
		super();
	}

	public HandoverNote(Long uniqueId) {
		super(uniqueId);
	}

	public boolean hasAttachment() { return getAttachedName() != null && !getAttachedName().isEmpty(); }
	public boolean hasLink() { return getLinkUrl() != null && !getLinkUrl().isEmpty(); }
}
