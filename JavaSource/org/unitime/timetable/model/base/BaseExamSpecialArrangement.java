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
import org.unitime.timetable.model.ExamSpecialArrangement;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the exam_special_arr table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseExamSpecialArrangement implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iStudentId;
	private Boolean iSeparateRoom;
	private Long iLocationId;
	private Boolean iAccessibleSeat;
	private Boolean iFrontSeat;
	private Integer iExtraTimePct;
	private String iNote;
	private String iApprovedBy;

	public BaseExamSpecialArrangement() {
	}

	public BaseExamSpecialArrangement(Long uniqueId) {
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

	@Column(name = "student_id", nullable = false)
	public Long getStudentId() { return iStudentId; }
	public void setStudentId(Long studentId) { iStudentId = studentId; }

	@Column(name = "separate_room", nullable = false)
	public Boolean getSeparateRoom() { return iSeparateRoom; }
	public void setSeparateRoom(Boolean separateRoom) { iSeparateRoom = separateRoom; }

	@Column(name = "location_id", nullable = true)
	public Long getLocationId() { return iLocationId; }
	public void setLocationId(Long locationId) { iLocationId = locationId; }

	@Column(name = "accessible_seat", nullable = false)
	public Boolean getAccessibleSeat() { return iAccessibleSeat; }
	public void setAccessibleSeat(Boolean accessibleSeat) { iAccessibleSeat = accessibleSeat; }

	@Column(name = "front_seat", nullable = false)
	public Boolean getFrontSeat() { return iFrontSeat; }
	public void setFrontSeat(Boolean frontSeat) { iFrontSeat = frontSeat; }

	@Column(name = "extra_time_pct", nullable = true)
	public Integer getExtraTimePct() { return iExtraTimePct; }
	public void setExtraTimePct(Integer extraTimePct) { iExtraTimePct = extraTimePct; }

	@Column(name = "note", nullable = true, length = 500)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Column(name = "approved_by", nullable = true, length = 100)
	public String getApprovedBy() { return iApprovedBy; }
	public void setApprovedBy(String approvedBy) { iApprovedBy = approvedBy; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ExamSpecialArrangement)) return false;
		if (getUniqueId() == null || ((ExamSpecialArrangement)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((ExamSpecialArrangement)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "ExamSpecialArrangement[" + getUniqueId() + "]";
	}
}
