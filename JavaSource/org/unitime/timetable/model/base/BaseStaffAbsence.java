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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;
import java.util.Date;

import org.unitime.commons.annotations.UniqueIdGenerator;
import org.unitime.timetable.model.AbsenceReason;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StaffAbsence;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation
 * Ant task is not part of the Maven build). Keep field/column mappings in sync
 * with StaffAbsence.hbm.xml.
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseStaffAbsence implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private String iExternalUniqueId;
	private String iName;
	private Date iStartDate;
	private Date iEndDate;
	private String iNote;
	private Integer iStatus;
	private String iRequestorUid;
	private Date iTimeStamp;

	private Session iSession;
	private AbsenceReason iReason;

	public BaseStaffAbsence() {
	}

	public BaseStaffAbsence(Long uniqueId) {
		setUniqueId(uniqueId);
	}


	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "external_uid", nullable = false, length = 40)
	public String getExternalUniqueId() { return iExternalUniqueId; }
	public void setExternalUniqueId(String externalUniqueId) { iExternalUniqueId = externalUniqueId; }

	@Column(name = "name", nullable = true, length = 100)
	public String getName() { return iName; }
	public void setName(String name) { iName = name; }

	@Column(name = "start_date", nullable = false)
	public Date getStartDate() { return iStartDate; }
	public void setStartDate(Date startDate) { iStartDate = startDate; }

	@Column(name = "end_date", nullable = false)
	public Date getEndDate() { return iEndDate; }
	public void setEndDate(Date endDate) { iEndDate = endDate; }

	@Column(name = "note", nullable = true, length = 2048)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Column(name = "status", nullable = false)
	public Integer getStatus() { return iStatus; }
	public void setStatus(Integer status) { iStatus = status; }

	@Column(name = "requestor_uid", nullable = true, length = 40)
	public String getRequestorUid() { return iRequestorUid; }
	public void setRequestorUid(String requestorUid) { iRequestorUid = requestorUid; }

	@Column(name = "time_stamp", nullable = true)
	public Date getTimeStamp() { return iTimeStamp; }
	public void setTimeStamp(Date timeStamp) { iTimeStamp = timeStamp; }

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	public Session getSession() { return iSession; }
	public void setSession(Session session) { iSession = session; }

	@ManyToOne(optional = true, fetch = FetchType.LAZY)
	@JoinColumn(name = "reason_id", nullable = true)
	public AbsenceReason getReason() { return iReason; }
	public void setReason(AbsenceReason reason) { iReason = reason; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof StaffAbsence)) return false;
		if (getUniqueId() == null || ((StaffAbsence)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((StaffAbsence)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "StaffAbsence["+getUniqueId()+" "+getName()+"]";
	}

	public String toDebugString() {
		return "StaffAbsence[" +
			"\n	EndDate: " + getEndDate() +
			"\n	ExternalUniqueId: " + getExternalUniqueId() +
			"\n	Name: " + getName() +
			"\n	Note: " + getNote() +
			"\n	Reason: " + getReason() +
			"\n	RequestorUid: " + getRequestorUid() +
			"\n	Session: " + getSession() +
			"\n	StartDate: " + getStartDate() +
			"\n	Status: " + getStatus() +
			"\n	TimeStamp: " + getTimeStamp() +
			"\n	UniqueId: " + getUniqueId() +
			"]";
	}
}
