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
import org.unitime.timetable.model.ExamAllocationStatus;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the exam_alloc_status table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseExamAllocationStatus implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iExamTypeId;
	private String iKind;
	private String iStatus;
	private String iApprovedBy;
	private Date iApprovedTs;
	private String iPublishedBy;
	private Date iPublishedTs;
	private String iNote;
	private Integer iVersion;

	public BaseExamAllocationStatus() {
	}

	public BaseExamAllocationStatus(Long uniqueId) {
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

	@Column(name = "exam_type_id", nullable = false)
	public Long getExamTypeId() { return iExamTypeId; }
	public void setExamTypeId(Long examTypeId) { iExamTypeId = examTypeId; }

	@Column(name = "kind", nullable = false, length = 20)
	public String getKind() { return iKind; }
	public void setKind(String kind) { iKind = kind; }

	@Column(name = "status", nullable = false, length = 20)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "approved_by", nullable = true, length = 100)
	public String getApprovedBy() { return iApprovedBy; }
	public void setApprovedBy(String approvedBy) { iApprovedBy = approvedBy; }

	@Column(name = "approved_ts", nullable = true)
	public Date getApprovedTs() { return iApprovedTs; }
	public void setApprovedTs(Date approvedTs) { iApprovedTs = approvedTs; }

	@Column(name = "published_by", nullable = true, length = 100)
	public String getPublishedBy() { return iPublishedBy; }
	public void setPublishedBy(String publishedBy) { iPublishedBy = publishedBy; }

	@Column(name = "published_ts", nullable = true)
	public Date getPublishedTs() { return iPublishedTs; }
	public void setPublishedTs(Date publishedTs) { iPublishedTs = publishedTs; }

	@Column(name = "note", nullable = true, length = 2000)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Column(name = "version", nullable = false)
	public Integer getVersion() { return iVersion; }
	public void setVersion(Integer version) { iVersion = version; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ExamAllocationStatus)) return false;
		if (getUniqueId() == null || ((ExamAllocationStatus)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((ExamAllocationStatus)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "ExamAllocationStatus[" + getUniqueId() + "]";
	}
}
