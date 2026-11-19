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
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;
import java.util.Date;

import org.unitime.commons.annotations.UniqueIdGenerator;
import org.unitime.timetable.model.AssignmentChange;
import org.unitime.timetable.model.Session;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation
 * Ant task is not part of the Maven build). Mappings follow the assignment_change
 * table of dbupdate.xml version 277; there is no hbm.xml (JPA annotations only).
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseAssignmentChange implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private String iChangeType;
	private String iChangeMode;
	private String iStatus;
	private Long iSubjectId;
	private String iSubjectName;
	private String iSolverKey;
	private String iSummary;
	private String iPayload;
	private String iReason;
	private String iRequestedBy;
	private String iRequestedName;
	private Date iRequestedTs;
	private String iDecidedBy;
	private String iDecidedName;
	private Date iDecidedTs;
	private String iDecisionNote;
	private String iResultMessage;
	private Boolean iRequesterSeen;
	private Session iSession;

	public BaseAssignmentChange() {
	}

	public BaseAssignmentChange(Long uniqueId) {
		setUniqueId(uniqueId);
	}

	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "change_type", nullable = false, length = 10)
	public String getChangeType() { return iChangeType; }
	public void setChangeType(String changeType) { iChangeType = changeType; }

	@Column(name = "change_mode", nullable = false, length = 10)
	public String getChangeMode() { return iChangeMode; }
	public void setChangeMode(String changeMode) { iChangeMode = changeMode; }

	@Column(name = "status", nullable = false, length = 10)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "subject_id", nullable = true)
	public Long getSubjectId() { return iSubjectId; }
	public void setSubjectId(Long subjectId) { iSubjectId = subjectId; }

	@Column(name = "subject_name", nullable = true, length = 200)
	public String getSubjectName() { return iSubjectName; }
	public void setSubjectName(String subjectName) { iSubjectName = subjectName; }

	@Column(name = "solver_key", nullable = true, length = 100)
	public String getSolverKey() { return iSolverKey; }
	public void setSolverKey(String solverKey) { iSolverKey = solverKey; }

	@Column(name = "summary", nullable = true, length = 4000)
	public String getSummary() { return iSummary; }
	public void setSummary(String summary) { iSummary = summary; }

	@Lob
	@Column(name = "payload", nullable = true)
	public String getPayload() { return iPayload; }
	public void setPayload(String payload) { iPayload = payload; }

	@Column(name = "reason", nullable = false, length = 2000)
	public String getReason() { return iReason; }
	public void setReason(String reason) { iReason = reason; }

	@Column(name = "requested_by", nullable = true, length = 40)
	public String getRequestedBy() { return iRequestedBy; }
	public void setRequestedBy(String requestedBy) { iRequestedBy = requestedBy; }

	@Column(name = "requested_name", nullable = true, length = 100)
	public String getRequestedName() { return iRequestedName; }
	public void setRequestedName(String requestedName) { iRequestedName = requestedName; }

	@Column(name = "requested_ts", nullable = false)
	public Date getRequestedTs() { return iRequestedTs; }
	public void setRequestedTs(Date requestedTs) { iRequestedTs = requestedTs; }

	@Column(name = "decided_by", nullable = true, length = 40)
	public String getDecidedBy() { return iDecidedBy; }
	public void setDecidedBy(String decidedBy) { iDecidedBy = decidedBy; }

	@Column(name = "decided_name", nullable = true, length = 100)
	public String getDecidedName() { return iDecidedName; }
	public void setDecidedName(String decidedName) { iDecidedName = decidedName; }

	@Column(name = "decided_ts", nullable = true)
	public Date getDecidedTs() { return iDecidedTs; }
	public void setDecidedTs(Date decidedTs) { iDecidedTs = decidedTs; }

	@Column(name = "decision_note", nullable = true, length = 2000)
	public String getDecisionNote() { return iDecisionNote; }
	public void setDecisionNote(String decisionNote) { iDecisionNote = decisionNote; }

	@Column(name = "result_message", nullable = true, length = 2000)
	public String getResultMessage() { return iResultMessage; }
	public void setResultMessage(String resultMessage) { iResultMessage = resultMessage; }

	@Column(name = "requester_seen", nullable = true)
	public Boolean getRequesterSeen() { return iRequesterSeen; }
	public void setRequesterSeen(Boolean requesterSeen) { iRequesterSeen = requesterSeen; }

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	public Session getSession() { return iSession; }
	public void setSession(Session session) { iSession = session; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AssignmentChange)) return false;
		if (getUniqueId() == null || ((AssignmentChange)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AssignmentChange)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AssignmentChange[" + getUniqueId() + " " + getChangeType() + " " + getSubjectName() + " " + getStatus() + "]";
	}
}
