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
import org.unitime.timetable.model.AttendanceExcuse;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the attendance_excuse table of dbupdate.xml version 279 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseAttendanceExcuse implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iStudentId;
	private String iExcuseType;
	private Date iFromDate;
	private Date iToDate;
	private String iNote;
	private String iFileName;
	private String iFileType;
	private byte[] iFileContent;
	private String iStatus;
	private Date iSubmittedTs;
	private String iDecidedBy;
	private Date iDecidedTs;
	private String iDecisionNote;

	public BaseAttendanceExcuse() {
	}

	public BaseAttendanceExcuse(Long uniqueId) {
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

	@Column(name = "excuse_type", nullable = false, length = 20)
	public String getExcuseType() { return iExcuseType; }
	public void setExcuseType(String excuseType) { iExcuseType = excuseType; }

	@Column(name = "from_date", nullable = false)
	public Date getFromDate() { return iFromDate; }
	public void setFromDate(Date fromDate) { iFromDate = fromDate; }

	@Column(name = "to_date", nullable = false)
	public Date getToDate() { return iToDate; }
	public void setToDate(Date toDate) { iToDate = toDate; }

	@Column(name = "note", nullable = true, length = 1000)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Column(name = "file_name", nullable = true, length = 260)
	public String getFileName() { return iFileName; }
	public void setFileName(String fileName) { iFileName = fileName; }

	@Column(name = "file_type", nullable = true, length = 100)
	public String getFileType() { return iFileType; }
	public void setFileType(String fileType) { iFileType = fileType; }

	@jakarta.persistence.Lob

	@Column(name = "file_content", nullable = true)
	public byte[] getFileContent() { return iFileContent; }
	public void setFileContent(byte[] fileContent) { iFileContent = fileContent; }

	@Column(name = "status", nullable = false, length = 20)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "submitted_ts", nullable = false)
	public Date getSubmittedTs() { return iSubmittedTs; }
	public void setSubmittedTs(Date submittedTs) { iSubmittedTs = submittedTs; }

	@Column(name = "decided_by", nullable = true, length = 100)
	public String getDecidedBy() { return iDecidedBy; }
	public void setDecidedBy(String decidedBy) { iDecidedBy = decidedBy; }

	@Column(name = "decided_ts", nullable = true)
	public Date getDecidedTs() { return iDecidedTs; }
	public void setDecidedTs(Date decidedTs) { iDecidedTs = decidedTs; }

	@Column(name = "decision_note", nullable = true, length = 1000)
	public String getDecisionNote() { return iDecisionNote; }
	public void setDecisionNote(String decisionNote) { iDecisionNote = decisionNote; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AttendanceExcuse)) return false;
		if (getUniqueId() == null || ((AttendanceExcuse)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AttendanceExcuse)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AttendanceExcuse[" + getUniqueId() + "]";
	}
}
