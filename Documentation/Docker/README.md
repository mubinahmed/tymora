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

## Deploying on a server that already runs nginx

Use this when the server already has an nginx serving other applications on ports 80 and 443. The setup on the server is **approach 4**: external MySQL database plus the AI assistant through the Claude Code CLI. The server doesn't build anything and doesn't use Docker Compose. It pulls the two images built with approach 4 from Amazon ECR and starts them with plain `docker run` commands. The same images are in a public and a private repository; use either:

| Service | Public ECR (no login) | Private ECR (AWS login) |
|---|---|---|
| `unitime-web` (Tomcat, with the Claude Code CLI installed) | `public.ecr.aws/p3y5w7c0/lms/unitime-web` | `050752618487.dkr.ecr.ap-south-1.amazonaws.com/lms/unitime-be` |
| `unitime-ui` (nginx: Angular UI, proxies the backend paths) | `public.ecr.aws/p3y5w7c0/lms/unitime-ui` | `050752618487.dkr.ecr.ap-south-1.amazonaws.com/lms/unitime-ui` |

Note the different name of the web repository: `unitime-web` in the public registry, `unitime-be` in the private one. The commands below use two shell variables, `WEB_IMAGE` and `UI_IMAGE`, set in step 4 for the registry you choose, so the rest is the same for both.

- **Public:** anyone can pull, without an AWS account or login. Simplest for the server. Anonymous pulls are rate-limited per IP address (about one per second, and a monthly data allowance), which is ample for one server.
- **Private:** pulling needs AWS credentials with read rights on the repositories, and a login that lasts 12 hours. Use it when the images must not be public.

The images hold no passwords or tokens: the database settings and the Claude token are given to the container when it starts (step 2). Keep it that way, because anyone can download and inspect a public image.

The existing nginx stays in charge of ports 80 and 443 and of HTTPS, and forwards one hostname to Tymora. The containers listen only on addresses inside the server (loopback, and the Docker bridge), so they can't be reached from outside except through the existing nginx.

```
browser ──https──> existing nginx (:443) ──> 127.0.0.1:8000   unitime-ui
                                                                 │ "unitime-web" = 172.17.0.1 (Docker's default bridge gateway)
                                             172.17.0.1:8080 <───┘ unitime-web ──> 172.17.0.1:3306 MySQL (system service)
                                             127.0.0.1:8888   unitime-web (legacy UI; server-local only)
```

**Give Tymora its own hostname**, for example `tymora.example.edu`. The Angular UI is built with `<base href="/">`, and the backend paths it calls (`/api/`, `/login`, `*.action`, ...) are at the root, so serving it under a sub-path of another site (`https://example.edu/tymora/`) does not work without changing the build.

### On the build machine: push the images

**Public ECR, with the script:** `build-and-push.sh` in this folder builds the WAR and both images (the web image with the Claude Code CLI, as in approach 4) and pushes them to `public.ecr.aws/p3y5w7c0/lms`, with a release tag and `latest`. The two images build in parallel in the background; each is pushed as soon as its own build is done, and the script ends when both are pushed (output prefixed `[web]` / `[ui]`, a log file per image):

```
Documentation/Docker/build-and-push.sh                    # from the repository root; tag = yyyymmdd-hhmm
Documentation/Docker/build-and-push.sh --tag 2026-11-24   # a tag of your choice
Documentation/Docker/build-and-push.sh --only ui          # one image only (web or ui); --skip-war reuses target/UniTime.war
Documentation/Docker/build-and-push.sh --dry-run          # show the commands without running them; --help for all options
```

It needs Docker, the AWS CLI v2 with push rights on the two public repositories (profile `ifas-sit` by default; `ECR_PROFILE=other-profile` selects another, `ECR_PROFILE=` the default credentials), Maven and JDK 17. It also tags the images `docker-unitime-web` / `docker-unitime-ui`, so the local Docker Compose setup runs the same build. The steps below do the same by hand, and cover the private registry.

Build as in approach 4 (WAR, then `docker compose ... up -d --build` or `docker compose ... build`); Docker Compose is used only here, on the build machine. Compose names the images `docker-unitime-web` and `docker-unitime-ui`. Tag and push them to the registry the server uses, or to both. The AWS user needs push rights on the repositories.

```
TAG=$(date +%Y%m%d-%H%M)                       # a fixed tag per release, besides latest
```

**Public ECR.** Logging in for push always uses the region `us-east-1`, wherever the repositories are:

```
PUB=public.ecr.aws/p3y5w7c0/lms
aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws

docker tag docker-unitime-web:latest $PUB/unitime-web:$TAG
docker tag docker-unitime-web:latest $PUB/unitime-web:latest
docker tag docker-unitime-ui:latest  $PUB/unitime-ui:$TAG
docker tag docker-unitime-ui:latest  $PUB/unitime-ui:latest
docker push --all-tags $PUB/unitime-web
docker push --all-tags $PUB/unitime-ui
```

**Private ECR:**

```
PRIV=050752618487.dkr.ecr.ap-south-1.amazonaws.com/lms
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin ${PRIV%%/*}

docker tag docker-unitime-web:latest $PRIV/unitime-be:$TAG
docker tag docker-unitime-web:latest $PRIV/unitime-be:latest
docker tag docker-unitime-ui:latest  $PRIV/unitime-ui:$TAG
docker tag docker-unitime-ui:latest  $PRIV/unitime-ui:latest
docker push --all-tags $PRIV/unitime-be
docker push --all-tags $PRIV/unitime-ui
```

Things to know about the images:
- **They are `amd64`.** The server must be x86-64 too. An ARM server (e.g. AWS Graviton) fails with `exec format error`. Check with `docker image inspect --format '{{.Architecture}}' docker-unitime-web`.
- **The Claude Code CLI is inside the web image**, installed at build time (`CLAUDE_CLI_VERSION`, default 2.1.329). The login token is not: it stays a file on the server (step 2). Check with `docker run --rm --entrypoint claude $WEB_IMAGE:latest --version` (variables from step 4).
- **The UI image has no server-specific settings.** Its nginx sends backend calls to `unitime-web:8080`. On the server, that name points at the server itself through Docker's default bridge, where the web container's port 8080 is published (steps 5 and 6). No Docker network needs to be created.

### On the server

The server needs Docker only: no Docker Compose, no repository, no compose files. Everything below runs from one folder, e.g. `/opt/tymora`.

1. **Prepare the server.**
   - Docker (any current version).
   - **Private ECR only:** the AWS CLI, and rights to pull from both repositories: `ecr:GetAuthorizationToken`, `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer`. On EC2, give these to the instance role instead of storing access keys on the server. Public ECR needs neither.
   - MySQL running on the server as a system service, with the database prepared as in approach 4, step 2. Step 3 lets the container reach it.

2. **Create the settings and the token file:**

   ```
   sudo install -d -m 700 /opt/tymora /opt/tymora/secrets
   cd /opt/tymora
   sudo nano tymora-web.env                 # contents below
   sudo nano secrets/claude-token           # paste the token from `claude setup-token` (approach 4, step 4), save
   sudo chmod 600 tymora-web.env secrets/claude-token
   ```

   `tymora-web.env`, the settings of the web container (one `NAME=value` per line, **no quotes**: `docker run --env-file` takes every character after `=` literally):

   ```
   # Database: MySQL on this server (system service); host.docker.internal is mapped to the server in step 5.
   DB_URL=jdbc:mysql://host.docker.internal:3306/timetable?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf-8
   DB_USER=timetable
   DB_PASSWORD=your-password
   # Seconds to wait for the database host and port before Tymora starts.
   DB_WAIT_SECONDS=120
   # AI assistant through the Claude Code CLI (what docker-compose.claude-cli.yml sets in approach 4).
   CATALINA_OPTS=-Dtmtbl.assistant.backend=claude-cli
   CLAUDE_CODE_OAUTH_TOKEN_FILE=/run/secrets/claude-code-token
   ```

   Every assistant question uses the Claude account of this token. Approach 4 is meant for personal use, so check that this fits how the server will be used.

3. **Let the containers reach MySQL** (once). The containers run on Docker's default bridge network (`172.17.0.0/16`). From a container, the server is the bridge gateway `172.17.0.1` (`host-gateway` in step 5), not `127.0.0.1`, which is the container itself. Check the addresses on your server; they differ only if Docker's configuration changed them:

   ```
   docker network inspect bridge --format '{{(index .IPAM.Config 0).Subnet}} {{(index .IPAM.Config 0).Gateway}}'   # 172.17.0.0/16 172.17.0.1
   ```

   1. **Where MySQL listens.** Check with `sudo ss -ltnp | grep mysqld`. `[::]:3306`, `0.0.0.0:3306` or `*:3306` means all addresses, including `172.17.0.1`: nothing to change. Only `127.0.0.1:3306` means local connections only. In that case set `bind-address = 0.0.0.0` in the `[mysqld]` section of MySQL's configuration and restart it (`sudo systemctl restart mysql`, `mysqld` or `mariadb`). Don't bind to `172.17.0.1`: that address exists only once Docker has started, so MySQL would fail to start at boot.

   2. **Allow the `timetable` user from the containers.** A user created for `'localhost'` only won't be accepted from a container. Check, as a MySQL administrator (read-only):

      ```
      sudo mysql -e "SELECT user, host FROM mysql.user WHERE user = 'timetable';"
      ```

      If no row has host `%` or `172.17.%`, add one with the password in `tymora-web.env`:

      ```
      sudo mysql -e "CREATE USER 'timetable'@'172.17.%' IDENTIFIED BY 'your-password'; GRANT ALL ON timetable.* TO 'timetable'@'172.17.%';"
      ```

   3. **Firewall.** Containers connect to MySQL as incoming traffic to the server, from `172.17.0.0/16`. If the server has a firewall, allow that, and nothing else, to port 3306:

      ```
      sudo ufw allow from 172.17.0.0/16 to any port 3306 proto tcp       # ufw (Ubuntu)
      sudo firewall-cmd --permanent --zone=trusted --add-source=172.17.0.0/16 && sudo firewall-cmd --reload   # firewalld (RHEL-type)
      ```

      MySQL listening on all addresses means ports 3306 and 33060 (X Protocol) must not be open to the internet. Check the firewall (`sudo ufw status`) and, on a cloud server, its security group or network firewall.

   4. **Test from a container** (after pulling the image in step 4):

      ```
      docker run --rm --add-host host.docker.internal:host-gateway --entrypoint bash \
          $WEB_IMAGE:latest -c 'timeout 5 bash -c "echo > /dev/tcp/host.docker.internal/3306" && echo "MySQL reachable"'
      ```

      If nothing is printed, re-check 1 and 3. A wrong user, host or password shows later, in `docker logs unitime-web` (step 7).

4. **Pull the images.** Set the two variables for your registry, then pull. The variables are used by the commands in steps 3.4, 5 and 6, so set them again in a new shell (or put them in a script, see *Updating*).

   **Public ECR** (no login):

   ```
   WEB_IMAGE=public.ecr.aws/p3y5w7c0/lms/unitime-web
   UI_IMAGE=public.ecr.aws/p3y5w7c0/lms/unitime-ui
   docker pull $WEB_IMAGE:latest
   docker pull $UI_IMAGE:latest
   ```

   **Private ECR** (log in first; a login lasts 12 hours, so log in again before each pull):

   ```
   WEB_IMAGE=050752618487.dkr.ecr.ap-south-1.amazonaws.com/lms/unitime-be
   UI_IMAGE=050752618487.dkr.ecr.ap-south-1.amazonaws.com/lms/unitime-ui
   aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 050752618487.dkr.ecr.ap-south-1.amazonaws.com
   docker pull $WEB_IMAGE:latest
   docker pull $UI_IMAGE:latest
   ```

   To deploy a fixed release, use its tag instead of `latest` here and in steps 5 and 6. The containers keep running from the pulled images; the registry is needed again only for the next pull.

5. **Start the web container:**

   ```
   docker run -d --name unitime-web \
       --restart unless-stopped \
       --env-file /opt/tymora/tymora-web.env \
       --add-host host.docker.internal:host-gateway \
       --mount type=bind,source=/opt/tymora/secrets/claude-token,target=/run/secrets/claude-code-token,readonly \
       -v unitime-data:/usr/local/tomcat/data \
       -p 172.17.0.1:8080:8080 \
       -p 127.0.0.1:8888:8080 \
       $WEB_IMAGE:latest
   ```

   - `--name unitime-web`: the name used in the commands below.
   - `--add-host host.docker.internal:host-gateway`: `host.docker.internal`, the host name in `DB_URL`, becomes the server itself (`172.17.0.1`), where MySQL runs (step 3).
   - `--mount ... readonly`: the token file only, read-only. Unlike `-v`, `--mount` refuses to start if the file is missing, instead of creating an empty directory in its place.
   - `-v unitime-data:...`: Tymora's data folder (solver files, uploads) in a named volume, so it survives updates.
   - `-p 172.17.0.1:8080:8080`: Tomcat on the bridge gateway address, for the UI container (step 6). That address is reachable only from the server and its containers. The UI image calls port 8080 by default. Check first that no other application on the server uses it: `sudo ss -ltnp | grep ':8080 '` should print nothing. **If port 8080 is taken**, publish another port and tell the UI about it: see step 6a.
   - `-p 127.0.0.1:8888:8080`: the legacy UI, reachable only from the server itself. Without an address, Docker publishes a port on all interfaces, and its port rules bypass most host firewalls (ufw, firewalld). If port 8888 is taken, change the first number.
   - `--restart unless-stopped`: starts again after a reboot or a crash, unless you stopped it yourself.

6. **Start the UI container:**

   ```
   docker run -d --name unitime-ui \
       --restart unless-stopped \
       --add-host unitime-web:host-gateway \
       -p 127.0.0.1:8000:80 \
       $UI_IMAGE:latest
   ```

   - `--add-host unitime-web:host-gateway`: the name the UI's nginx calls (`unitime-web:8080`) becomes the server's bridge address, where step 5 published Tomcat. This address doesn't change when either container is replaced or the server restarts, so the two containers can be started and replaced in any order. Until the web container is up, the UI answers backend calls with 502.
   - If port 8000 is taken, change the first number here and in `proxy_pass` in step 8.

6a. **Only if port 8080 is taken on the server:** use another port, e.g. `18080`. Starting the web container then fails with `listen tcp4 172.17.0.1:8080: bind: address already in use`. The port the UI calls is set in the nginx configuration inside the UI image. Give the UI container a copy of that file with the new port, mounted over the built-in one. No rebuild is needed.

   1. Remove the container left behind by the failed start, and check that the new port is free:

      ```
      docker rm -f unitime-web
      sudo ss -ltnp | grep -E ':(8080|18080) '        # shows what holds 8080; nothing may hold 18080
      ```

   2. Copy the configuration out of the UI image and change the port:

      ```
      docker run --rm --entrypoint cat $UI_IMAGE:latest /etc/nginx/conf.d/default.conf | sudo tee /opt/tymora/ui-nginx.conf >/dev/null
      sudo sed -i 's/server unitime-web:8080;/server unitime-web:18080;/' /opt/tymora/ui-nginx.conf
      grep -n "server unitime-web" /opt/tymora/ui-nginx.conf     # must show: server unitime-web:18080;
      ```

   3. Start the web container as in step 5, with `-p 172.17.0.1:18080:8080` instead of `-p 172.17.0.1:8080:8080`. Only the left-hand number changes: Tomcat still listens on 8080 inside the container.

   4. Start the UI container as in step 6, with one more line before the image name. If `unitime-ui` is already running, remove it first with `docker rm -f unitime-ui`.

      ```
      docker run -d --name unitime-ui \
          --restart unless-stopped \
          --add-host unitime-web:host-gateway \
          --mount type=bind,source=/opt/tymora/ui-nginx.conf,target=/etc/nginx/conf.d/default.conf,readonly \
          -p 127.0.0.1:8000:80 \
          $UI_IMAGE:latest
      ```

   The mounted copy replaces the image's file. When a new UI image changes its nginx configuration, repeat step 2 before replacing `unitime-ui`; see *Updating*. The same file is also where the UI's 1 MB upload limit can be raised (see *Troubleshooting*, 413).

7. **Check the start, the assistant and the ports:**

   ```
   docker logs -f unitime-web                         # "database reachable", "token loaded", then "Server startup in"; Ctrl+C to stop following
   docker exec unitime-web sh -c 'CLAUDE_CODE_OAUTH_TOKEN="$(tr -d "[:space:]" < /run/secrets/claude-code-token)" claude auth status'   # "loggedIn": true
   curl -sI http://127.0.0.1:8000/ | head -1          # HTTP/1.1 200 OK
   ss -ltn | grep -E ':(8000|8888) '                  # both on 127.0.0.1 only, not 0.0.0.0
   ```

   If the database was used before with the assistant, check the setting `tmtbl.assistant.cli.command` in **Administration > Defaults > Configuration**. It must be empty, or `claude`. A path that exists only in another container, such as a wrapper copied in by hand, is not in the image, and the assistant then fails.

8. **Add a site to the existing nginx**, e.g. `/etc/nginx/sites-available/tymora` (Debian / Ubuntu; on RHEL-type systems put it in `/etc/nginx/conf.d/tymora.conf`):

   ```nginx
   server {
       listen 80;
       server_name tymora.example.edu;

       # Room pictures and medical certificates are uploaded through this proxy (nginx default: 1 MB).
       client_max_body_size 20m;

       location / {
           proxy_pass http://127.0.0.1:8000;
           proxy_http_version 1.1;
           proxy_set_header Host              $host;
           proxy_set_header X-Real-IP         $remote_addr;
           proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
           # Solver runs and AI assistant answers (10-30 seconds, sometimes more) can exceed the 60-second default.
           proxy_read_timeout 300s;
       }
   }
   ```

   Only the one `location /` is needed: the nginx inside `unitime-ui` already decides which paths go to Tomcat. Do not proxy port 8888 publicly. The legacy UI is for administrators; reach it through an SSH tunnel: `ssh -L 8888:127.0.0.1:8888 user@server`.

   Enable it, test, and reload. A reload doesn't interrupt the other sites; if `nginx -t` fails, nothing is changed:

   ```
   sudo ln -s /etc/nginx/sites-available/tymora /etc/nginx/sites-enabled/tymora
   sudo nginx -t && sudo systemctl reload nginx
   ```

9. **Point DNS** for `tymora.example.edu` at the server, then **add HTTPS** the way the other sites have it. With Let's Encrypt:

   ```
   sudo certbot --nginx -d tymora.example.edu
   ```

   Certbot adds the `listen 443 ssl` lines and the redirect from HTTP to this server block. The containers keep talking plain HTTP on the loopback interface. The browser-facing URLs stay correct: Tomcat sends relative redirects and the attendance QR code is built from the address in the browser.

10. **Set the public URL** used in e-mail links: sign in as `admin`, open **Administration > Defaults > Configuration**, and set `unitime.url` to `https://tymora.example.edu`.

11. **Open** <https://tymora.example.edu>, sign in, and select the sparkles button in the header to try the assistant.

### Updating and stopping

- **With the script:** copy `deploy-server.sh` from this folder to the server (e.g. `/opt/tymora/deploy-server.sh`, `chmod +x`). It pulls the public ECR images and replaces the containers with the commands of steps 5 and 6, including the copied UI configuration of step 6a (backend port 18080 by default). It checks the settings files and pulls before removing anything, and waits until Tomcat has started:

  ```
  ./deploy-server.sh                  # both containers, tag latest
  ./deploy-server.sh ui               # only the UI (or: web)
  ./deploy-server.sh both --tag 20261124-1130 --prune   # a specific build; remove old images afterwards
  ./deploy-server.sh --dry-run        # show the commands; --help for all options and settings
  ```

- **New release:** push new images from the build machine. Then, on the server, pull as in step 4 (for private ECR, log in first), and replace the containers. The named volume and the settings files are kept:

  ```
  docker rm -f unitime-web && <the docker run command of step 5>
  docker rm -f unitime-ui  && <the docker run command of step 6>
  ```

  Replace only the container whose image changed; the other can keep running. With a different backend port (step 6a), repeat step 6a.2 before replacing `unitime-ui`, so the copy follows the new image's configuration, and keep the port in the web container's `-p`.

  Tymora upgrades the database schema when a newer web image starts, so back up the database before an update. To go back, run step 5 with the previous tag. A schema that was already upgraded is not downgraded, so restore the backup too.

  To keep the commands at hand, save the two variables of step 4 and the commands of steps 5 and 6 in a script, e.g. `/opt/tymora/start.sh`.
- **Changed settings** in `tymora-web.env`: a container reads them only when it is created, so replace `unitime-web` (`docker rm -f unitime-web`, then step 5).
- **New Claude token:** replace the file's contents, then replace `unitime-web` as above. A plain `docker restart` may keep reading the old file, because the bind mount holds on to the original file when an editor saves a new copy.
- **Stop and start:** `docker stop unitime-ui unitime-web`, then `docker start unitime-web unitime-ui`. The database is not touched.
- **Remove:** `docker rm -f unitime-ui unitime-web`. The volume `unitime-data` is kept; `docker volume rm unitime-data` deletes it.
- **Free disk space** from old images: `docker image prune -a`. This deletes all images that no container uses.

### Troubleshooting

- **`no basic auth credentials` or `denied` on pull from private ECR:** the ECR login expired (12 hours) or the server's AWS identity lacks the pull rights listed in step 1.
- **`denied` or `unauthorized` on pull from public ECR:** usually an expired login for `public.ecr.aws` left on the server, which Docker sends instead of pulling anonymously. Run `docker logout public.ecr.aws` and pull again. Also check the repository name: `unitime-web`, not `unitime-be`.
- **`toomanyrequests` from public ECR:** the anonymous rate limit for the server's IP address. Wait and retry, or log in with any AWS account (`aws ecr-public get-login-password --region us-east-1 | docker login --username AWS --password-stdin public.ecr.aws`) for a higher limit.
- **`invalid reference format`** from `docker pull` or `docker run`: `WEB_IMAGE` / `UI_IMAGE` are not set in this shell (step 4).
- **`exec format error`:** the server isn't x86-64. Use an x86-64 server, or build `linux/arm64` images.
- **The UI answers 502 for every backend call** (the page loads, sign-in fails): the web container isn't running or still starting (`docker logs unitime-web`), or the port published on `172.17.0.1` doesn't match the one the UI calls. `docker port unitime-web` should list `8080/tcp -> 172.17.0.1:8080` (or your port from step 6a), and `docker exec unitime-ui grep "server unitime-web" /etc/nginx/conf.d/default.conf` should show the same port.
- **`listen tcp4 172.17.0.1:8080: bind: address already in use`** or `port is already allocated` when starting the web container: another application uses port 8080. Use another port as in step 6a.
- **`invalid mount config` / `bind source path does not exist`:** the token file isn't at `/opt/tymora/secrets/claude-token`.
- **Web container waits for the database, then stops** (`DB_WAIT_SECONDS`): MySQL isn't reachable from the container. Run the test in step 3.4, and re-check where MySQL listens (3.1) and the firewall (3.3).
- **`Access denied for user 'timetable'@'172.17.0.x'`** in `docker logs unitime-web`: the MySQL user isn't allowed from the containers, or the password differs (step 3.2). Quotes in `tymora-web.env` are passed on as part of the value.
- **After a reboot the web container starts before MySQL:** it waits up to `DB_WAIT_SECONDS` (120) for the database. If MySQL takes longer to start, raise the value.
- **Assistant: "Claude Code is not signed in" or `401`:** check the token file and the `claude auth status` command in step 7. The token must come from `claude setup-token`.
- **502 Bad Gateway** from the existing nginx: the containers aren't running or are still starting (`docker ps`, `docker logs unitime-web`). On SELinux systems (RHEL, Rocky, Alma), nginx may also be blocked from connecting to local ports: `sudo setsebool -P httpd_can_network_connect 1`.
- **413 Request Entity Too Large** on an upload: raise `client_max_body_size` in the site above. The nginx inside `unitime-ui` keeps its own 1 MB default, because `ui/nginx.standalone.conf` sets no limit. For larger files, add `client_max_body_size 20m;` to that file's `server` block, then rebuild and push `unitime-ui`. Or, without a rebuild, add it to the copy described in step 6a.2 and mount that copy as in step 6a.4.
- **Signed out after a while:** sessions end after 45 minutes without activity (`WebContent/WEB-INF/web.xml`). This is not caused by the proxy.
- **Another site's page appears:** the `server_name` doesn't match the hostname used in the browser, so nginx picked its default server. Check `server_name` and DNS.
