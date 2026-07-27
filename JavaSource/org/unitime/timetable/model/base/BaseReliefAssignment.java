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
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.ReliefAssignment;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StaffAbsence;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation
 * Ant task is not part of the Maven build). Keep field/column mappings in sync
 * with ReliefAssignment.hbm.xml.
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseReliefAssignment implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Date iMeetingDate;
	private Integer iStartPeriod;
	private Integer iStopPeriod;
	private Long iLocationPermanentId;
	private String iReliefUid;
	private String iReliefName;
	private Integer iStatus;
	private String iAssignedBy;
	private String iNote;
	private Date iTimeStamp;

	private Session iSession;
	private StaffAbsence iAbsence;
	private Class_ iClazz;

	public BaseReliefAssignment() {
	}

	public BaseReliefAssignment(Long uniqueId) {
		setUniqueId(uniqueId);
	}


	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "meeting_date", nullable = false)
	public Date getMeetingDate() { return iMeetingDate; }
	public void setMeetingDate(Date meetingDate) { iMeetingDate = meetingDate; }

	@Column(name = "start_period", nullable = false)
	public Integer getStartPeriod() { return iStartPeriod; }
	public void setStartPeriod(Integer startPeriod) { iStartPeriod = startPeriod; }

	@Column(name = "stop_period", nullable = false)
	public Integer getStopPeriod() { return iStopPeriod; }
	public void setStopPeriod(Integer stopPeriod) { iStopPeriod = stopPeriod; }

	@Column(name = "location_perm_id", nullable = true)
	public Long getLocationPermanentId() { return iLocationPermanentId; }
	public void setLocationPermanentId(Long locationPermanentId) { iLocationPermanentId = locationPermanentId; }

	@Column(name = "relief_uid", nullable = true, length = 40)
	public String getReliefUid() { return iReliefUid; }
	public void setReliefUid(String reliefUid) { iReliefUid = reliefUid; }

	@Column(name = "relief_name", nullable = true, length = 100)
	public String getReliefName() { return iReliefName; }
	public void setReliefName(String reliefName) { iReliefName = reliefName; }

	@Column(name = "status", nullable = false)
	public Integer getStatus() { return iStatus; }
	public void setStatus(Integer status) { iStatus = status; }

	@Column(name = "assigned_by", nullable = true, length = 100)
	public String getAssignedBy() { return iAssignedBy; }
	public void setAssignedBy(String assignedBy) { iAssignedBy = assignedBy; }

	@Column(name = "note", nullable = true, length = 1000)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Column(name = "time_stamp", nullable = true)
	public Date getTimeStamp() { return iTimeStamp; }
	public void setTimeStamp(Date timeStamp) { iTimeStamp = timeStamp; }

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	public Session getSession() { return iSession; }
	public void setSession(Session session) { iSession = session; }

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "absence_id", nullable = false)
	public StaffAbsence getAbsence() { return iAbsence; }
	public void setAbsence(StaffAbsence absence) { iAbsence = absence; }

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "class_id", nullable = false)
	public Class_ getClazz() { return iClazz; }
	public void setClazz(Class_ clazz) { iClazz = clazz; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ReliefAssignment)) return false;
		if (getUniqueId() == null || ((ReliefAssignment)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((ReliefAssignment)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "ReliefAssignment["+getUniqueId()+"]";
	}

	public String toDebugString() {
		return "ReliefAssignment[" +
			"\n	AssignedBy: " + getAssignedBy() +
			"\n	MeetingDate: " + getMeetingDate() +
			"\n	Note: " + getNote() +
			"\n	ReliefName: " + getReliefName() +
			"\n	ReliefUid: " + getReliefUid() +
			"\n	StartPeriod: " + getStartPeriod() +
			"\n	Status: " + getStatus() +
			"\n	StopPeriod: " + getStopPeriod() +
			"\n	UniqueId: " + getUniqueId() +
			"]";
	}
}
