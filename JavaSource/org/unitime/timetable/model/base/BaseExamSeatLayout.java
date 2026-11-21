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
package org.unitime.timetable.model.base;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;
import java.util.Date;

import org.unitime.commons.annotations.UniqueIdGenerator;
import org.unitime.timetable.model.ExamSeatLayout;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the exam_seat_layout table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseExamSeatLayout implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iLocationId;
	private Integer iNrRows;
	private Integer iNrColumns;
	private String iBlockedSeats;
	private String iAccessibleSeats;
	private Integer iFrontRows;

	public BaseExamSeatLayout() {
	}

	public BaseExamSeatLayout(Long uniqueId) {
		setUniqueId(uniqueId);
	}

	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "session_id", nullable = false)
	public Long getSessionId() { return iSessionId; }
	public void setSessionId(Long sessionId) { iSessionId = sessionId; }

	@Column(name = "location_id", nullable = false)
	public Long getLocationId() { return iLocationId; }
	public void setLocationId(Long locationId) { iLocationId = locationId; }

	@Column(name = "nr_rows", nullable = false)
	public Integer getNrRows() { return iNrRows; }
	public void setNrRows(Integer nrRows) { iNrRows = nrRows; }

	@Column(name = "nr_columns", nullable = false)
	public Integer getNrColumns() { return iNrColumns; }
	public void setNrColumns(Integer nrColumns) { iNrColumns = nrColumns; }

	@Column(name = "blocked_seats", nullable = true, length = 2000)
	public String getBlockedSeats() { return iBlockedSeats; }
	public void setBlockedSeats(String blockedSeats) { iBlockedSeats = blockedSeats; }

	@Column(name = "accessible_seats", nullable = true, length = 2000)
	public String getAccessibleSeats() { return iAccessibleSeats; }
	public void setAccessibleSeats(String accessibleSeats) { iAccessibleSeats = accessibleSeats; }

	@Column(name = "front_rows", nullable = true)
	public Integer getFrontRows() { return iFrontRows; }
	public void setFrontRows(Integer frontRows) { iFrontRows = frontRows; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ExamSeatLayout)) return false;
		if (getUniqueId() == null || ((ExamSeatLayout)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((ExamSeatLayout)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "ExamSeatLayout[" + getUniqueId() + "]";
	}
}
