# Tymora — Developer Onboarding Guide

Welcome to Tymora 4.8. This guide gets you from a clean checkout to a running,
modifiable application, and explains how the codebase is organized so you know
where to make changes.

> **License / governance:** Apache 2.0, sponsored by the Apereo Foundation.
> Canonical docs: <https://help.unitime.org/building-unitime> and
> <https://help.unitime.org/eclipse>.

---

## 1. Technology stack

| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| Language / build | Java, Maven | **Java 17**, Maven | `maven-compiler-plugin` source/target = 17. CI uses Temurin 17. |
| Server-side web | **Apache Struts 2** | 6.7.4 | Convention plugin scans `…/action` packages; Tiles for layout. Handles `*.action` pages. |
| Rich client UI | **GWT** (Google Web Toolkit) | 2.12.2 | Compiled Java → JavaScript. Module `org.unitime.timetable.gwt.UniTime`. Handles `type="gwt"` pages and RPC. |
| Persistence | **Hibernate ORM** | 6.6.18.Final | ~130 `*.hbm.xml` mappings in `JavaSource/`. C3P0 pool + Infinispan 2nd-level cache. |
| Security | **Spring Security** | 5.8.16 | Pluggable: DB / CAS / LDAP / LDAP-AD / OAuth2. |
| DI / plumbing | **Spring** | 5.3.39 | Root context `applicationContext.xml`. |
| Optimization engine | **cpsolver** | 1.4-SNAPSHOT | `org.unitime:cpsolver` — the constraint solver behind every timetable. Pulled from Sonatype snapshots. |
| Database | MySQL / Oracle / PostgreSQL | MySQL connector-j 8.0.33 | MySQL is the default active config. |
| Container | Tomcat | 9 (jre17) in Docker | Servlet API 3.1, `web-app` 3.1. Jetty plugin also wired for `run-war`. |

Other notable libraries: log4j2 2.24.3, JGroups 5.4.8 (solver clustering), Apache
POI 5.4.0 (Excel), iText 2.1.7 (PDF), FreeMarker, Joda-Time, Restlet, protobuf
4.31.1, Infinispan 15.2.4.

---

## 2. Repository layout

```
unitime/
├── JavaSource/            ← ALL Java source + Hibernate config
│   ├── org/unitime/…      ← application packages (see below)
│   ├── *.hbm.xml          ← ~130 Hibernate mapping files
│   ├── hibernate.cfg.xml  ← DB connection + dialect + cache config
│   ├── application.properties, messages.properties
│   ├── log4j2*.xml, infinispan-*.xml, hibernate-jgroups-tcp.xml
│   ├── menu.xml / menu-custom.xml   ← the application menu tree
│   └── *.proto            ← protobuf definitions
├── WebContent/            ← web application root (becomes the WAR)
│   ├── WEB-INF/
│   │   ├── web.xml         ← filter/servlet wiring (see §6)
│   │   ├── struts.xml, tiles.xml
│   │   ├── applicationContext.xml + securityContext*.xml + *-integration.xml
│   │   └── lib/, tld/
│   ├── *.jsp              ← login.jsp, gwt.jsp/gwt2.jsp, selectPrimaryRole.jsp, …
│   ├── admin/ user/ exam/ tt/ help/   ← JSP pages by area
│   └── layouts/ styles/ images/ scripts/ leaflet/
├── 3rd_party/            ← BUILD-TIME libs not bundled in WAR (gwt-dev, ant, servlet APIs)
├── Documentation/       ← Database SQL, Docker, Interfaces (DTDs), Reports, Scripts
├── target/              ← Maven output (UniTime.war, gwt/, classes/, …)
├── pom.xml              ← Maven build (primary, used by CI)
├── build.xml            ← Ant build (legacy/dev convenience, needs $TOMCAT_HOME)
├── build.number         ← Ant build counter (also stamped into WAR manifest)
└── build.properties     ← Ant-only configuration
```

### Key Java packages (`JavaSource/org/unitime/timetable/`)

| Package | Responsibility |
|---------|----------------|
| `model` | ~230 Hibernate entity classes (Session, InstructionalOffering, Class_, Exam, Event, …). The domain model. |
| `action` | Struts 2 action classes backing `*.action` pages. |
| `form` | Struts form beans. |
| `gwt` | GWT client + shared + server code. `gwt/client` is the browser UI; `gwt.xml` modules define compilation. |
| `server` | Server-side GWT-RPC backends (e.g. `server/menu/MenuBackend.java`). |
| `security` | Authorization: `rights/Right.java` (the permission enum), `evaluation/UniTimePermissionCheck.java`, `SessionContext`. |
| `solver` | Integration with cpsolver: course, exam, student sectioning, instructor scheduling. Includes the JGroups solver server. |
| `onlinesectioning` | Real-time student scheduling engine. |
| `dataexchange` | XML import/export (DTDs live in `Documentation/Interfaces`). |
| `api` | REST-ish HTTP API (`ApiServlet`, connectors) served at `/api/*`. |
| `reports` / `export` | HQL reports, PDF/Excel/CSV exports. |
| `events` | Event management backend. |
| `defaults`, `spring`, `tags`, `util`, `webutil` | App defaults, Spring glue, JSP custom tags, utilities. |

---

## 3. Running locally

### Option A — Docker (fastest, demo-grade)

All-in-one MySQL + Tomcat, mirrors <https://demo.unitime.org>.

```bash
cd Documentation/Docker
docker-compose build && docker-compose up
```

Then open <http://localhost:8888> and log in as **`admin` / `admin`**
(other demo credentials are listed at <https://demo.unitime.org>).

- `docker-compose.yml` runs two services: `unitime-db` (MySQL 8.3, database
  `timetable`, user `timetable`/`unitime`) and `unitime-web` (Tomcat 9 / JRE17,
  host **8888 → 8080**).
- The DB connection is injected at runtime via
  `JAVA_OPTS=-Dconnection.url=jdbc:mysql://unitime-db:3306/timetable`, which
  overrides the checked-in `hibernate.cfg.xml` URL.
- MySQL is seeded from `schema.sql` then `woebegon-data.sql` (sample dataset).

> The Docker README expects the packaged release (it references `web/UniTime.war`
> and `doc/mysql/*`), so build the WAR first (Option C) or download a release
> ≥ 4.8.126.

### Option B — Eclipse + Tomcat (full dev loop)

The canonical setup is documented at <https://help.unitime.org/eclipse>. In
short: import as a Java/Web project, point at a local MySQL, configure
`hibernate.cfg.xml`, and deploy the exploded web app to Tomcat. GWT can be run in
dev/super-dev mode using the `org.unitime.timetable.gwt.UniTimeDev` module (see
the Ant `redeploy-gwtdev` target).

### Option C — Build the WAR with Maven

```bash
mvn -B package -D ignore.symbol.file
```

Produces **`target/UniTime.war`** (and an exploded copy under
`target/unitime-4.8/`). Deploy that WAR to any Servlet 3.1 container (Tomcat 9)
with a reachable database. This is exactly what CI runs
(`.github/workflows/maven.yml`, on push/PR to `master`).

### Option D — Fully local, no Docker (local MySQL + Tomcat + Angular dev server)

Everything on bare metal: a MySQL server you installed yourself, the backend
in a plain Tomcat 9, and the Angular app via `ng serve`. Pick **one** shell —
Git Bash or PowerShell — and stick to it for every step below; the JDBC URL's
`&` characters need different escaping in each, and switching shells mid-way
is the most common way this breaks.

> ⚠️ **`mvn jetty:run-war` does not work on this repo.** The `jetty-maven-plugin`
> execution wired into `pom.xml` looks like a ready-made dev server, but the
> project's packaging is `jar` (§4 explains why: it also produces a solver-server
> jar), and Jetty's `run-war` goal silently **skips** on non-`war` packaging —
> printing `BUILD SUCCESS` and `Skipping UniTime : packaging type [jar] is
> unsupported` while starting nothing. Verified live 2026-08-26; use the Tomcat
> steps below instead.

**1. Install & seed MySQL 8.x locally**

```sql
-- in a mysql shell (mysql -u root -p)
CREATE DATABASE timetable CHARACTER SET utf8mb4;
CREATE USER 'timetable'@'localhost' IDENTIFIED BY 'unitime';
GRANT ALL PRIVILEGES ON timetable.* TO 'timetable'@'localhost';
FLUSH PRIVILEGES;
```

Git Bash:

```bash
# schema, then ONE of the two data sets below (same pair the Docker image loads)
mysql -u timetable -punitime timetable < Documentation/Database/MySQL/schema.sql
mysql -u timetable -punitime timetable < Documentation/Database/MySQL/woebegon-data.sql  # demo dataset (recommended)
# — or, for a bare install with no sample courses/rooms/people —
# mysql -u timetable -punitime timetable < Documentation/Database/MySQL/blank-data.sql
```

PowerShell (native Windows redirection `<` doesn't feed stdin the way bash's
does, so pipe the file content instead):

```powershell
Get-Content Documentation\Database\MySQL\schema.sql | mysql -u timetable -punitime timetable
Get-Content Documentation\Database\MySQL\woebegon-data.sql | mysql -u timetable -punitime timetable
```

**2. Point the backend at it**

Don't edit the checked-in `JavaSource/hibernate.cfg.xml` (it has placeholder
dev credentials pointing at a Docker-host IP — see the security note in §5).
Override the connection at runtime instead, the same way Docker does via
`JAVA_OPTS`. Note the escaped `&` — this matters once you hand the value to
Tomcat's startup script (step 4 explains why), and the escape character
**differs by shell**:

```
# Git Bash — backslash-escape
-Dconnection.url=jdbc:mysql://localhost:3306/timetable?useSSL=false\&useUnicode=true\&characterEncoding=utf-8\&allowPublicKeyRetrieval=true

# PowerShell / cmd.exe — caret-escape
-Dconnection.url=jdbc:mysql://localhost:3306/timetable?useSSL=false^&useUnicode=true^&characterEncoding=utf-8^&allowPublicKeyRetrieval=true

-Dconnection.username=timetable
-Dconnection.password=unitime
```

**3. Build the WAR**

Git Bash:

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-17"   # repo targets Java 17; machine default JAVA_HOME may be JDK 8
mvn -B package -D ignore.symbol.file              # builds target/UniTime.war — the GWT compile step is slow
```

PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B package -D ignore.symbol.file
```

**4. Get a Tomcat 9 and deploy the WAR**

No local Tomcat install needed beforehand — download and unzip one (this
doesn't go through Maven/the JDK's own truststore, so it's unaffected by any
corporate-proxy PKIX issues that block `mvn dependency:*` fetches).

Git Bash:

```bash
curl -LO https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.121/bin/apache-tomcat-9.0.121.zip
unzip -q apache-tomcat-9.0.121.zip
cd apache-tomcat-9.0.121

rm -rf webapps/ROOT webapps/ROOT.war
cp ../target/UniTime.war webapps/ROOT.war   # ROOT.war = root context, matches proxy.conf.json

export JAVA_HOME="/c/Program Files/Java/jdk-17"
export JAVA_OPTS='-Dconnection.url=jdbc:mysql://localhost:3306/timetable?useSSL=false\&useUnicode=true\&characterEncoding=utf-8\&allowPublicKeyRetrieval=true -Dconnection.username=timetable -Dconnection.password=unitime'
./bin/catalina.sh run
```

`catalina.sh` passes `$JAVA_OPTS` through `eval`, so an **unescaped** `&` in the
JDBC URL is parsed as "background this command", splitting the java invocation
apart with `command not found` errors — hence the `\&`. (The same escaping is
not needed for `mvn -D...` on its own, since that's a single already-parsed
shell argument; it only bites once a value is re-parsed by a script like
`catalina.sh`.)

PowerShell (using `catalina.bat` — same idea, different script and escape
character: `catalina.bat` is a batch file, so it's parsed by `cmd.exe`, which
treats an unescaped `&` in an expanded `%JAVA_OPTS%` as a command separator
just like it does on a raw `cmd.exe` command line — hence `^&` instead of `\&`):

```powershell
Invoke-WebRequest https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.121/bin/apache-tomcat-9.0.121.zip -OutFile tomcat9.zip
Expand-Archive tomcat9.zip -DestinationPath .
Set-Location apache-tomcat-9.0.121

Remove-Item webapps\ROOT,webapps\ROOT.war -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item ..\target\UniTime.war webapps\ROOT.war

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:JAVA_OPTS = '-Dconnection.url=jdbc:mysql://localhost:3306/timetable?useSSL=false^&useUnicode=true^&characterEncoding=utf-8^&allowPublicKeyRetrieval=true -Dconnection.username=timetable -Dconnection.password=unitime'
.\bin\catalina.bat run
```

Both were verified live 2026-08-26 (Git Bash + `catalina.sh`, and PowerShell +
`catalina.bat`) against the same local MySQL — each starts cleanly with the
full JDBC URL intact once escaped correctly, and fails with `command not
found` / `'useUnicode' is not recognized...` without the escape. Stop with
Ctrl+C, or `./bin/shutdown.sh` (Git Bash) / `.\bin\shutdown.bat` (PowerShell).

Backend is now at **http://localhost:8080** (first request is slow — the WAR
is unpacked and Spring/Hibernate/GWT initialize on startup; tail
`logs/catalina.<date>.log` for `Server startup in [...] milliseconds`). Log in
as `admin`/`admin` (or another demo user from `Documentation/Docker/README.md`,
if you loaded `woebegon-data.sql`).

**5. Run the Angular UI**

`ui/src/proxy.conf.json` already targets `http://localhost:8080`, so no changes
are needed if you followed step 4 as-is — see [`ui/README.md`](../ui/README.md#run-dev)
for the `npm install && npm start` steps. Open **http://localhost:4200**.

---

## 4. The build in detail

Two build systems coexist. **Maven (`pom.xml`) is authoritative** and used by CI;
Ant (`build.xml`) is a legacy developer convenience.

### Maven pipeline

1. `maven-resources-plugin` copies `JavaSource` → `target/src`, filtering
   `**/Constants.java` to substitute the build number, and excluding
   ant/generator/translation helper classes. The compiler's `<sourceDirectory>`
   is `target/src` (not `JavaSource` directly).
2. `maven-compiler-plugin` compiles Java 17 (`fork=true`, `maxmem=1024m`).
3. `gwt-maven-plugin` 2.10.0 compiles the `org.unitime.timetable.gwt.UniTime`
   module → `target/gwt` (`localWorkers=8`, `-Xmx1g`). **This is the slow step.**
4. `maven-war-plugin` assembles **`UniTime.war`** from `target/webApp` +
   `WebContent` + `target/gwt`.
5. Supporting plugins: `buildnumber-maven-plugin` (revision), `maven-antrun-plugin`
   (build number/date), `license-maven-plugin` (regenerates `NOTICE` from
   `3rd_party/3rd_party.properties` + `.ftl`), `maven-jar-plugin` (solver-server
   jar, main class `…solver.jgroups.SolverServerImplementation`).

### Ant pipeline (optional)

`build.xml` (default target `build`) loads `build.properties` and offers
`compile-java`, `compile-gwt`, `timetable-jar`, `compile-war`, `dist`, plus
deploy/run targets (`deploy`, `start`, `stop`, `redeploy-gwt`, `redeploy-gwtdev`)
that require `$TOMCAT_HOME`. `build.number` (`=18`, "Do not edit!") is the Ant
build counter; `build.properties` holds Ant-only paths and flags.

---

## 5. Database

| File (under `Documentation/Database/<db>/`) | Purpose |
|---------------------------------------------|---------|
| `schema.sql` | Full DDL — creates all tables. |
| `blank-data.sql` | Minimal seed: roles, rights, reference data. Roles are defined in the `roles` table (~line 1616 in MySQL). |
| `woebegon-data.sql` | Sample/demo dataset (the one Docker loads). |
| `Changes/NNN *.sql` | ~596 numbered incremental migration scripts, applied in order for version upgrades. |

Supported databases: **MySQL** (default), **Oracle**, **PostgreSQL** — each has
its own script directory. `JavaSource/hibernate.cfg.xml` is the active DB config:
driver, `MySQLDialect`, default schema `timetable`, C3P0 pool (max 100 / min 10),
Infinispan 2nd-level + query cache, and it maps the package
`org.unitime.timetable.model`.

> ⚠️ **Security note:** the checked-in `hibernate.cfg.xml` contains hardcoded dev
> credentials and a private host IP. In real deployments the connection URL is
> overridden at runtime with `-Dconnection.url=…`. Do not commit real credentials;
> treat the checked-in values as placeholders.

### Authorization model (worth knowing early)

- **`rights`** table maps `role_id → permission value` (a string).
- Those permission strings are the `Right` enum values in
  `security/rights/Right.java`, and the same strings appear as
  `<hasPermission name="…">` guards in `menu.xml`.
- Users don't get rights directly. A **Timetable Manager** record is linked to one
  or more roles via `tmtbl_mgr_to_roles`; the role carries the rights.
- Runtime checks funnel through `SessionContext` →
  `security/evaluation/UniTimePermissionCheck.java`, which validates the current
  authority's right, resolves the target object, and delegates to a per-right
  `permission<RightName>` Spring bean. Denials throw `AccessDeniedException` and
  are converted to a redirect by `PageAccessFilter`.

So: **authenticate → pick a "current authority" (role + academic session) →
every page/action calls a `SessionContext` permission check.**

---

## 6. Request flow & configuration entry points

A request travels through the `web.xml` filter chain (order matters):

```
log4jServletFilter → Encoding(UTF-8) → HibSessionFilter → springSecurityFilterChain
→ Message Log → PageAccessFilter → Query Log → Locale → JavaScript Cache
→ BusySessions → StrutsPrepareAndExecuteFilter (*.action) → DoToActionRedirect (*.do)
```

Servlets handle the non-Struts endpoints: `GwtDispatcherServlet` (`*.gwt`),
`GwtRpcServlet` (`/unitime/gwt.rpc`), plus calendar/pattern/upload/export/
`ApiServlet` (`/api/*`)/maps/picture/task/queue servlets.

**Two rendering paths, one layout:**

- **Struts pages** (`*.action`): action returns a named result → Tiles definition
  in `tiles.xml` → a JSP body. All pages extend `baseLayout` (rendered by
  `layouts/layout-struts2.jsp`) which adds the header/menu/footer and the GWT
  bootstrap script `unitime/unitime.nocache.js`.
- **GWT pages** (`type="gwt"` in `menu.xml`): the body is `gwt2.jsp`, whose anchors
  are attached by the GWT client entry point (`gwt/client/Client.java`). The
  client talks to the server via the RPC servlets.

**Configuration files you will touch:**

| File | What it configures |
|------|--------------------|
| `WebContent/WEB-INF/applicationContext.xml` | Root Spring context; component-scans `org.unitime`; imports the pluggable security + integration contexts via properties. |
| `securityContext.xml` (+ `…CAS/LDAP/LDAP-AD/OAuth2.xml`) | Authentication provider — selected by `unitime.spring.context.security`. |
| `no-integration.xml` / `integration.xml` / `file-integration.xml` | External-system integration — selected by `unitime.spring.context.integration`. |
| `JavaSource/application.properties` | Default app settings: login URL, lockout, solver flags (`tmtbl.solver.local.enabled`, `tmtbl.solver.mem_limit`), logging. |
| `JavaSource/org/unitime/timetable/ApplicationProperties.java` | Central runtime property store — merges `application.properties`, an optional override file, DB `ApplicationConfig`/`SessionConfig`, and supports hot-reload (`tmtbl.properties.dynamic_reload`). |
| `JavaSource/menu.xml` | The application menu tree (each item guarded by a permission). |

---

## 7. Making your first change

1. **Find the feature by its menu entry.** Open `JavaSource/menu.xml`, locate the
   item (e.g. `Instructional Offerings → instructionalOfferingSearch.action`).
2. **Struts page?** The `.action` name maps to an action class in
   `org/unitime/timetable/action/` (convention plugin). The view is a JSP under
   `WebContent/user|admin|exam/…` wired through `tiles.xml`.
   **GWT page?** (`type="gwt"`) the UI is in `gwt/client/…`; server logic in
   `server/…` behind GWT-RPC.
3. **Data model** lives in `org/unitime/timetable/model/` with a matching
   `*.hbm.xml` in `JavaSource/`. Schema changes need a new numbered script in
   `Documentation/Database/*/Changes/`.
4. **Permissions:** if you add a guarded feature, add a value to
   `security/rights/Right.java`, seed it in `blank-data.sql` / a change script,
   and reference it in `menu.xml` / JSP `sec:authorize` / `SessionContext` checks.
5. **Build & verify:** `mvn -B package`, then deploy `target/UniTime.war` (or use
   the Ant `redeploy-gwtdev` loop for fast GWT iteration).

### Tips

- The GWT compile dominates build time — during development, use GWT super-dev
  mode (`UniTimeDev` module) instead of recompiling the whole app.
- Localization strings live in `messages.properties` and per-module GWT
  `*.properties`; see <https://help.unitime.org/localization>.
- XML data import/export contracts are the DTDs in `Documentation/Interfaces/`.
- Reports are HQL-based (Administration → Utilities → Test HQL is a good sandbox).

---

## 8. Where to go next

- **Customization:** <https://help.unitime.org/customizations>
- **Localization:** <https://help.unitime.org/localization>
- **XML interfaces:** <https://www.unitime.org/uct_interfaces.php>
- **User-facing behavior:** read the persona guides under
  [`user-guides/`](user-guides/) to understand what the features you're editing
  actually do for end users.
