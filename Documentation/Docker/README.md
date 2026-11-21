# Tymora with Docker Compose

Pick one approach and follow its steps. Each section is complete on its own.

| Approach | Database | AI assistant | Files used |
|---|---|---|---|
| [1. Demo database in a container](#approach-1-demo-database-in-a-container-default) (default) | MySQL container with the demo data | not configured | `docker-compose.yml` |
| [2. External MySQL database](#approach-2-external-mysql-database) | your MySQL server | not configured | `docker-compose.yml`, `docker-compose.external-db.yml` |
| [3. Demo database + Claude Code CLI](#approach-3-demo-database-and-ai-assistant-through-the-claude-code-cli) | MySQL container with the demo data | Claude Code login (personal use) | `docker-compose.yml`, `docker-compose.claude-cli.yml` |
| [4. External database + Claude Code CLI](#approach-4-external-database-and-ai-assistant-through-the-claude-code-cli) | your MySQL server | Claude Code login (personal use) | all three |

All approaches serve the Angular UI at <http://localhost:8000> and the legacy UI at <http://localhost:8888>.

## Approach 1: demo database in a container (default)

MySQL runs in the `unitime-db` container and is loaded with the demo data (`woebegon-data.sql`) on its first start.

1. **Build the WAR.** From the repository root:

   ```
   mvn -DskipTests package
   ```

   This writes `target/UniTime.war`, the file the web image is built from. `cpsolver` comes from `lib/maven-repo` inside the project; no other setup is needed.

   The build skips the GWT compile of the legacy UI, which the Angular UI doesn't use. To include the legacy pages that still need it, such as Roll Forward Session, build with `mvn -DskipTests -Dgwt.compiler.skip=false package` instead (slower).

2. **Go to the Docker folder:**

   ```
   cd Documentation/Docker
   ```

3. **Optional: choose the database password.** The default user is `timetable` with the password `unitime`. To use another password, set it **before the first start**, because MySQL creates the user only on an empty volume:

   ```
   export DB_PASSWORD='your-password'
   ```

4. **Build and start:**

   ```
   docker compose up -d --build
   ```

5. **Wait for the first start to finish** (about 2-3 minutes: the demo data is loaded, then Tymora upgrades the schema):

   ```
   docker logs -f unitime-web        # ready when "Server startup in" appears; Ctrl+C to stop following
   ```

6. **Open** <http://localhost:8000> and sign in, e.g. as `admin` / `admin` (see [Demo users](#demo-users)).

7. **Stop or reset:**

   ```
   docker compose down               # stop; the database is kept
   docker compose down -v            # stop and DELETE the database and data volumes (next start reloads the demo data)
   ```

## Approach 2: external MySQL database

Tymora uses your MySQL server; the `unitime-db` container is not started.

1. **Build the WAR.** From the repository root:

   ```
   mvn -DskipTests package
   ```

   The build skips the GWT compile of the legacy UI, which the Angular UI doesn't use. To include the legacy pages that still need it, such as Roll Forward Session, build with `mvn -DskipTests -Dgwt.compiler.skip=false package` instead (slower).

2. **Prepare the database.**

   **New database:** run as a MySQL administrator, from `Documentation/Docker`:

   ```
   mysql -u root -p < ../Database/MySQL/schema.sql                    # DROPS and recreates the database `timetable`
   mysql -u root -p timetable < ../Database/MySQL/blank-data.sql      # or woebegon-data.sql for the demo data
   mysql -u root -p -e "CREATE USER 'timetable'@'%' IDENTIFIED BY 'your-password'; GRANT ALL ON timetable.* TO 'timetable'@'%';"
   ```

   - **Warning:** `schema.sql` deletes any existing database named `timetable`.
   - **Why the `'%'` user:** `schema.sql` creates its user only for `localhost`, which a container cannot use. You can restrict `'%'` to the Docker host or network.

   **Existing Tymora / UniTime database:** back it up. Tymora upgrades its schema on the first start.

3. **Make the server reachable from containers.**
   - **Remote server:** use its host name.
   - **MySQL on the Docker host itself:** use `host.docker.internal`. MySQL must listen on an address containers can reach, not only `127.0.0.1`.

4. **Go to the Docker folder:**

   ```
   cd Documentation/Docker
   ```

5. **Create the settings file:**

   ```
   cp external-db.env.example external-db.env
   ```

   Edit `external-db.env`. It is git-ignored, so the password stays out of the repository.
   - **`DB_URL`:** e.g. `jdbc:mysql://db.example.org:3306/timetable?useSSL=true`.
   - **`DB_USER`:** the database user.
   - **`DB_PASSWORD`:** may contain any characters. Alternatively, mount a secret file into `unitime-web` and set `DB_PASSWORD_FILE` to its path.

6. **Build and start:**

   ```
   docker compose --env-file external-db.env -f docker-compose.yml -f docker-compose.external-db.yml up -d --build
   ```

7. **Check the start:**

   ```
   docker logs -f unitime-web        # "tymora: database reachable", then "Server startup in"
   ```

   Tymora waits up to `DB_WAIT_SECONDS` (default 120) for the database host and port before starting.

8. **Open** <http://localhost:8000> and sign in with an account of your database.

9. **Stop** (use the same files; your database is not touched):

   ```
   docker compose --env-file external-db.env -f docker-compose.yml -f docker-compose.external-db.yml down
   ```

## Approach 3: demo database and AI assistant through the Claude Code CLI

Like approach 1, plus the AI assistant (header sparkles button) answering through the Claude Code CLI. The CLI is installed in the web image at build time, and only a login token is mounted from the host, read-only.
- **Personal or local use only.** Every assistant question uses the Claude account of the token.

1. **Build the WAR.** From the repository root:

   ```
   mvn -DskipTests package
   ```

   The build skips the GWT compile of the legacy UI, which the Angular UI doesn't use. To include the legacy pages that still need it, such as Roll Forward Session, build with `mvn -DskipTests -Dgwt.compiler.skip=false package` instead (slower).

2. **Create a Claude Code token** (once). On any machine with Claude Code installed; this needs a Claude subscription and opens a browser to sign in:

   ```
   claude setup-token
   ```

3. **Save the printed token in a file** that only you can read:

   ```
   mkdir -p ~/.config/tymora
   nano ~/.config/tymora/claude-token        # paste the token, save
   chmod 600 ~/.config/tymora/claude-token
   ```

4. **Point Docker Compose at the file:**

   ```
   export CLAUDE_TOKEN_FILE=~/.config/tymora/claude-token
   ```

5. **Go to the Docker folder:**

   ```
   cd Documentation/Docker
   ```

6. **Build and start.** The first build downloads and installs the CLI (about 380 MB):

   ```
   docker compose -f docker-compose.yml -f docker-compose.claude-cli.yml up -d --build
   ```

7. **Wait for the first start** (about 2-3 minutes):

   ```
   docker logs -f unitime-web        # "tymora: Claude Code token loaded ...", then "Server startup in"
   ```

8. **Check the CLI login** (no model call):

   ```
   docker exec unitime-web sh -c 'CLAUDE_CODE_OAUTH_TOKEN="$(tr -d "[:space:]" < /run/secrets/claude-code-token)" claude auth status'   # "loggedIn": true, "authMethod": "oauth_token"
   ```

9. **Open** <http://localhost:8000>, sign in as `admin` / `admin`, and select the sparkles button in the header. The panel header shows "Claude Code".

10. **Stop or reset:**

    ```
    docker compose -f docker-compose.yml -f docker-compose.claude-cli.yml down        # add -v to DELETE the database
    ```

## Approach 4: external database and AI assistant through the Claude Code CLI

Like approach 2, plus the AI assistant through the Claude Code CLI, installed in the image; only a login token is mounted from the host.
- **Personal or local use only.** Every assistant question uses the Claude account of the token.

1. **Build the WAR.** From the repository root:

   ```
   mvn -DskipTests package
   ```

   The build skips the GWT compile of the legacy UI, which the Angular UI doesn't use. To include the legacy pages that still need it, such as Roll Forward Session, build with `mvn -DskipTests -Dgwt.compiler.skip=false package` instead (slower).

2. **Prepare the database.**

   **New database:** run as a MySQL administrator, from `Documentation/Docker`:

   ```
   mysql -u root -p < ../Database/MySQL/schema.sql                    # DROPS and recreates the database `timetable`
   mysql -u root -p timetable < ../Database/MySQL/blank-data.sql      # or woebegon-data.sql for the demo data
   mysql -u root -p -e "CREATE USER 'timetable'@'%' IDENTIFIED BY 'your-password'; GRANT ALL ON timetable.* TO 'timetable'@'%';"
   ```

   **Existing Tymora / UniTime database:** back it up. Tymora upgrades its schema on the first start.

3. **Make the server reachable from containers.**
   - **Remote server:** use its host name.
   - **MySQL on the Docker host itself:** use `host.docker.internal`. MySQL must listen on an address containers can reach, not only `127.0.0.1`.

4. **Create a Claude Code token** (once). This needs a Claude subscription:

   ```
   claude setup-token
   ```

5. **Save the token in a file** that only you can read, and point Docker Compose at it:

   ```
   mkdir -p ~/.config/tymora
   nano ~/.config/tymora/claude-token        # paste the token, save
   chmod 600 ~/.config/tymora/claude-token
   export CLAUDE_TOKEN_FILE=~/.config/tymora/claude-token
   ```

6. **Go to the Docker folder and create the database settings file:**

   ```
   cd Documentation/Docker
   cp external-db.env.example external-db.env        # edit DB_URL, DB_USER, DB_PASSWORD (git-ignored)
   ```

7. **Build and start:**

   ```
   docker compose --env-file external-db.env -f docker-compose.yml -f docker-compose.external-db.yml -f docker-compose.claude-cli.yml up -d --build
   ```

8. **Check the start and the CLI login:**

   ```
   docker logs -f unitime-web                         # "database reachable", "token loaded", then "Server startup in"
   docker exec unitime-web sh -c 'CLAUDE_CODE_OAUTH_TOKEN="$(tr -d "[:space:]" < /run/secrets/claude-code-token)" claude auth status'   # "loggedIn": true, "authMethod": "oauth_token"
   ```

9. **Open** <http://localhost:8000>, sign in, and select the sparkles button in the header.

10. **Stop** (use the same files; your database is not touched):

    ```
    docker compose --env-file external-db.env -f docker-compose.yml -f docker-compose.external-db.yml -f docker-compose.claude-cli.yml down
    ```

## Settings reference

| Setting | Used by | Default | Meaning |
|---|---|---|---|
| `DB_URL` | all | the `unitime-db` container | JDBC URL of the database (required in approaches 2 and 4) |
| `DB_USER` | all | `timetable` | Database user (required in approaches 2 and 4) |
| `DB_PASSWORD` | all | `unitime` (approaches 1 and 3 only) | Database password; any characters |
| `DB_PASSWORD_FILE` | 2, 4 | (none) | Path, inside the container, of a mounted file holding the password; used instead of `DB_PASSWORD` |
| `DB_WAIT_SECONDS` | 2, 4 | 120 | How long Tymora waits for the database host and port before starting |
| `CLAUDE_TOKEN_FILE` | 3, 4 | (required) | Host file holding the token from `claude setup-token`; mounted read-only. If the file doesn't exist, `up` stops with "bind source path does not exist": create the file first (Docker is told not to create a directory in its place) |
| `CLAUDE_CLI_VERSION` | 3, 4 | 2.1.329 | Claude Code version installed in the image (a version, `stable` or `latest`); rebuild with `--build` after changing it |

- **How the settings are passed:** the database settings are written into a properties file when the container starts, not passed on the Java command line, so passwords with special characters work.
- **Token handling:** the Claude token is handed to the CLI as `CLAUDE_CODE_OAUTH_TOKEN` and never printed.
- **Alternative to the token file:** mount the CLI's credentials file, `~/.claude/.credentials.json`; it's commented out in `docker-compose.claude-cli.yml`. This works on Linux / WSL only. The token file is preferred, because the credentials file must be writable and host and container would share one rotating login.
- **AI assistant with an API key:** for a shared server, the assistant should use an Anthropic API key rather than a personal login. These compose files do not pass one in yet; see `analysis/08-assistant/README.md`.

## Demo users

You can enter the demo as one of the following users. In every case the **password is same as the user name**.

| User | Role | What they can do |
|---|---|---|
| `guest` | View-only | Can see all course data (for all departments) and all committed timetabling solutions, but cannot change anything. |
| `dept` | Schedule manager | Can edit course data of their department and work with the appropriate timetabling solutions. Classes that are externally managed (e.g. large-lecture-room classes timetabled centrally by another department) are only editable by this user during the **input data entry** phase; afterwards ownership transfers to the appropriate manager. In the current example, all subject areas are managed by the same department. |
| `llr` | Large lecture room manager | Can edit all classes marked as large-lecture-room classes and work with large-lecture-room timetables. |
| `exam` | Examination manager | Can edit all examination data and solutions. |
| `event` | Event manager | Can edit, approve, or reject all special and course events. |
| `admin` | Administrator | Can edit all course data and timetabling solutions. Many administrative pages are also available only to this role. |
| `doe`, `newman`, `smith` | Instructors | Associated with instructors in the system. Can see their personalized instructor schedules and existing events, and request a new (special) event (which may need approval by an event manager or administrator). |
| `student` | Student | Associated with a student in the system. Can see their personalized student schedule and existing events, and request a new (special) event (which may need approval by an event manager or administrator). |

## Notes

- This is a simple installation with one web server and no dedicated solver server.
- The image built by default has no legacy GWT UI. The Angular UI covers the menu except a few admin pages (Roll Forward Session, Statistics, Test HQL); build the WAR with `-Dgwt.compiler.skip=false` if you need them.
- The opt-in GWT build compiles one permutation (all languages and browsers in one script). It took about 4 minutes and peaked at 3.5 GB of memory on an 8 GB machine. It uses at most `gwt.localWorkers` (default 2) worker JVMs with `gwt.jvmArgs` (default `-Xmx2g`); raise them on a build server, e.g. `-Dgwt.localWorkers=8`.
- Tomcat (Tymora), the Angular UI (nginx) and, in approaches 1 and 3, MySQL run in separate containers.
- No HTTPS is included; a reverse proxy can provide the TLS layer.
