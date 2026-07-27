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

import org.unitime.commons.annotations.UniqueIdGenerator;
import org.unitime.timetable.model.AbsenceReason;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation
 * Ant task is not part of the Maven build). Keep field/column mappings in sync
 * with AbsenceReason.hbm.xml.
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseAbsenceReason implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private String iReference;
	private String iLabel;
	private Integer iSortOrder;

	public BaseAbsenceReason() {
	}

	public BaseAbsenceReason(Long uniqueId) {
		setUniqueId(uniqueId);
	}


	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "reference", nullable = false, length = 20)
	public String getReference() { return iReference; }
	public void setReference(String reference) { iReference = reference; }

	@Column(name = "label", nullable = true, length = 60)
	public String getLabel() { return iLabel; }
	public void setLabel(String label) { iLabel = label; }

	@Column(name = "sort_order", nullable = false)
	public Integer getSortOrder() { return iSortOrder; }
	public void setSortOrder(Integer sortOrder) { iSortOrder = sortOrder; }

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof AbsenceReason)) return false;
		if (getUniqueId() == null || ((AbsenceReason)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((AbsenceReason)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "AbsenceReason["+getUniqueId()+" "+getLabel()+"]";
	}

	public String toDebugString() {
		return "AbsenceReason[" +
			"\n	Label: " + getLabel() +
			"\n	Reference: " + getReference() +
			"\n	SortOrder: " + getSortOrder() +
			"\n	UniqueId: " + getUniqueId() +
			"]";
	}
}
