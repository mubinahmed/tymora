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
import org.unitime.timetable.model.HandoverNote;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.StaffAbsence;

/**
 * Hand-written to mirror the output of ant create-model (the model-generation
 * Ant task is not part of the Maven build). Keep field/column mappings in sync
 * with HandoverNote.hbm.xml.
 * @see org.unitime.commons.ant.CreateBaseModelFromXml
 */
@MappedSuperclass
public abstract class BaseHandoverNote implements Serializable {
	private static final long serialVersionUID = 1L;

	private Long iUniqueId;
	private String iTextNote;
	private String iLinkUrl;
	private String iLinkLabel;
	private String iAttachedName;
	private String iAttachedContentType;
	private Integer iAttachedSize;
	private byte[] iAttachedFile;
	private String iCreatedBy;
	private String iCreatedByName;
	private Date iTimeStamp;

	private Session iSession;
	private StaffAbsence iAbsence;

	public BaseHandoverNote() {
	}

	public BaseHandoverNote(Long uniqueId) {
		setUniqueId(uniqueId);
	}


	@Id
	@UniqueIdGenerator(sequence = "pref_group_seq")
	@Column(name="uniqueid")
	public Long getUniqueId() { return iUniqueId; }
	public void setUniqueId(Long uniqueId) { iUniqueId = uniqueId; }

	@Column(name = "text_note", nullable = true, length = 4000)
	public String getTextNote() { return iTextNote; }
	public void setTextNote(String textNote) { iTextNote = textNote; }

	@Column(name = "link_url", nullable = true, length = 500)
	public String getLinkUrl() { return iLinkUrl; }
	public void setLinkUrl(String linkUrl) { iLinkUrl = linkUrl; }

	@Column(name = "link_label", nullable = true, length = 200)
	public String getLinkLabel() { return iLinkLabel; }
	public void setLinkLabel(String linkLabel) { iLinkLabel = linkLabel; }

	@Column(name = "attached_name", nullable = true, length = 260)
	public String getAttachedName() { return iAttachedName; }
	public void setAttachedName(String attachedName) { iAttachedName = attachedName; }

	@Column(name = "attached_content_type", nullable = true, length = 100)
	public String getAttachedContentType() { return iAttachedContentType; }
	public void setAttachedContentType(String attachedContentType) { iAttachedContentType = attachedContentType; }

	@Column(name = "attached_size", nullable = true)
	public Integer getAttachedSize() { return iAttachedSize; }
	public void setAttachedSize(Integer attachedSize) { iAttachedSize = attachedSize; }

	@Lob
	@Column(name = "attached_file", nullable = true)
	public byte[] getAttachedFile() { return iAttachedFile; }
	public void setAttachedFile(byte[] attachedFile) { iAttachedFile = attachedFile; }

	@Column(name = "created_by", nullable = true, length = 40)
	public String getCreatedBy() { return iCreatedBy; }
	public void setCreatedBy(String createdBy) { iCreatedBy = createdBy; }

	@Column(name = "created_by_name", nullable = true, length = 100)
	public String getCreatedByName() { return iCreatedByName; }
	public void setCreatedByName(String createdByName) { iCreatedByName = createdByName; }

	@Column(name = "time_stamp", nullable = false)
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

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof HandoverNote)) return false;
		if (getUniqueId() == null || ((HandoverNote)o).getUniqueId() == null) return false;
		return getUniqueId().equals(((HandoverNote)o).getUniqueId());
	}

	@Override
	public int hashCode() {
		if (getUniqueId() == null) return super.hashCode();
		return getUniqueId().hashCode();
	}

	@Override
	public String toString() {
		return "HandoverNote["+getUniqueId()+"]";
	}

	public String toDebugString() {
		return "HandoverNote[" +
			"\n	AttachedContentType: " + getAttachedContentType() +
			"\n	AttachedName: " + getAttachedName() +
			"\n	AttachedSize: " + getAttachedSize() +
			"\n	CreatedBy: " + getCreatedBy() +
			"\n	CreatedByName: " + getCreatedByName() +
			"\n	LinkLabel: " + getLinkLabel() +
			"\n	LinkUrl: " + getLinkUrl() +
			"\n	TextNote: " + getTextNote() +
			"\n	TimeStamp: " + getTimeStamp() +
			"\n	UniqueId: " + getUniqueId() +
			"]";
	}
}
