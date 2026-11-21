#!/bin/bash
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

# Entrypoint of the Tymora web image. Writes the database connection settings from the environment into a
# properties file that Tymora reads (-Dtmtbl.custom.properties), then starts Tomcat. Going through a file instead
# of -D options in JAVA_OPTS / CATALINA_OPTS keeps passwords with any characters intact: catalina.sh evals those.
#
#   DB_URL            JDBC URL, e.g. jdbc:mysql://db.example.org:3306/timetable?useSSL=true
#   DB_USER           database user
#   DB_PASSWORD       its password, or DB_PASSWORD_FILE: a file holding it (e.g. a Docker secret)
#   DB_WAIT_SECONDS   how long to wait for the database host and port before starting Tomcat (default 120)
# Unset settings are left to hibernate.cfg.xml.
#
# AI assistant, claude-cli backend (image built with INSTALL_CLAUDE_CLI=true):
#   CLAUDE_CODE_OAUTH_TOKEN_FILE   a mounted file holding a Claude Code token from `claude setup-token`; its content is
#                                  exported as CLAUDE_CODE_OAUTH_TOKEN for the CLI (never printed)
set -euo pipefail

PROPS=/usr/local/tomcat/conf/tymora-db.properties

# java.util.Properties value: backslash is the escape character, a leading space and line breaks must be escaped,
# and non-ASCII characters are written as \uXXXX (the file is read as ISO-8859-1).
escape() {
  local LC_ALL=C.UTF-8
  local s="$1" out="" ch code u c i
  for ((i = 0; i < ${#s}; i++)); do
    ch="${s:i:1}"
    printf -v code '%d' "'$ch"
    if ((code == 92)); then out+='\\'
    elif ((code == 10)); then out+='\n'
    elif ((code == 13)); then out+='\r'
    elif ((code == 9)); then out+='\t'
    elif ((code == 32 && i == 0)); then out+='\ '
    elif ((code < 128)); then out+="$ch"
    elif ((code < 0x10000)); then printf -v u '\\u%04x' "$code"; out+="$u"
    else c=$((code - 0x10000)); printf -v u '\\u%04x\\u%04x' $((0xD800 + (c >> 10))) $((0xDC00 + (c & 0x3FF))); out+="$u"
    fi
  done
  printf '%s' "$out"
}

password="${DB_PASSWORD:-}"
if [ -n "${DB_PASSWORD_FILE:-}" ]; then
  [ -r "$DB_PASSWORD_FILE" ] || { echo "tymora: DB_PASSWORD_FILE $DB_PASSWORD_FILE is not readable" >&2; exit 1; }
  password="$(cat "$DB_PASSWORD_FILE")"
fi

umask 077
{
  echo "# written by tymora-entrypoint.sh from DB_URL / DB_USER / DB_PASSWORD"
  [ -n "${DB_URL:-}" ] && echo "connection.url=$(escape "$DB_URL")"
  [ -n "${DB_USER:-}" ] && echo "connection.username=$(escape "$DB_USER")"
  [ -n "$password" ] && echo "connection.password=$(escape "$password")"
  true
} > "$PROPS"
export CATALINA_OPTS="${CATALINA_OPTS:-} -Dtmtbl.custom.properties=$PROPS"

# Claude Code login for the CLI from a mounted token file (read-only mount; nothing is written back).
if [ -n "${CLAUDE_CODE_OAUTH_TOKEN_FILE:-}" ]; then
  [ -r "$CLAUDE_CODE_OAUTH_TOKEN_FILE" ] || { echo "tymora: CLAUDE_CODE_OAUTH_TOKEN_FILE $CLAUDE_CODE_OAUTH_TOKEN_FILE is not readable" >&2; exit 1; }
  token="$(tr -d '[:space:]' < "$CLAUDE_CODE_OAUTH_TOKEN_FILE")"
  [ -n "$token" ] || { echo "tymora: CLAUDE_CODE_OAUTH_TOKEN_FILE $CLAUDE_CODE_OAUTH_TOKEN_FILE is empty" >&2; exit 1; }
  export CLAUDE_CODE_OAUTH_TOKEN="$token"
  unset token
  echo "tymora: Claude Code token loaded from $CLAUDE_CODE_OAUTH_TOKEN_FILE"
fi

# Tymora initialises once at startup: wait until the database port accepts connections.
if [ -n "${DB_URL:-}" ]; then
  hostport="$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:[a-z]+://([^/?]+).*#\1#')"
  host="${hostport%%:*}"; port="${hostport##*:}"
  [ "$port" = "$hostport" ] && port=3306
  wait="${DB_WAIT_SECONDS:-120}"
  echo "tymora: waiting up to ${wait}s for the database at ${host}:${port}"
  for ((i = 0; i < wait; i += 2)); do
    if (exec 3<>"/dev/tcp/${host}/${port}") 2>/dev/null; then echo "tymora: database reachable"; break; fi
    sleep 2
  done
fi

exec "$@"
