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
package org.unitime.timetable.server.admin;

import java.text.DecimalFormat;

import org.cpsolver.ifs.util.ToolBox;
import org.hibernate.Session;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.SimpleEditInterface;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Field;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.FieldType;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Flag;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.PageName;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Record;
import org.unitime.timetable.model.AbsenceReason;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ChangeLog.Operation;
import org.unitime.timetable.model.ChangeLog.Source;
import org.unitime.timetable.model.dao.AbsenceReasonDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Admin CRUD for the {@link AbsenceReason} lookup, reusing the generic SimpleEdit
 * framework (the same table/edit UI as Position Types). Mirrors {@link PositionTypes}.
 */
@Service("gwtAdminTable[type=absenceReason]")
public class AbsenceReasons implements AdminTable {
	protected static final GwtMessages MESSAGES = Localization.create(GwtMessages.class);

	@Override
	public PageName name() {
		return new PageName("Absence Reason", "Absence Reasons");
	}

	@Override
	@PreAuthorize("checkPermission('AbsenceReasons')")
	public SimpleEditInterface load(SessionContext context, Session hibSession) {
		SimpleEditInterface data = new SimpleEditInterface(
				new Field(MESSAGES.fieldReference(), FieldType.text, 160, 20, Flag.UNIQUE),
				new Field(MESSAGES.fieldName(), FieldType.text, 300, 60, Flag.UNIQUE),
				new Field(MESSAGES.fieldSortOrder(), FieldType.number, 80, 10, Flag.UNIQUE)
				);
		data.setSortBy(2, 0, 1);
		DecimalFormat df = new DecimalFormat("0000");
		for (AbsenceReason reason: AbsenceReasonDAO.getInstance().findAll()) {
			int used = (hibSession.createQuery(
					"select count(a) from StaffAbsence a where a.reason.uniqueId = :uniqueId", Number.class)
					.setParameter("uniqueId", reason.getUniqueId()).uniqueResult()).intValue();
			Record r = data.addRecord(reason.getUniqueId(), used == 0);
			r.setField(0, reason.getReference());
			r.setField(1, reason.getLabel());
			r.setField(2, df.format(reason.getSortOrder() == null ? 0 : reason.getSortOrder()));
		}
		data.setEditable(context.hasPermission(Right.AbsenceReasonEdit));
		return data;
	}

	@Override
	@PreAuthorize("checkPermission('AbsenceReasonEdit')")
	public void save(SimpleEditInterface data, SessionContext context, Session hibSession) {
		for (AbsenceReason reason: AbsenceReasonDAO.getInstance().findAll()) {
			Record r = data.getRecord(reason.getUniqueId());
			if (r == null)
				delete(reason, context, hibSession);
			else
				update(reason, r, context, hibSession);
		}
		for (Record r: data.getNewRecords())
			save(r, context, hibSession);
	}

	@Override
	@PreAuthorize("checkPermission('AbsenceReasonEdit')")
	public void save(Record record, SessionContext context, Session hibSession) {
		AbsenceReason reason = new AbsenceReason();
		reason.setReference(record.getField(0));
		reason.setLabel(record.getField(1));
		reason.setSortOrder(Integer.valueOf(record.getField(2)));
		hibSession.persist(reason);
		record.setUniqueId(reason.getUniqueId());
		ChangeLog.addChange(hibSession, context, reason,
				reason.getReference() + " " + reason.getLabel(),
				Source.SIMPLE_EDIT, Operation.CREATE, null, null);
	}

	protected void update(AbsenceReason reason, Record record, SessionContext context, Session hibSession) {
		if (reason == null) return;
		DecimalFormat df = new DecimalFormat("0000");
		if (ToolBox.equals(reason.getReference(), record.getField(0)) &&
				ToolBox.equals(reason.getLabel(), record.getField(1)) &&
				ToolBox.equals(df.format(reason.getSortOrder() == null ? 0 : reason.getSortOrder()), record.getField(2))) return;
		reason.setReference(record.getField(0));
		reason.setLabel(record.getField(1));
		reason.setSortOrder(Integer.valueOf(record.getField(2)));
		hibSession.merge(reason);
		ChangeLog.addChange(hibSession, context, reason,
				reason.getReference() + " " + reason.getLabel(),
				Source.SIMPLE_EDIT, Operation.UPDATE, null, null);
	}

	@Override
	@PreAuthorize("checkPermission('AbsenceReasonEdit')")
	public void update(Record record, SessionContext context, Session hibSession) {
		update(AbsenceReasonDAO.getInstance().get(record.getUniqueId(), hibSession), record, context, hibSession);
	}

	protected void delete(AbsenceReason reason, SessionContext context, Session hibSession) {
		if (reason == null) return;
		ChangeLog.addChange(hibSession, context, reason,
				reason.getReference() + " " + reason.getLabel(),
				Source.SIMPLE_EDIT, Operation.DELETE, null, null);
		hibSession.remove(reason);
	}

	@Override
	@PreAuthorize("checkPermission('AbsenceReasonEdit')")
	public void delete(Record record, SessionContext context, Session hibSession) {
		delete(AbsenceReasonDAO.getInstance().get(record.getUniqueId(), hibSession), context, hibSession);
	}
}
