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
package org.unitime.timetable.server.courses;

import org.hibernate.Transaction;
import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.OfferingModifyInterface.ConfigEdit;
import org.unitime.timetable.gwt.shared.OfferingModifyInterface.OfferingModifyResponse;
import org.unitime.timetable.gwt.shared.OfferingModifyInterface.OfferingModifyUpdateRequest;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.dao.InstrOfferingConfigDAO;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Save backend for the migrated Modify Instructional Offering page — persists ONLY
 * each configuration's name and limit (direct scalar setters). Adding/removing
 * configurations, subparts or classes stays on the existing GWT config editors.
 * Gated by {@link Right#InstrOfferingConfigEdit} per configuration, transactional,
 * and change-logged.
 *
 * @author Angular migration
 */
@GwtRpcImplements(OfferingModifyUpdateRequest.class)
public class OfferingModifyUpdateBackend implements GwtRpcImplementation<OfferingModifyUpdateRequest, OfferingModifyResponse> {

	@Override
	public OfferingModifyResponse execute(OfferingModifyUpdateRequest request, SessionContext context) {
		Long offeringId = request.getOfferingId();
		if (offeringId == null)
			throw new GwtRpcException("No instructional offering was specified.");

		InstructionalOfferingDAO dao = InstructionalOfferingDAO.getInstance();
		org.hibernate.Session hibSession = dao.getSession();
		InstructionalOffering io = dao.get(offeringId);
		if (io == null)
			throw new GwtRpcException("Instructional offering " + offeringId + " was not found.");

		Transaction tx = hibSession.beginTransaction();
		try {
			for (ConfigEdit edit : request.getConfigs()) {
				if (edit.getConfigId() == null) continue;
				InstrOfferingConfig config = InstrOfferingConfigDAO.getInstance().get(edit.getConfigId());
				if (config == null || config.getInstructionalOffering() == null
						|| !config.getInstructionalOffering().getUniqueId().equals(offeringId))
					throw new GwtRpcException("Configuration " + edit.getConfigId() + " does not belong to this offering.");

				context.checkPermission(edit.getConfigId(), "InstrOfferingConfig", Right.InstrOfferingConfigEdit);

				if (edit.getName() != null && !edit.getName().trim().isEmpty())
					config.setName(edit.getName().trim());
				if (!Boolean.TRUE.equals(config.isUnlimitedEnrollment()))
					config.setLimit(edit.getLimit());
				hibSession.merge(config);
			}

			ChangeLog.addChange(
					hibSession,
					context,
					io,
					ChangeLog.Source.INSTR_CFG_EDIT,
					ChangeLog.Operation.UPDATE,
					io.getControllingCourseOffering().getSubjectArea(),
					null);

			tx.commit();
		} catch (GwtRpcException e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw e;
		} catch (Exception e) {
			try { tx.rollback(); } catch (Exception x) {}
			throw new GwtRpcException("Failed to save configurations: " + e.getMessage(), e);
		}

		OfferingModifyResponse response = new OfferingModifyResponse();
		OfferingModifyBackend.fill(response, io);
		response.setSaved(true);
		return response;
	}
}
