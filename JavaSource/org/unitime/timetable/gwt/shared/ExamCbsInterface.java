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

import java.io.Serializable;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseList;
import org.unitime.timetable.gwt.shared.SuggestionsInterface.CBSNode;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * Shared DTO for the Angular Examination Conflict-Based Statistics screen
 * (migrates the legacy ecbs.action / ExamCbsAction). The examination solver's
 * conflict-based statistics ({@code ExamConflictStatisticsInfo}) produce the
 * same recursive tree shape as the course-timetabling CBS, so the response
 * reuses the existing {@link CBSNode} node (count / name / html / pref / nodes)
 * rather than introducing a parallel node type. Only the request is new (unique
 * simple name {@code ExamCbsRequest}), so the GwtRpc facade registers it
 * distinctly from {@code ConflictBasedStatisticsRequest}.
 *
 * READ-ONLY: the backend reads the in-memory examination solver. When no solver
 * is loaded (or it holds no conflicts) the backend returns an empty list so the
 * UI can render a "solver not loaded" banner.
 *
 * @author Angular migration
 */
public class ExamCbsInterface implements IsSerializable, Serializable {
	private static final long serialVersionUID = 1L;

	/** Variable-based view (exam -> period -> constraint -> assignment). */
	public static final int TYPE_VARIABLE_BASED = 0;
	/** Constraint-based view (constraint -> exam -> period -> assignment). */
	public static final int TYPE_CONSTRAINT_BASED = 1;

	public static class ExamCbsRequest implements GwtRpcRequest<GwtRpcResponseList<CBSNode>>, Serializable {
		private static final long serialVersionUID = 1L;
		private boolean iVariableOriented = false;
		private double iLimit = 100.0;

		public ExamCbsRequest() {}

		public boolean isVariableOriented() { return iVariableOriented; }
		public void setVariableOriented(boolean variableOriented) { iVariableOriented = variableOriented; }

		public double getLimit() { return iLimit; }
		public void setLimit(double limit) { iLimit = limit; }
	}
}
