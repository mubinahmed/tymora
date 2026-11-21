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
import org.unitime.timetable.model.AttendanceFollowup;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the attendance_followup table of dbupdate.xml version 279 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseAttendanceFollowup implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iStudentId;
	private Long iClassId;
	private String iRuleType;
	private String iDetail;
	private String iStatus;
	private Date iCreatedTs;
	private String iRecipients;
	private String iNotes;
	private String iOutcome;
	private Date iNextReview;
	private String iUpdatedBy;
	private Date iUpdatedTs;

	public BaseAttendanceFollowup() {
	}

	public BaseAttendanceFollowup(Long uniqueId) {
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

	@Column(name = "class_id", nullable = true)
	public Long getClassId() { return iClassId; }
	public void setClassId(Long classId) { iClassId = classId; }

	@Column(name = "rule_type", nullable = false, length = 30)
	public String getRuleType() { return iRuleType; }
	public void setRuleType(String ruleType) { iRuleType = ruleType; }

	@Column(name = "detail", nullable = true, length = 1000)
	public String getDetail() { return iDetail; }
	public void setDetail(String detail) { iDetail = detail; }

	@Column(name = "status", nullable = false, length = 20)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "created_ts", nullable = false)
	public Date getCreatedTs() { return iCreatedTs; }
	public void setCreatedTs(Date createdTs) { iCreatedTs = createdTs; }

	@Column(name = "recipients", nullable = true, length = 1000)
	public String getRecipients() { return iRecipients; }
	public void setRecipients(String recipients) { iRecipients = recipients; }

	@Column(name = "notes", nullable = true, length = 2000)
	public String getNotes() { return iNotes; }
	public void setNotes(String notes) { iNotes = notes; }

	@Column(name = "outcome", nullable = true, length = 1000)
	public String getOutcome() { return iOutcome; }
	public void setOutcome(String outcome) { iOutcome = outcome; }

	@Column(name = "next_review", nullable = true)
	public Date getNextReview() { return iNextReview; }
	public void setNextReview(Date nextReview) { iNextReview = nextReview; }

	@Column(name = "updated_by", nullable = true, length = 100)
	public String getUpdatedBy() { return iUpdatedBy; }
	public void setUpdatedBy(String updatedBy) { iUpdatedBy = updatedBy; }

	@Column(name = "updated_ts", nullable = true)
	public Date getUpdatedTs() { return iUpdatedTs; }
	public void setUpdatedTs(Date updatedTs) { iUpdatedTs = updatedTs; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AttendanceFollowup)) return false;
		if (getUniqueId() == null || ((AttendanceFollowup)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AttendanceFollowup)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AttendanceFollowup[" + getUniqueId() + "]";
	}
}
