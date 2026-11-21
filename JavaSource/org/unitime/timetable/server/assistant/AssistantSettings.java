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

import java.util.Arrays;
import java.util.List;

import org.unitime.timetable.model.ApplicationConfig;
import org.unitime.timetable.model.dao.ApplicationConfigDAO;

/**
 * Assistant settings. Each value is read on every request, in this order: JVM system property, the
 * application configuration table (editable on the Application Configuration page, takes effect at once),
 * then the default. The Anthropic credential is never stored as a setting: the SDK reads ANTHROPIC_API_KEY (or
 * ANTHROPIC_AUTH_TOKEN, or an `ant auth login` profile) from the server's environment, or the key is read from
 * the file named by tmtbl.assistant.api.keyFile (e.g. a mounted Docker / Kubernetes secret).
 */
public class AssistantSettings {
	public static final String BACKEND = "tmtbl.assistant.backend", MODEL = "tmtbl.assistant.model", EFFORT = "tmtbl.assistant.effort",
			CLI_COMMAND = "tmtbl.assistant.cli.command", CLI_TIMEOUT = "tmtbl.assistant.cli.timeout", BASE_URL = "tmtbl.assistant.api.baseUrl",
			MAX_STEPS = "tmtbl.assistant.maxSteps", KEY_FILE = "tmtbl.assistant.api.keyFile",
			RATE_LIMIT = "tmtbl.assistant.rateLimit";
	public static final String DEFAULT_MODEL = "claude-opus-5-5";
	static final List<String> EFFORTS = Arrays.asList("low", "medium", "high", "xhigh", "max");

	public String backend, model, effort, cliCommand, baseUrl, keyFile;
	public int cliTimeoutSeconds, maxSteps;
	/** messages / confirmations per user and minute */
	public int rateLimit;
	/** why the configured backend cannot be used (null when it can) */
	public String unavailable;

	static String value(String key, String def) {
		String v = System.getProperty(key);
		if (v != null && !v.trim().isEmpty()) return v.trim();
		try {
			ApplicationConfig c = ApplicationConfigDAO.getInstance().getSession().get(ApplicationConfig.class, key);
			if (c != null && c.getValue() != null && !c.getValue().trim().isEmpty()) return c.getValue().trim();
		} catch (Exception e) {}
		return def;
	}

	static int intValue(String key, int def) {
		try { return Integer.parseInt(value(key, String.valueOf(def))); } catch (NumberFormatException e) { return def; }
	}

	boolean hasApiCredential() {
		if (keyFile != null && !keyFile.isEmpty() && new java.io.File(keyFile).canRead()) return true;
		for (String env: new String[] {"ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN"}) {
			String v = System.getenv(env);
			if (v != null && !v.trim().isEmpty()) return true;
		}
		return false;
	}

	public static AssistantSettings load() {
		AssistantSettings s = new AssistantSettings();
		String b = value(BACKEND, "auto").toLowerCase();
		s.model = value(MODEL, DEFAULT_MODEL);
		s.effort = value(EFFORT, "medium").toLowerCase();
		if (!EFFORTS.contains(s.effort)) s.effort = "medium";
		s.cliCommand = value(CLI_COMMAND, "claude");
		s.cliTimeoutSeconds = Math.max(30, intValue(CLI_TIMEOUT, 300));
		s.baseUrl = value(BASE_URL, "");
		s.keyFile = value(KEY_FILE, "");
		s.maxSteps = Math.max(1, Math.min(20, intValue(MAX_STEPS, 8)));
		s.rateLimit = Math.max(1, intValue(RATE_LIMIT, 20));
		switch (b) {
		case "auto":
			if (s.hasApiCredential()) s.backend = "api";
			else { s.backend = "off"; s.unavailable = "The AI assistant is not configured: set ANTHROPIC_API_KEY on the server, or choose the claude-cli backend (" + BACKEND + ")."; }
			break;
		case "api": case "claude-cli":
			s.backend = b;
			break;
		default:
			s.backend = "off"; s.unavailable = "The AI assistant is turned off (" + BACKEND + ").";
		}
		return s;
	}

	public boolean enabled() { return unavailable == null; }

	/** identity of the backend configuration: a conversation started with another one is not continued */
	public String key() { return backend + "|" + model + "|" + effort + "|" + cliCommand + "|" + baseUrl + "|" + keyFile; }
}
