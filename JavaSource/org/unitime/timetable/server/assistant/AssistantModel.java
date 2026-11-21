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
package org.unitime.timetable.server.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A way of talking to Claude: the Anthropic API (ApiAssistantModel, API key) or the Claude Code CLI
 * (CliAssistantModel, the CLI's login). Both keep their own form of the history and return the same Turn,
 * so the tool loop in AssistantService works the same for both.
 */
public interface AssistantModel {
	/** api or claude-cli */
	String backend();

	String model();

	Conversation newConversation();

	/** The history of one chat in the backend's own form. */
	interface Conversation {
		/** a user turn (text, plus the results of the previous turn's tool calls if there were any) */
		void addUser(List<ToolResult> results, String text);

		/** asks Claude for the next turn and appends it to the history */
		Turn next(String system, List<AssistantTools.Tool> tools);
	}

	class Turn {
		public String text = "";
		public List<ToolCall> calls = new ArrayList<ToolCall>();
		/** end_turn, tool_use, refusal, max_tokens */
		public String stop = "end_turn";
		public String refusal;
		public String servedBy;
		public long inputTokens, outputTokens, cacheReadTokens;
	}

	class ToolCall {
		public String id, name;
		public Map<String, Object> input;
		public ToolCall(String id, String name, Map<String, Object> input) { this.id = id; this.name = name; this.input = input; }
	}

	class ToolResult {
		public String id, content;
		public boolean error;
		public ToolResult(String id, String content, boolean error) { this.id = id; this.content = content; this.error = error; }
	}

	/** The backend failed; the message is shown to the user (no provider details), status mirrors HTTP. */
	class AssistantException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public final int status;
		public AssistantException(String message, int status) { super(message); this.status = status; }
	}
}
