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

import org.cpsolver.ifs.util.ToolBox;
import org.hibernate.Session;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.unitime.timetable.gwt.shared.SimpleEditInterface;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Field;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.FieldType;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Flag;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.PageName;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Record;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.ChangeLog.Operation;
import org.unitime.timetable.model.ChangeLog.Source;
import org.unitime.timetable.model.ItypeDesc;
import org.unitime.timetable.model.dao.ItypeDescDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Instructional Type descriptions (legacy itypeDescEdit.action) migrated to the generic
 * SimpleEdit admin table (page {@code /admin/itype}). Edit-only: the {@code itype} number is
 * the entity's primary key (read-only), so adding new types stays on the legacy screen; the
 * abbreviation, name, SIS reference, organized and basic flags are editable here.
 *
 * @author Angular migration
 */
@Service("gwtAdminTable[type=itype]")
public class Itypes implements AdminTable {

	@Override
	public PageName name() {
		return new PageName("Instructional Type", "Instructional Types");
	}

	@Override
	@PreAuthorize("checkPermission('InstructionalTypes')")
	public SimpleEditInterface load(SessionContext context, Session hibSession) {
		SimpleEditInterface data = new SimpleEditInterface(
				new Field("Type", FieldType.number, 80, 10, Flag.READ_ONLY),
				new Field("Abbreviation", FieldType.text, 160, 20, Flag.NOT_EMPTY),
				new Field("Name", FieldType.text, 300, 60, Flag.NOT_EMPTY),
				new Field("SIS Reference", FieldType.text, 160, 20),
				new Field("Organized", FieldType.toggle, 40),
				new Field("Basic", FieldType.toggle, 40));
		data.setSortBy(0);
		data.setAddable(false);
		for (ItypeDesc type : ItypeDescDAO.getInstance().findAll()) {
			Record r = data.addRecord(Long.valueOf(type.getItype()), context.hasPermission(type, Right.InstructionalTypeDelete));
			r.setField(0, type.getItype().toString(), false);
			r.setField(1, type.getAbbv());
			r.setField(2, type.getDesc());
			r.setField(3, type.getSis_ref());
			r.setField(4, Boolean.TRUE.equals(type.isOrganized()) ? "true" : "false");
			r.setField(5, Boolean.TRUE.equals(type.isBasic()) ? "true" : "false");
		}
		data.setEditable(context.hasPermission(Right.InstructionalTypeEdit));
		return data;
	}

	@Override
	@PreAuthorize("checkPermission('InstructionalTypeEdit')")
	public void save(SimpleEditInterface data, SessionContext context, Session hibSession) {
		for (ItypeDesc type : ItypeDescDAO.getInstance().findAll()) {
			Record r = data.getRecord(Long.valueOf(type.getItype()));
			if (r == null)
				delete(type, context, hibSession);
			else
				update(type, r, context, hibSession);
		}
	}

	@Override
	@PreAuthorize("checkPermission('InstructionalTypeAdd')")
	public void save(Record record, SessionContext context, Session hibSession) {
		// Not addable — the itype number is the primary key; adding stays on the legacy screen.
	}

	protected void update(ItypeDesc type, Record record, SessionContext context, Session hibSession) {
		if (type == null) return;
		if (ToolBox.equals(type.getAbbv(), record.getField(1)) &&
				ToolBox.equals(type.getDesc(), record.getField(2)) &&
				ToolBox.equals(type.getSis_ref(), record.getField(3)) &&
				ToolBox.equals(Boolean.TRUE.equals(type.isOrganized()), "true".equalsIgnoreCase(record.getField(4))) &&
				ToolBox.equals(Boolean.TRUE.equals(type.isBasic()), "true".equalsIgnoreCase(record.getField(5))))
			return;
		type.setAbbv(record.getField(1));
		type.setDesc(record.getField(2));
		type.setSis_ref(record.getField(3) == null || record.getField(3).isEmpty() ? null : record.getField(3));
		type.setOrganized("true".equalsIgnoreCase(record.getField(4)));
		type.setBasic("true".equalsIgnoreCase(record.getField(5)));
		hibSession.merge(type);
		ChangeLog.addChange(hibSession, context, type,
				type.getItype() + " " + type.getDesc(),
				Source.SIMPLE_EDIT, Operation.UPDATE, null, null);
	}

	@Override
	@PreAuthorize("checkPermission('InstructionalTypeEdit')")
	public void update(Record record, SessionContext context, Session hibSession) {
		update(ItypeDescDAO.getInstance().get(Integer.valueOf(record.getUniqueId().intValue())), record, context, hibSession);
	}

	protected void delete(ItypeDesc type, SessionContext context, Session hibSession) {
		if (type == null) return;
		ChangeLog.addChange(hibSession, context, type,
				type.getItype() + " " + type.getDesc(),
				Source.SIMPLE_EDIT, Operation.DELETE, null, null);
		hibSession.remove(type);
	}

	@Override
	@PreAuthorize("checkPermission('InstructionalTypeDelete')")
	public void delete(Record record, SessionContext context, Session hibSession) {
		delete(ItypeDescDAO.getInstance().get(Integer.valueOf(record.getUniqueId().intValue())), context, hibSession);
	}
}
