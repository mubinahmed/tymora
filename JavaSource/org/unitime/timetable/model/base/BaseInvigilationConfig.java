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
import org.unitime.timetable.model.InvigilationConfig;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation Ant task is not part of
 * the Maven build). Mappings follow the invigilation_config table of dbupdate.xml version 278 (JPA annotations only).
 * Foreign keys are plain id columns.
 */
@MappedSuperclass
public abstract class BaseInvigilationConfig implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Long iSessionId;
	private Integer iStudentsPerInvigilator;
	private Integer iMinPerRoom;
	private Boolean iChiefPerRoom;
	private Integer iExtraSpecialRoom;
	private Integer iReservesPerPeriod;
	private Integer iDefaultMaxPerDay;
	private Integer iDefaultMaxTotal;
	private Boolean iAvoidOwnExam;

	public BaseInvigilationConfig() {
	}

	public BaseInvigilationConfig(Long uniqueId) {
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

	@Column(name = "students_per_invig", nullable = false)
	public Integer getStudentsPerInvigilator() { return iStudentsPerInvigilator; }
	public void setStudentsPerInvigilator(Integer studentsPerInvigilator) { iStudentsPerInvigilator = studentsPerInvigilator; }

	@Column(name = "min_per_room", nullable = false)
	public Integer getMinPerRoom() { return iMinPerRoom; }
	public void setMinPerRoom(Integer minPerRoom) { iMinPerRoom = minPerRoom; }

	@Column(name = "chief_per_room", nullable = false)
	public Boolean getChiefPerRoom() { return iChiefPerRoom; }
	public void setChiefPerRoom(Boolean chiefPerRoom) { iChiefPerRoom = chiefPerRoom; }

	@Column(name = "extra_special_room", nullable = false)
	public Integer getExtraSpecialRoom() { return iExtraSpecialRoom; }
	public void setExtraSpecialRoom(Integer extraSpecialRoom) { iExtraSpecialRoom = extraSpecialRoom; }

	@Column(name = "reserves_per_period", nullable = false)
	public Integer getReservesPerPeriod() { return iReservesPerPeriod; }
	public void setReservesPerPeriod(Integer reservesPerPeriod) { iReservesPerPeriod = reservesPerPeriod; }

	@Column(name = "default_max_per_day", nullable = false)
	public Integer getDefaultMaxPerDay() { return iDefaultMaxPerDay; }
	public void setDefaultMaxPerDay(Integer defaultMaxPerDay) { iDefaultMaxPerDay = defaultMaxPerDay; }

	@Column(name = "default_max_total", nullable = false)
	public Integer getDefaultMaxTotal() { return iDefaultMaxTotal; }
	public void setDefaultMaxTotal(Integer defaultMaxTotal) { iDefaultMaxTotal = defaultMaxTotal; }

	@Column(name = "avoid_own_exam", nullable = false)
	public Boolean getAvoidOwnExam() { return iAvoidOwnExam; }
	public void setAvoidOwnExam(Boolean avoidOwnExam) { iAvoidOwnExam = avoidOwnExam; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof InvigilationConfig)) return false;
		if (getUniqueId() == null || ((InvigilationConfig)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((InvigilationConfig)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "InvigilationConfig[" + getUniqueId() + "]";
	}
}
