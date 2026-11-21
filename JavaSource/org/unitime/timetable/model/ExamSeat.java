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

import org.unitime.timetable.model.base.BaseExamSeat;

/**
 * A student's seat for an examination: period, venue and seat label.
 * Part of examination seat allocation and invigilation (dbupdate.xml version 278).
 */
@Entity
@Table(name = "exam_seat")
public class ExamSeat extends BaseExamSeat {
	private static final long serialVersionUID = 1L;

	public ExamSeat() {
		super();
	}

	public ExamSeat(Long uniqueId) {
		super(uniqueId);
	}
}
