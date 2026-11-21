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
 * AI assistant (Claude) chat. The conversation, including tool calls and results, is kept on the server; the
 * browser sends the user's text and receives the new chat entries. Tools that change data stop the loop and
 * come back as a pending action the user confirms or declines. Public fields (REST bridge / Gson).
 */
public class AssistantInterface {

	public static class AssistantRequest implements GwtRpcRequest<AssistantResponse> {
		/** STATUS, SEND, CONFIRM, RESET */
		public String operation = "STATUS";
		public String conversationId;
		public String text;
		/** CONFIRM: true runs the pending action(s), false declines them */
		public Boolean approve;
		/** the page the user is on (route), given to Claude as context */
		public String page;
	}

	/** One line in the chat. */
	public static class Entry implements IsSerializable {
		/** user, assistant, tool (a lookup or action Claude ran), notice (declined, errors, limits) */
		public String role;
		public String text;
		/** tool: the tool name and a short description of what it did */
		public String tool, detail;
		/** tool: true when the tool failed */
		public boolean error;
		/** a page of the application Claude suggests opening (open_page tool) */
		public String linkPath, linkLabel;
		public Entry() {}
		public Entry(String role, String text) { this.role = role; this.text = text; }
	}

	/** A change Claude wants to make; runs only after the user confirms. */
	public static class PendingAction implements IsSerializable {
		public String tool, title, summary;
	}

	public static class AssistantResponse implements GwtRpcResponse {
		public boolean enabled;
		/** api or claude-cli (when enabled) */
		public String backend;
		public String model;
		/** why the assistant is not available */
		public String unavailableReason;
		public String conversationId;
		public List<Entry> entries = new ArrayList<Entry>();
		/** answer, confirm (pending actions), refused, truncated, limit (too many steps), error */
		public String status;
		public List<PendingAction> pending = new ArrayList<PendingAction>();
		/** model that answered (differs when the server-side fallback answered) */
		public String servedBy;
		public long inputTokens, outputTokens, cacheReadTokens;
		/** suggested first questions for this user (STATUS) */
		public List<String> suggestions = new ArrayList<String>();
	}
}
