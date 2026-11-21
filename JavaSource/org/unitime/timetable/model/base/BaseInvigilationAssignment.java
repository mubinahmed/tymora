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
import org.unitime.timetable.model.InvigilationAssignment;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the invigilation_asgn table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseInvigilationAssignment implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Long iExamTypeId;
	private Long iExamPeriodId;
	private Long iLocationId;
	private Long iInvigilatorId;
	private String iDutyType;
	private String iSource;
	private Boolean iLocked;
	private Date iLastModified;
	private String iModifiedBy;

	public BaseInvigilationAssignment() {
	}

	public BaseInvigilationAssignment(Long uniqueId) {
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

	@Column(name = "exam_period_id", nullable = false)
	public Long getExamPeriodId() { return iExamPeriodId; }
	public void setExamPeriodId(Long examPeriodId) { iExamPeriodId = examPeriodId; }

	@Column(name = "location_id", nullable = true)
	public Long getLocationId() { return iLocationId; }
	public void setLocationId(Long locationId) { iLocationId = locationId; }

	@Column(name = "invigilator_id", nullable = false)
	public Long getInvigilatorId() { return iInvigilatorId; }
	public void setInvigilatorId(Long invigilatorId) { iInvigilatorId = invigilatorId; }

	@Column(name = "duty_type", nullable = false, length = 20)
	public String getDutyType() { return iDutyType; }
	public void setDutyType(String dutyType) { iDutyType = dutyType; }

	@Column(name = "source", nullable = false, length = 10)
	public String getSource() { return iSource; }
	public void setSource(String source) { iSource = source; }

	@Column(name = "locked", nullable = false)
	public Boolean getLocked() { return iLocked; }
	public void setLocked(Boolean locked) { iLocked = locked; }

	@Column(name = "last_modified", nullable = false)
	public Date getLastModified() { return iLastModified; }
	public void setLastModified(Date lastModified) { iLastModified = lastModified; }

	@Column(name = "modified_by", nullable = true, length = 100)
	public String getModifiedBy() { return iModifiedBy; }
	public void setModifiedBy(String modifiedBy) { iModifiedBy = modifiedBy; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof InvigilationAssignment)) return false;
		if (getUniqueId() == null || ((InvigilationAssignment)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((InvigilationAssignment)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "InvigilationAssignment[" + getUniqueId() + "]";
	}
}
