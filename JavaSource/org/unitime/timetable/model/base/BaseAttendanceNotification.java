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
import org.unitime.timetable.model.AttendanceNotification;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the attendance_notification table of dbupdate.xml version 279 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseAttendanceNotification implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iFollowupId;
	private Long iStudentId;
	private Long iClassId;
	private String iRecipientType;
	private String iRecipientName;
	private String iAddress;
	private String iSubject;
	private String iStatus;
	private String iError;
	private Date iSentTs;

	public BaseAttendanceNotification() {
	}

	public BaseAttendanceNotification(Long uniqueId) {
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

	@Column(name = "followup_id", nullable = true)
	public Long getFollowupId() { return iFollowupId; }
	public void setFollowupId(Long followupId) { iFollowupId = followupId; }

	@Column(name = "student_id", nullable = true)
	public Long getStudentId() { return iStudentId; }
	public void setStudentId(Long studentId) { iStudentId = studentId; }

	@Column(name = "class_id", nullable = true)
	public Long getClassId() { return iClassId; }
	public void setClassId(Long classId) { iClassId = classId; }

	@Column(name = "recipient_type", nullable = false, length = 20)
	public String getRecipientType() { return iRecipientType; }
	public void setRecipientType(String recipientType) { iRecipientType = recipientType; }

	@Column(name = "recipient_name", nullable = true, length = 200)
	public String getRecipientName() { return iRecipientName; }
	public void setRecipientName(String recipientName) { iRecipientName = recipientName; }

	@Column(name = "address", nullable = true, length = 200)
	public String getAddress() { return iAddress; }
	public void setAddress(String address) { iAddress = address; }

	@Column(name = "subject", nullable = true, length = 300)
	public String getSubject() { return iSubject; }
	public void setSubject(String subject) { iSubject = subject; }

	@Column(name = "status", nullable = false, length = 20)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "error", nullable = true, length = 1000)
	public String getError() { return iError; }
	public void setError(String error) { iError = error; }

	@Column(name = "sent_ts", nullable = false)
	public Date getSentTs() { return iSentTs; }
	public void setSentTs(Date sentTs) { iSentTs = sentTs; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AttendanceNotification)) return false;
		if (getUniqueId() == null || ((AttendanceNotification)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AttendanceNotification)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AttendanceNotification[" + getUniqueId() + "]";
	}
}
