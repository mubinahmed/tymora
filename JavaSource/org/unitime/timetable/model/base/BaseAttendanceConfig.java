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
import org.unitime.timetable.model.AttendanceConfig;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the attendance_config table of dbupdate.xml version 279 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseAttendanceConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Integer iMinPct;
	private Integer iEligibilityPct;
	private Integer iLateMinutes;
	private Integer iCheckinMinutes;
	private Boolean iRollcallEnabled;
	private Boolean iQrEnabled;
	private Integer iQrRotationSec;
	private Boolean iOneDevice;
	private Boolean iLateCountsPresent;
	private Boolean iExcusedCountsPresent;
	private Integer iConsecutiveAbsences;
	private Integer iMinSessions;
	private String iAlertRecipients;
	private String iExcuseApprover;
	private Boolean iNotifyStudent;
	private String iExportColumns;

	public BaseAttendanceConfig() {
	}

	public BaseAttendanceConfig(Long uniqueId) {
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

	@Column(name = "min_pct", nullable = false)
	public Integer getMinPct() { return iMinPct; }
	public void setMinPct(Integer minPct) { iMinPct = minPct; }

	@Column(name = "eligibility_pct", nullable = true)
	public Integer getEligibilityPct() { return iEligibilityPct; }
	public void setEligibilityPct(Integer eligibilityPct) { iEligibilityPct = eligibilityPct; }

	@Column(name = "late_minutes", nullable = false)
	public Integer getLateMinutes() { return iLateMinutes; }
	public void setLateMinutes(Integer lateMinutes) { iLateMinutes = lateMinutes; }

	@Column(name = "checkin_minutes", nullable = false)
	public Integer getCheckinMinutes() { return iCheckinMinutes; }
	public void setCheckinMinutes(Integer checkinMinutes) { iCheckinMinutes = checkinMinutes; }

	@Column(name = "rollcall_enabled", nullable = false)
	public Boolean getRollcallEnabled() { return iRollcallEnabled; }
	public void setRollcallEnabled(Boolean rollcallEnabled) { iRollcallEnabled = rollcallEnabled; }

	@Column(name = "qr_enabled", nullable = false)
	public Boolean getQrEnabled() { return iQrEnabled; }
	public void setQrEnabled(Boolean qrEnabled) { iQrEnabled = qrEnabled; }

	@Column(name = "qr_rotation_sec", nullable = false)
	public Integer getQrRotationSec() { return iQrRotationSec; }
	public void setQrRotationSec(Integer qrRotationSec) { iQrRotationSec = qrRotationSec; }

	@Column(name = "one_device", nullable = false)
	public Boolean getOneDevice() { return iOneDevice; }
	public void setOneDevice(Boolean oneDevice) { iOneDevice = oneDevice; }

	@Column(name = "late_counts_present", nullable = false)
	public Boolean getLateCountsPresent() { return iLateCountsPresent; }
	public void setLateCountsPresent(Boolean lateCountsPresent) { iLateCountsPresent = lateCountsPresent; }

	@Column(name = "excused_counts_present", nullable = false)
	public Boolean getExcusedCountsPresent() { return iExcusedCountsPresent; }
	public void setExcusedCountsPresent(Boolean excusedCountsPresent) { iExcusedCountsPresent = excusedCountsPresent; }

	@Column(name = "consecutive_absences", nullable = false)
	public Integer getConsecutiveAbsences() { return iConsecutiveAbsences; }
	public void setConsecutiveAbsences(Integer consecutiveAbsences) { iConsecutiveAbsences = consecutiveAbsences; }

	@Column(name = "min_sessions", nullable = false)
	public Integer getMinSessions() { return iMinSessions; }
	public void setMinSessions(Integer minSessions) { iMinSessions = minSessions; }

	@Column(name = "alert_recipients", nullable = false, length = 100)
	public String getAlertRecipients() { return iAlertRecipients; }
	public void setAlertRecipients(String alertRecipients) { iAlertRecipients = alertRecipients; }

	@Column(name = "excuse_approver", nullable = false, length = 20)
	public String getExcuseApprover() { return iExcuseApprover; }
	public void setExcuseApprover(String excuseApprover) { iExcuseApprover = excuseApprover; }

	@Column(name = "notify_student", nullable = false)
	public Boolean getNotifyStudent() { return iNotifyStudent; }
	public void setNotifyStudent(Boolean notifyStudent) { iNotifyStudent = notifyStudent; }

	@Column(name = "export_columns", nullable = true, length = 500)
	public String getExportColumns() { return iExportColumns; }
	public void setExportColumns(String exportColumns) { iExportColumns = exportColumns; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AttendanceConfig)) return false;
		if (getUniqueId() == null || ((AttendanceConfig)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AttendanceConfig)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AttendanceConfig[" + getUniqueId() + "]";
	}
}
