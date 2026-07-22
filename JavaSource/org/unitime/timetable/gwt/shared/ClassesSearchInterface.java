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
 * Read-only Class Search (legacy classSearch.action) migrated to a GwtRpc
 * command bean. A single request drives both the subject-area picker and the
 * class listing:
 * <ul>
 *   <li>{@code subjectAreaId == null} -&gt; the backend returns the user's
 *       subject areas (rows: id = subject area id, cell[0] = label) so the
 *       Angular screen can populate its picker.</li>
 *   <li>{@code subjectAreaId != null} -&gt; the backend returns the controlling
 *       classes of that subject area (optionally filtered by course number).</li>
 * </ul>
 * The response reuses {@link SimpleListInterface.SimpleListResponse}'s
 * columns[] + rows[{id, cells[]}] shape. Additive: introduces no changes to
 * existing behavior.
 *
 * @author Angular migration
 */
public class ClassesSearchInterface implements IsSerializable {

	public static class ClassesSearchRequest implements GwtRpcRequest<SimpleListResponse> {
		private Long iSubjectAreaId;
		private String iCourseNbr;

		public ClassesSearchRequest() {}

		public Long getSubjectAreaId() { return iSubjectAreaId; }
		public void setSubjectAreaId(Long subjectAreaId) { iSubjectAreaId = subjectAreaId; }

		public String getCourseNbr() { return iCourseNbr; }
		public void setCourseNbr(String courseNbr) { iCourseNbr = courseNbr; }

		@Override
		public String toString() {
			return "ClassesSearch[" + (iSubjectAreaId == null ? "subjects" : iSubjectAreaId + (iCourseNbr == null || iCourseNbr.isEmpty() ? "" : " " + iCourseNbr)) + "]";
		}
	}
}
