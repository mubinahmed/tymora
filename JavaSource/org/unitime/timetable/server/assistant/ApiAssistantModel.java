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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolResultBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;

/**
 * The assistant through the Anthropic API (Anthropic Java SDK). The credential comes from the server's
 * environment (ANTHROPIC_API_KEY, ANTHROPIC_AUTH_TOKEN or an `ant auth login` profile) or from the key file
 * (tmtbl.assistant.api.keyFile), and never leaves the server. Request settings: adaptive thinking, the configured effort, a cached system prompt, and server-side
 * refusal fallbacks (fallbacks "default") on the models that support them. The conversation keeps every
 * assistant turn exactly as returned (thinking blocks included), as the API requires for tool use.
 */
public class ApiAssistantModel implements AssistantModel {
	private static Logger sLog = LogManager.getLogger(ApiAssistantModel.class);
	static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
	private static AnthropicClient sClient;
	private static String sClientKey;

	private final AssistantSettings settings;
	private final AnthropicClient client;

	public ApiAssistantModel(AssistantSettings settings) {
		this.settings = settings;
		this.client = client(settings.baseUrl, settings.keyFile);
	}

	/** one client per configuration (the client holds connection pools); the key file is re-read when it changes */
	static synchronized AnthropicClient client(String baseUrl, String keyFile) {
		java.io.File kf = keyFile == null || keyFile.isEmpty() ? null : new java.io.File(keyFile);
		String key = (baseUrl == null ? "" : baseUrl) + "|" + (kf == null ? "" : kf.getPath() + "@" + kf.lastModified());
		if (sClient == null || !key.equals(sClientKey)) {
			AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder().fromEnv().timeout(Duration.ofMinutes(5)).maxRetries(2);
			if (baseUrl != null && !baseUrl.isEmpty()) b.baseUrl(baseUrl);
			if (kf != null && kf.canRead()) {
				try {
					b.apiKey(new String(java.nio.file.Files.readAllBytes(kf.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim());
				} catch (java.io.IOException e) {
					throw new AssistantException("The AI assistant's API key file could not be read.", 503);
				}
			}
			sClient = b.build();
			sClientKey = key;
		}
		return sClient;
	}

	@Override public String backend() { return "api"; }
	@Override public String model() { return settings.model; }

	/** server-side refusal fallbacks are offered for these models */
	static boolean supportsFallbacks(String model) {
		return model.startsWith("claude-opus-5-5") || model.startsWith("claude-fable-5") || model.startsWith("claude-sonnet-5-5") || model.equals("claude-opus-5");
	}

	static BetaTool toBetaTool(AssistantTools.Tool t) {
		BetaTool.InputSchema.Properties.Builder props = BetaTool.InputSchema.Properties.builder();
		for (Map.Entry<String, Object> e: t.properties.entrySet()) props.putAdditionalProperty(e.getKey(), JsonValue.from(e.getValue()));
		return BetaTool.builder().name(t.name).description(t.description)
				.inputSchema(BetaTool.InputSchema.builder().properties(props.build()).required(t.required).build()).build();
	}

	@Override
	public Conversation newConversation() {
		return new Conversation() {
			private final List<BetaMessageParam> messages = new ArrayList<BetaMessageParam>();
			/** tool_use ids of the last assistant turn: each needs a tool_result in the next user turn */
			private final List<String> openToolUses = new ArrayList<String>();
			/** user content not sent yet; kept until a call succeeds, so consecutive user turns merge into one */
			private final List<BetaContentBlockParam> userBlocks = new ArrayList<BetaContentBlockParam>();

			@Override
			public void addUser(List<ToolResult> results, String text) {
				List<BetaContentBlockParam> blocks = userBlocks;
				List<String> answered = new ArrayList<String>();
				if (results != null)
					for (ToolResult r: results) {
						blocks.add(BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder().toolUseId(r.id).content(r.content).isError(r.error).build()));
						answered.add(r.id);
					}
				for (String id: openToolUses) // e.g. a turn cut off at max_tokens: its tool calls were not run
					if (!answered.contains(id))
						blocks.add(BetaContentBlockParam.ofToolResult(BetaToolResultBlockParam.builder().toolUseId(id).content("Not run.").isError(true).build()));
				openToolUses.clear();
				if (text != null && !text.isEmpty()) blocks.add(BetaContentBlockParam.ofText(BetaTextBlockParam.builder().text(text).build()));
			}

			@Override
			public Turn next(String system, List<AssistantTools.Tool> tools) {
				MessageCreateParams.Builder b = MessageCreateParams.builder()
						.model(settings.model)
						.maxTokens(16000L)
						.systemOfBetaTextBlockParams(List.of(BetaTextBlockParam.builder().text(system).cacheControl(BetaCacheControlEphemeral.builder().build()).build()))
						.thinking(BetaThinkingConfigAdaptive.builder().build())
						.outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(settings.effort)).build())
						.messages(messages);
				BetaMessageParam user = userBlocks.isEmpty() ? null : BetaMessageParam.ofUserOfContentBlockParams(new ArrayList<BetaContentBlockParam>(userBlocks));
				if (user != null) b.addMessage(user);
				for (AssistantTools.Tool t: tools) b.addTool(toBetaTool(t));
				if (supportsFallbacks(settings.model)) {
					b.addBeta(FALLBACK_BETA);
					b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
				}
				BetaMessage response;
				try {
					response = client.beta().messages().create(b.build());
				} catch (NoCredentialsException e) {
					throw new AssistantException("The AI assistant has no Anthropic credential on the server (set ANTHROPIC_API_KEY).", 503);
				} catch (UnauthorizedException | PermissionDeniedException e) {
					sLog.warn("Anthropic API rejected the credential: " + e.getMessage());
					throw new AssistantException("The AI assistant is not configured correctly: the Anthropic API rejected the server's credential.", 503);
				} catch (RateLimitException e) {
					throw new AssistantException("The AI assistant is busy (rate limit). Please try again in a minute.", 429);
				} catch (BadRequestException e) {
					sLog.warn("Anthropic API rejected the request: " + e.getMessage());
					throw new AssistantException("The AI assistant could not process this request.", 502);
				} catch (InternalServerException e) {
					throw new AssistantException("The AI service is temporarily unavailable. Please try again.", 503);
				} catch (AnthropicServiceException e) {
					sLog.warn("Anthropic API error " + e.statusCode() + ": " + e.getMessage());
					throw new AssistantException("The AI service returned an error (" + e.statusCode() + ").", 502);
				} catch (AnthropicIoException e) {
					sLog.warn("Anthropic API not reachable: " + e.getMessage());
					throw new AssistantException("The AI service could not be reached from the server.", 503);
				}
				if (user != null) { messages.add(user); userBlocks.clear(); }
				messages.add(response.toParam());
				Turn turn = new Turn();
				StringBuilder text = new StringBuilder();
				for (BetaContentBlock block: response.content()) {
					block.text().ifPresent(t -> { if (text.length() > 0) text.append("\n\n"); text.append(t.text()); });
					block.toolUse().ifPresent(u -> {
						@SuppressWarnings("unchecked")
						Map<String, Object> input = u._input().convert(Map.class);
						turn.calls.add(new ToolCall(u.id(), u.name(), input));
						openToolUses.add(u.id());
					});
				}
				turn.text = text.toString().trim();
				BetaStopReason stop = response.stopReason().orElse(BetaStopReason.END_TURN);
				if (BetaStopReason.REFUSAL.equals(stop)) {
					turn.stop = "refusal";
					turn.refusal = response.stopDetails().flatMap(d -> d.explanation()).orElse(null);
					turn.calls.clear();
				} else if (BetaStopReason.MAX_TOKENS.equals(stop)) {
					turn.stop = "max_tokens";
					turn.calls.clear();
				} else if (BetaStopReason.TOOL_USE.equals(stop) || !turn.calls.isEmpty()) {
					turn.stop = "tool_use";
				}
				turn.servedBy = response.model().asString();
				turn.inputTokens = response.usage().inputTokens();
				turn.outputTokens = response.usage().outputTokens();
				turn.cacheReadTokens = response.usage().cacheReadInputTokens().orElse(0L);
				return turn;
			}
		};
	}
}
