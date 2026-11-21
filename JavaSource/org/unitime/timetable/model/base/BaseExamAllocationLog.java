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
import org.unitime.timetable.model.ExamAllocationLog;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the exam_alloc_log table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseExamAllocationLog implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iExamTypeId;
	private String iKind;
	private String iAction;
	private Long iExamPeriodId;
	private String iDetail;
	private String iUserId;
	private String iUserName;
	private Date iTimeStamp;

	public BaseExamAllocationLog() {
	}

	public BaseExamAllocationLog(Long uniqueId) {
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

	@Column(name = "exam_type_id", nullable = true)
	public Long getExamTypeId() { return iExamTypeId; }
	public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

	@Column(name = "kind", nullable = false, length = 20)
	public String getKind() { return iKind; }
	public void setKind(String kind) { iKind = kind; }

	@Column(name = "action", nullable = false, length = 40)
	public String getAction() { return iAction; }
	public void setAction(String action) { iAction = action; }

	@Column(name = "exam_period_id", nullable = true)
	public Long getExamPeriodId() { return iExamPeriodId; }
	public void setExamPeriodId(Long examPeriodId) { iExamPeriodId = examPeriodId; }

	@Column(name = "detail", nullable = true, length = 2000)
	public String getDetail() { return iDetail; }
	public void setDetail(String detail) { iDetail = detail; }

	@Column(name = "user_id", nullable = true, length = 40)
	public String getUserId() { return iUserId; }
	public void setUserId(String userId) { iUserId = userId; }

	@Column(name = "user_name", nullable = true, length = 100)
	public String getUserName() { return iUserName; }
	public void setUserName(String userName) { iUserName = userName; }

	@Column(name = "time_stamp", nullable = false)
	public Date getTimeStamp() { return iTimeStamp; }
	public void setTimeStamp(Date timeStamp) { iTimeStamp = timeStamp; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ExamAllocationLog)) return false;
		if (getUniqueId() == null || ((ExamAllocationLog)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((ExamAllocationLog)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "ExamAllocationLog[" + getUniqueId() + "]";
	}
}
