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

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.shared.SimpleListInterface.SimpleListResponse;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Session-wide "Last Changes" (Change Log) admin report. Distinct from
 * {@link LastChangesInterface} (which is the per-object change-log widget keyed
 * by objectType + objectId): this request lists the most recent ChangeLog
 * entries for the current academic session. Read-only. Reuses the generic
 * {@link SimpleListResponse} table shape so the Angular table renderer is shared.
 *
 * The request simple name ({@code SessionChangeLogRequest}) is unique across the
 * command surface so the GwtRpc facade can auto-register it by simple name.
 *
 * @author Angular migration
 */
public class SessionChangeLogInterface implements IsSerializable {

	public static class SessionChangeLogRequest implements GwtRpcRequest<SimpleListResponse> {
		private Integer iLimit;

		public SessionChangeLogRequest() {}
		public SessionChangeLogRequest(Integer limit) { iLimit = limit; }

		/** Recent-N cap. Null/&lt;=0 falls back to the backend default. */
		public Integer getLimit() { return iLimit; }
		public void setLimit(Integer limit) { iLimit = limit; }

		@Override
		public String toString() { return "SessionChangeLog[limit=" + iLimit + "]"; }
	}
}
