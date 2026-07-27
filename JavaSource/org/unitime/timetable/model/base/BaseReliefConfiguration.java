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

import org.unitime.commons.annotations.UniqueIdGenerator;
import org.unitime.timetable.model.ReliefConfiguration;
import org.unitime.timetable.model.Session;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation
 * Ant task is not part of the Maven build). Keep field/column mappings in sync
 * with ReliefConfiguration.hbm.xml.
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseReliefConfiguration implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private Integer iWeeklyCap;
	private Boolean iPreferContinuity;
	private Boolean iExcludeNonTeaching;
	private Boolean iSameDeptFirst;
	private String iExemptUids;

	private Session iSession;

	public BaseReliefConfiguration() {
	}

	public BaseReliefConfiguration(Long uniqueId) {
		setUniqueId(uniqueId);
	}


	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "weekly_cap", nullable = true)
	public Integer getWeeklyCap() { return iWeeklyCap; }
	public void setWeeklyCap(Integer weeklyCap) { iWeeklyCap = weeklyCap; }

	@Column(name = "prefer_continuity", nullable = true)
	public Boolean isPreferContinuity() { return iPreferContinuity; }
	public Boolean getPreferContinuity() { return iPreferContinuity; }
	public void setPreferContinuity(Boolean preferContinuity) { iPreferContinuity = preferContinuity; }

	@Column(name = "exclude_non_teaching", nullable = true)
	public Boolean isExcludeNonTeaching() { return iExcludeNonTeaching; }
	public Boolean getExcludeNonTeaching() { return iExcludeNonTeaching; }
	public void setExcludeNonTeaching(Boolean excludeNonTeaching) { iExcludeNonTeaching = excludeNonTeaching; }

	@Column(name = "same_dept_first", nullable = true)
	public Boolean isSameDeptFirst() { return iSameDeptFirst; }
	public Boolean getSameDeptFirst() { return iSameDeptFirst; }
	public void setSameDeptFirst(Boolean sameDeptFirst) { iSameDeptFirst = sameDeptFirst; }

	@Column(name = "exempt_uids", nullable = true, length = 2048)
	public String getExemptUids() { return iExemptUids; }
	public void setExemptUids(String exemptUids) { iExemptUids = exemptUids; }

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	public Session getSession() { return iSession; }
	public void setSession(Session session) { iSession = session; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof ReliefConfiguration)) return false;
		if (getUniqueId() == null || ((ReliefConfiguration)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((ReliefConfiguration)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "ReliefConfiguration["+getUniqueId()+"]";
	}

	public String toDebugString() {
		return "ReliefConfiguration[" +
			"\n	ExcludeNonTeaching: " + getExcludeNonTeaching() +
			"\n	ExemptUids: " + getExemptUids() +
			"\n	PreferContinuity: " + getPreferContinuity() +
			"\n	SameDeptFirst: " + getSameDeptFirst() +
			"\n	Session: " + getSession() +
			"\n	UniqueId: " + getUniqueId() +
			"\n	WeeklyCap: " + getWeeklyCap() +
			"]";
	}
}
