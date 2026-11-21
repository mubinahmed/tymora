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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

/**
 * The assistant through the Claude Code CLI (`claude -p`), using the CLI's login on the server instead of an
 * API key. Every request is billed to that login, so this is for personal or local use only.
 *
 * Each call is an isolated, tool-less headless session in an empty temporary directory:
 *   --tools ""                 no built-in tools (no files, commands or web)
 *   --strict-mcp-config        no MCP servers
 *   --disable-slash-commands   chat text is never run as a skill or command
 *   --setting-sources project  no user settings, hooks, plugins or CLAUDE.md
 *   --no-session-persistence   nothing saved to the Claude Code session history
 *   --json-schema              the answer is structured: a reply or the tool calls to make
 * The CLI gets no application tools; it asks for them in its structured answer and this server runs them
 * (same tools, permissions and confirmations as the API backend), sending the results in the next call.
 * The whole transcript is sent on stdin each time.
 */
public class CliAssistantModel implements AssistantModel {
	private static Logger sLog = LogManager.getLogger(CliAssistantModel.class);
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Pattern AUTH = Pattern.compile("log ?in|auth|credential|401|403|not signed", Pattern.CASE_INSENSITIVE);

	static final String OUTPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{"
			+ "\"kind\":{\"type\":\"string\",\"enum\":[\"answer\",\"tool_calls\"]},"
			+ "\"text\":{\"type\":\"string\",\"description\":\"For answer: the reply to the user (Markdown). For tool_calls: optional short note shown to the user, may be empty.\"},"
			+ "\"calls\":{\"type\":\"array\",\"description\":\"For tool_calls: the tools to call now.\",\"items\":{\"type\":\"object\",\"properties\":{"
			+ "\"tool\":{\"type\":\"string\"},\"input\":{\"type\":\"object\"}},\"required\":[\"tool\",\"input\"],\"additionalProperties\":false}}},"
			+ "\"required\":[\"kind\",\"text\"],\"additionalProperties\":false}";

	static final String RESPONSE_RULES = "\n\n# How to answer (structured output)\n"
			+ "You cannot call tools directly. To use tools, answer with kind \"tool_calls\" and list them in \"calls\" (tool name and input "
			+ "object matching the tool's schema); the application runs them and sends the results back in <tool_result> tags in the "
			+ "next turn. You may list several independent calls at once. When you have what you need, answer with kind \"answer\" "
			+ "and the reply in \"text\". Use only the tools listed below.\n\n# Tools\n";

	private final AssistantSettings settings;

	public CliAssistantModel(AssistantSettings settings) { this.settings = settings; }

	@Override public String backend() { return "claude-cli"; }
	@Override public String model() { return settings.model; }

	static String toolCatalogue(List<AssistantTools.Tool> tools) {
		JsonArray arr = new JsonArray();
		for (AssistantTools.Tool t: tools) {
			JsonObject o = new JsonObject();
			o.addProperty("name", t.name);
			o.addProperty("description", t.description);
			Map<String, Object> schema = new LinkedHashMap<String, Object>();
			schema.put("type", "object"); schema.put("properties", t.properties); schema.put("required", t.required);
			o.add("input_schema", GSON.toJsonTree(schema));
			arr.add(o);
		}
		return GSON.toJson(arr);
	}

	private static class Item {
		final String role, kind, body, id, name;
		final boolean error;
		Item(String role, String kind, String body, String id, String name, boolean error) { this.role = role; this.kind = kind; this.body = body; this.id = id; this.name = name; this.error = error; }
	}

	/** Render the history as one tagged transcript (the CLI takes a single prompt). */
	static String render(List<Item> items) {
		StringBuilder sb = new StringBuilder("<conversation>\n");
		String role = null;
		for (Item i: items) {
			if (!i.role.equals(role)) { if (role != null) sb.append("</turn>\n"); sb.append("<turn role=\"").append(i.role).append("\">\n"); role = i.role; }
			switch (i.kind) {
			case "text": sb.append(i.body).append("\n"); break;
			case "call": sb.append("<tool_call id=\"").append(i.id).append("\" tool=\"").append(i.name).append("\">").append(i.body).append("</tool_call>\n"); break;
			case "result": sb.append("<tool_result id=\"").append(i.id).append("\"").append(i.error ? " error=\"true\"" : "").append(">\n").append(i.body).append("\n</tool_result>\n"); break;
			default: break;
			}
		}
		if (role != null) sb.append("</turn>\n");
		sb.append("</conversation>\n\nAnswer the last user turn of the conversation above, following the structured-output rules.");
		return sb.toString();
	}

	static List<String> command(String configured) {
		List<String> ret = new ArrayList<String>();
		for (String p: configured.trim().split("\\s+")) if (!p.isEmpty()) ret.add(p);
		return ret;
	}

	@Override
	public Conversation newConversation() {
		return new Conversation() {
			private final List<Item> items = new ArrayList<Item>();
			private int seq = 0;

			@Override
			public void addUser(List<ToolResult> results, String text) {
				if (results != null) for (ToolResult r: results) items.add(new Item("user", "result", r.content, r.id, null, r.error));
				if (text != null && !text.isEmpty()) items.add(new Item("user", "text", text, null, null, false));
			}

			@Override
			public Turn next(String system, List<AssistantTools.Tool> tools) {
				Path dir = null;
				try {
					dir = Files.createTempDirectory("tymora-assistant-");
					Path systemFile = dir.resolve("system.txt");
					Files.write(systemFile, (system + RESPONSE_RULES + toolCatalogue(tools)).getBytes(StandardCharsets.UTF_8));
					List<String> cmd = command(settings.cliCommand);
					cmd.addAll(Arrays.asList("-p", "--output-format", "json", "--model", settings.model, "--effort", settings.effort,
							"--system-prompt-file", systemFile.toString(), "--json-schema", OUTPUT_SCHEMA, "--tools", "",
							"--strict-mcp-config", "--disable-slash-commands", "--no-session-persistence",
							"--setting-sources", "project", "--permission-mode", "dontAsk"));
					ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
					pb.redirectErrorStream(false);
					Process p;
					try {
						p = pb.start();
					} catch (IOException e) {
						throw new AssistantException("The Claude Code CLI (\"" + cmd.get(0) + "\") could not be started on the server. Install Claude Code, sign in with \"claude\", or set " + AssistantSettings.CLI_COMMAND + ".", 503);
					}
					byte[] input = render(items).getBytes(StandardCharsets.UTF_8);
					ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
					Thread tOut = pump(p.getInputStream(), out, Integer.MAX_VALUE), tErr = pump(p.getErrorStream(), err, 20000);
					try (OutputStream os = p.getOutputStream()) { os.write(input); }
					if (!p.waitFor(settings.cliTimeoutSeconds, TimeUnit.SECONDS)) {
						p.destroyForcibly();
						throw new AssistantException("Claude Code did not answer within " + settings.cliTimeoutSeconds + " s.", 504);
					}
					tOut.join(5000); tErr.join(5000);
					String stdout = out.toString(StandardCharsets.UTF_8).trim(), stderr = err.toString(StandardCharsets.UTF_8).trim();
					JsonObject result = null;
					String[] lines = stdout.split("\n");
					try { result = JsonParser.parseString(lines[lines.length - 1]).getAsJsonObject(); } catch (Exception e) {}
					boolean isError = result == null || (result.has("is_error") && result.get("is_error").getAsBoolean()) || p.exitValue() != 0;
					if (isError) {
						String detail = result != null && result.has("result") && !result.get("result").isJsonNull() ? result.get("result").getAsString() : stderr;
						detail = detail == null ? "" : detail.split("\n")[0];
						if (detail.length() > 300) detail = detail.substring(0, 300);
						sLog.warn("Claude Code CLI failed (exit " + p.exitValue() + "): " + detail);
						if (AUTH.matcher(detail + " " + stderr).find())
							throw new AssistantException("Claude Code is not signed in on the server. Run \"claude\" once in a terminal of the server account and sign in, then retry.", 503);
						throw new AssistantException("Claude Code failed" + (detail.isEmpty() ? " (exit " + p.exitValue() + ")" : ": " + detail) + ".", 502);
					}
					return toTurn(result);
				} catch (IOException e) {
					throw new AssistantException("Claude Code could not be run: " + e.getMessage(), 502);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new AssistantException("The request was interrupted.", 503);
				} finally {
					if (dir != null) deleteQuietly(dir.toFile());
				}
			}

			private Turn toTurn(JsonObject result) {
				Turn turn = new Turn();
				JsonObject usage = result.has("usage") && result.get("usage").isJsonObject() ? result.getAsJsonObject("usage") : new JsonObject();
				turn.inputTokens = num(usage, "input_tokens") + num(usage, "cache_creation_input_tokens");
				turn.outputTokens = num(usage, "output_tokens");
				turn.cacheReadTokens = num(usage, "cache_read_input_tokens");
				if (result.has("modelUsage") && result.get("modelUsage").isJsonObject() && !result.getAsJsonObject("modelUsage").keySet().isEmpty())
					turn.servedBy = result.getAsJsonObject("modelUsage").keySet().iterator().next();
				else turn.servedBy = settings.model;
				JsonElement so = result.get("structured_output");
				if (so == null || !so.isJsonObject()) {
					// no structured answer: treat the plain result as the reply
					turn.text = result.has("result") && !result.get("result").isJsonNull() ? result.get("result").getAsString() : "";
					items.add(new Item("assistant", "text", turn.text, null, null, false));
					return turn;
				}
				JsonObject o = so.getAsJsonObject();
				turn.text = o.has("text") && !o.get("text").isJsonNull() ? o.get("text").getAsString().trim() : "";
				if (!turn.text.isEmpty()) items.add(new Item("assistant", "text", turn.text, null, null, false));
				if ("tool_calls".equals(o.has("kind") ? o.get("kind").getAsString() : "") && o.has("calls") && o.get("calls").isJsonArray()) {
					for (JsonElement e: o.getAsJsonArray("calls")) {
						if (!e.isJsonObject()) continue;
						JsonObject c = e.getAsJsonObject();
						String name = c.has("tool") ? c.get("tool").getAsString() : "";
						Map<String, Object> input = c.has("input") && c.get("input").isJsonObject()
								? GSON.fromJson(c.get("input"), new TypeToken<Map<String, Object>>() {}.getType()) : new LinkedHashMap<String, Object>();
						String id = "cli_" + (++seq);
						turn.calls.add(new ToolCall(id, name, input));
						items.add(new Item("assistant", "call", GSON.toJson(input), id, name, false));
					}
					if (!turn.calls.isEmpty()) turn.stop = "tool_use";
				}
				return turn;
			}
		};
	}

	static long num(JsonObject o, String key) {
		try { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0; } catch (Exception e) { return 0; }
	}

	private static Thread pump(InputStream in, ByteArrayOutputStream out, int max) {
		Thread t = new Thread(() -> {
			byte[] buf = new byte[8192];
			int n;
			try {
				while ((n = in.read(buf)) > 0) if (out.size() < max) out.write(buf, 0, Math.min(n, max - out.size()));
			} catch (IOException e) {}
		}, "assistant-cli-pump");
		t.setDaemon(true);
		t.start();
		return t;
	}

	private static void deleteQuietly(File f) {
		File[] children = f.listFiles();
		if (children != null) for (File c: children) deleteQuietly(c);
		f.delete();
	}
}
