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
import org.unitime.timetable.model.AttendanceSession;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the attendance_session table of dbupdate.xml version 279 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseAttendanceSession implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iMeetingId;
	private Long iClassId;
	private Date iMeetingDate;
	private Integer iStartPeriod;
	private Integer iStopPeriod;
	private Long iLocationPermId;
	private String iTakerUid;
	private String iTakerName;
	private String iStatus;
	private Date iCheckinOpened;
	private Date iCheckinUntil;
	private String iQrSecret;
	private String iFinalisedBy;
	private Date iFinalisedTs;

	public BaseAttendanceSession() {
	}

	public BaseAttendanceSession(Long uniqueId) {
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

	@Column(name = "meeting_id", nullable = false)
	public Long getMeetingId() { return iMeetingId; }
	public void setMeetingId(Long meetingId) { iMeetingId = meetingId; }

	@Column(name = "class_id", nullable = false)
	public Long getClassId() { return iClassId; }
	public void setClassId(Long classId) { iClassId = classId; }

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
	public Long getLocationPermId() { return iLocationPermId; }
	public void setLocationPermId(Long locationPermId) { iLocationPermId = locationPermId; }

	@Column(name = "taker_uid", nullable = true, length = 40)
	public String getTakerUid() { return iTakerUid; }
	public void setTakerUid(String takerUid) { iTakerUid = takerUid; }

	@Column(name = "taker_name", nullable = true, length = 100)
	public String getTakerName() { return iTakerName; }
	public void setTakerName(String takerName) { iTakerName = takerName; }

	@Column(name = "status", nullable = false, length = 20)
	public String getStatus() { return iStatus; }
	public void setStatus(String status) { iStatus = status; }

	@Column(name = "checkin_opened", nullable = true)
	public Date getCheckinOpened() { return iCheckinOpened; }
	public void setCheckinOpened(Date checkinOpened) { iCheckinOpened = checkinOpened; }

	@Column(name = "checkin_until", nullable = true)
	public Date getCheckinUntil() { return iCheckinUntil; }
	public void setCheckinUntil(Date checkinUntil) { iCheckinUntil = checkinUntil; }

	@Column(name = "qr_secret", nullable = true, length = 64)
	public String getQrSecret() { return iQrSecret; }
	public void setQrSecret(String qrSecret) { iQrSecret = qrSecret; }

	@Column(name = "finalised_by", nullable = true, length = 100)
	public String getFinalisedBy() { return iFinalisedBy; }
	public void setFinalisedBy(String finalisedBy) { iFinalisedBy = finalisedBy; }

	@Column(name = "finalised_ts", nullable = true)
	public Date getFinalisedTs() { return iFinalisedTs; }
	public void setFinalisedTs(Date finalisedTs) { iFinalisedTs = finalisedTs; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AttendanceSession)) return false;
		if (getUniqueId() == null || ((AttendanceSession)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AttendanceSession)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AttendanceSession[" + getUniqueId() + "]";
	}
}
