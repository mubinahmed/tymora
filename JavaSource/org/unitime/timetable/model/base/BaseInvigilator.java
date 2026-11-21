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
import org.unitime.timetable.model.Invigilator;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the invigilator table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseInvigilator implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private String iExternalId;
	private String iName;
	private String iEmail;
	private Long iDepartmentId;
	private Long iInstructorId;
	private String iDutyTypes;
	private Integer iMaxPerDay;
	private Integer iMaxTotal;
	private Boolean iActive;
	private String iNote;

	public BaseInvigilator() {
	}

	public BaseInvigilator(Long uniqueId) {
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

	@Column(name = "external_uid", nullable = true, length = 40)
	public String getExternalId() { return iExternalId; }
	public void setExternalId(String externalId) { iExternalId = externalId; }

	@Column(name = "name", nullable = false, length = 100)
	public String getName() { return iName; }
	public void setName(String name) { iName = name; }

	@Column(name = "email", nullable = true, length = 200)
	public String getEmail() { return iEmail; }
	public void setEmail(String email) { iEmail = email; }

	@Column(name = "department_id", nullable = true)
	public Long getDepartmentId() { return iDepartmentId; }
	public void setDepartmentId(Long departmentId) { iDepartmentId = departmentId; }

	@Column(name = "instructor_id", nullable = true)
	public Long getInstructorId() { return iInstructorId; }
	public void setInstructorId(Long instructorId) { iInstructorId = instructorId; }

	@Column(name = "duty_types", nullable = false, length = 100)
	public String getDutyTypes() { return iDutyTypes; }
	public void setDutyTypes(String dutyTypes) { iDutyTypes = dutyTypes; }

	@Column(name = "max_per_day", nullable = true)
	public Integer getMaxPerDay() { return iMaxPerDay; }
	public void setMaxPerDay(Integer maxPerDay) { iMaxPerDay = maxPerDay; }

	@Column(name = "max_total", nullable = true)
	public Integer getMaxTotal() { return iMaxTotal; }
	public void setMaxTotal(Integer maxTotal) { iMaxTotal = maxTotal; }

	@Column(name = "active", nullable = false)
	public Boolean getActive() { return iActive; }
	public void setActive(Boolean active) { iActive = active; }

	@Column(name = "note", nullable = true, length = 500)
	public String getNote() { return iNote; }
	public void setNote(String note) { iNote = note; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof Invigilator)) return false;
		if (getUniqueId() == null || ((Invigilator)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((Invigilator)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "Invigilator[" + getUniqueId() + "]";
	}
}
