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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcException;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.OfferingModifyInterface.ConfigInfo;
import org.unitime.timetable.gwt.shared.OfferingModifyInterface.OfferingModifyRequest;
import org.unitime.timetable.gwt.shared.OfferingModifyInterface.OfferingModifyResponse;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.SchedulingSubpart;
import org.unitime.timetable.model.comparators.InstrOfferingConfigComparator;
import org.unitime.timetable.model.comparators.SchedulingSubpartComparator;
import org.unitime.timetable.model.dao.InstructionalOfferingDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

/**
 * Read backend for the migrated Modify Instructional Offering page (legacy
 * instructionalOfferingModify.action), scoped to configuration-level editing. Lists
 * the offering's configurations with their subpart structure. See
 * {@link org.unitime.timetable.gwt.shared.OfferingModifyInterface}; gated by
 * {@link Right#InstructionalOfferingDetail}. The companion {@code OfferingModifyUpdateBackend}
 * saves each configuration's name and limit.
 *
 * @author Angular migration
 */
@GwtRpcImplements(OfferingModifyRequest.class)
public class OfferingModifyBackend implements GwtRpcImplementation<OfferingModifyRequest, OfferingModifyResponse> {

	@Override
	public OfferingModifyResponse execute(OfferingModifyRequest request, SessionContext context) {
		Long offeringId = request.getOfferingId();
		if (offeringId == null)
			throw new GwtRpcException("No instructional offering was specified.");

		context.checkPermission(offeringId, "InstructionalOffering", Right.InstructionalOfferingDetail);

		InstructionalOffering io = InstructionalOfferingDAO.getInstance().get(offeringId);
		if (io == null)
			throw new GwtRpcException("Instructional offering " + offeringId + " was not found.");

		OfferingModifyResponse response = new OfferingModifyResponse();
		fill(response, io);
		return response;
	}

	/** Populate the response's read state from the offering (shared with the save bean). */
	static void fill(OfferingModifyResponse r, InstructionalOffering io) {
		r.setOfferingId(io.getUniqueId());
		r.setOfferingName(io.getCourseNameWithTitle());

		Long subjectAreaId = io.getControllingCourseOffering().getSubjectArea().getUniqueId();
		List<InstrOfferingConfig> configs = new ArrayList<InstrOfferingConfig>(io.getInstrOfferingConfigs());
		Collections.sort(configs, new InstrOfferingConfigComparator(subjectAreaId));
		for (InstrOfferingConfig config : configs) {
			ConfigInfo ci = new ConfigInfo();
			ci.setConfigId(config.getUniqueId());
			ci.setName(config.getName());
			ci.setUnlimited(Boolean.TRUE.equals(config.isUnlimitedEnrollment()));
			ci.setLimit(config.getLimit());

			List<SchedulingSubpart> subparts = new ArrayList<SchedulingSubpart>(config.getSchedulingSubparts());
			Collections.sort(subparts, new SchedulingSubpartComparator());
			for (SchedulingSubpart ss : subparts) {
				String type = ss.getItypeDesc() == null ? "" : ss.getItypeDesc().trim();
				int classes = ss.getClasses() == null ? 0 : ss.getClasses().size();
				ci.addSubpart(type + " (" + classes + " class" + (classes == 1 ? "" : "es") + ")");
			}
			r.addConfig(ci);
		}
	}
}
