# Licensed to The Apereo Foundation under one or more contributor license
# agreements. See the NOTICE file distributed with this work for
# additional information regarding copyright ownership.
#
# The Apereo Foundation licenses this file to you under the Apache License,
# Version 2.0 (the "License"); you may not use this file except in
# compliance with the License. You may obtain a copy of the License at:
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#
# See the License for the specific language governing permissions and
# limitations under the License.

FROM tomcat:9-jre17
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.31/mysql-connector-j-8.0.31.jar /usr/local/tomcat/lib

# Optional: the Claude Code CLI for the AI assistant's claude-cli backend (docker-compose.claude-cli.yml sets
# INSTALL_CLAUDE_CLI=true). Installed with Anthropic's native installer, which verifies the download against the
# release manifest checksum; pinned to CLAUDE_CLI_VERSION (a version, or "stable" / "latest"). No credentials are
# baked in: the login is supplied at run time (a token file, see tymora-entrypoint.sh). Auto-update is off so the
# image stays as built; downloads are retried (temporary ~/.curlrc). Before the WAR, so rebuilding for a new WAR
# does not reinstall the CLI.
ARG INSTALL_CLAUDE_CLI=false
ARG CLAUDE_CLI_VERSION=2.1.329
ENV DISABLE_AUTOUPDATER=1
RUN if [ "$INSTALL_CLAUDE_CLI" = "true" ]; then \
        set -e; \
        printf 'retry = 5\nretry-delay = 3\nretry-all-errors\nconnect-timeout = 30\n' > /root/.curlrc; \
        curl -fsSL https://claude.ai/install.sh -o /tmp/claude-install.sh; \
        bash /tmp/claude-install.sh "$CLAUDE_CLI_VERSION"; \
        rm -rf /tmp/claude-install.sh /root/.curlrc /root/.claude /root/.claude.json; \
        ln -sf /root/.local/bin/claude /usr/local/bin/claude; \
        claude --version; \
    fi

# Database connection from DB_URL / DB_USER / DB_PASSWORD (or DB_PASSWORD_FILE), written to a properties file
# at start-up so passwords with any characters work (see tymora-entrypoint.sh). CR stripped: Windows checkouts.
COPY Documentation/Docker/tymora-entrypoint.sh /usr/local/bin/tymora-entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/tymora-entrypoint.sh && chmod 755 /usr/local/bin/tymora-entrypoint.sh

# Build context: the repository root (see docker-compose.yml); the WAR is the Maven build output.
COPY target/UniTime.war /usr/local/tomcat/webapps/ROOT.war
ENTRYPOINT ["/usr/local/bin/tymora-entrypoint.sh"]
CMD ["catalina.sh", "run"]
