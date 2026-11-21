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
import org.unitime.timetable.model.AttendanceRecord;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the attendance_record table of dbupdate.xml version 279 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseAttendanceRecord implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iAttendanceSessionId;
	private Long iStudentId;
	private String iStatus;
	private String iSource;
	private Date iMarkedTs;
	private String iMarkedBy;
	private String iDeviceId;
	private String iNote;

	public BaseAttendanceRecord() {
	}

	public BaseAttendanceRecord(Long uniqueId) {
		setUniqueId(uniqueId);
	}

	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "attendance_session_id", nullable = false)
	public Long getAttendanceSessionId() { return iAttendanceSessionId; }
	public void setAttendanceSessionId(Long attendanceSessionId) { iAttendanceSessionId = attendanceSessionId; }

	@Column(name = "student_id", nullable = false)
	public Long getStudentId() { return iStudentId; }
	public void setStudentId(Long studentId) { iStudentId = studentId; }

	@Column(name = "status", nullable = false, length = 20)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "source", nullable = false, length = 20)
	public String getSource() { return iSource; }
	public void setSource(String source) { iSource = source; }

	@Column(name = "marked_ts", nullable = false)
	public Date getMarkedTs() { return iMarkedTs; }
	public void setMarkedTs(Date markedTs) { iMarkedTs = markedTs; }

	@Column(name = "marked_by", nullable = true, length = 100)
	public String getMarkedBy() { return iMarkedBy; }
	public void setMarkedBy(String markedBy) { iMarkedBy = markedBy; }

	@Column(name = "device_id", nullable = true, length = 64)
	public String getDeviceId() { return iDeviceId; }
	public void setDeviceId(String deviceId) { iDeviceId = deviceId; }

	@Column(name = "note", nullable = true, length = 500)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AttendanceRecord)) return false;
		if (getUniqueId() == null || ((AttendanceRecord)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AttendanceRecord)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AttendanceRecord[" + getUniqueId() + "]";
	}
}
