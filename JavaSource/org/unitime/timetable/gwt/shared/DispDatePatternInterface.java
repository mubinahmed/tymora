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
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.List;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Display Date Pattern (legacy dispDatePattern.action) migrated to a GwtRpc command
 * bean. The request resolves a date pattern by its own id, or by a class or subpart
 * (their effective date pattern), or falls back to the session default — mirroring
 * {@code DispDatePatternAction.getDatePattern()}. The response carries the pattern
 * summary and the list of active dates so the UI can render the calendar. Additive.
 *
 * @author Angular migration
 */
public class DispDatePatternInterface implements IsSerializable {

	public static class DispDatePatternRequest implements GwtRpcRequest<DispDatePatternResponse> {
		private Long iDatePatternId;
		private Long iClassId;
		private Long iSubpartId;

		public DispDatePatternRequest() {}

		public Long getDatePatternId() { return iDatePatternId; }
		public void setDatePatternId(Long datePatternId) { iDatePatternId = datePatternId; }

		public Long getClassId() { return iClassId; }
		public void setClassId(Long classId) { iClassId = classId; }

		public Long getSubpartId() { return iSubpartId; }
		public void setSubpartId(Long subpartId) { iSubpartId = subpartId; }

		@Override
		public String toString() {
			return "DispDatePattern[" + (iDatePatternId != null ? "dp=" + iDatePatternId : iClassId != null ? "class=" + iClassId : "subpart=" + iSubpartId) + "]";
		}
	}

	public static class DispDatePatternResponse implements GwtRpcResponse {
		private Long iId;
		private String iName;
		private String iType;
		private Float iNumberOfWeeks;
		private String iStartDate;
		private String iEndDate;
		private List<String> iActiveDates = new ArrayList<String>();

		public DispDatePatternResponse() {}

		public Long getId() { return iId; }
		public void setId(Long id) { iId = id; }

		public String getName() { return iName; }
		public void setName(String name) { iName = name; }

		public String getType() { return iType; }
		public void setType(String type) { iType = type; }

		public Float getNumberOfWeeks() { return iNumberOfWeeks; }
		public void setNumberOfWeeks(Float numberOfWeeks) { iNumberOfWeeks = numberOfWeeks; }

		public String getStartDate() { return iStartDate; }
		public void setStartDate(String startDate) { iStartDate = startDate; }

		public String getEndDate() { return iEndDate; }
		public void setEndDate(String endDate) { iEndDate = endDate; }

		public List<String> getActiveDates() { return iActiveDates; }
		public void addActiveDate(String date) { iActiveDates.add(date); }
	}
}
