<!-- 
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
 -->
# Tymora

Comprehensive University Timetabling System

Tymora is a comprehensive educational scheduling system that supports developing
course and exam timetables, managing changes to these timetables, sharing rooms
with other events, and scheduling students to individual classes.
It is a distributed system that allows multiple university and departmental schedule managers
to coordinate efforts to build and modify a schedule that meets their diverse organizational
needs while allowing for minimization of student course conflicts. It can be used alone to
create and maintain a school's schedule of classes and/or exams, or interfaced with
an existing student information system. 

The system was originally developed as a collaborative effort by faculty,
students, and staff at universities in North America and Europe. The software
is distributed free under an open source license in hopes that other colleges
and universities can benefit their students through better scheduling or wish to
contribute to ongoing research in this area. The Tymora project has become
a sponsored project of the [Apereo Foundation][apereo] in March 2015.

### Components
- [Course Timetabling & Management][courses]
- [Examination Timetabling][exams]
- [Event Management][events]
- [Student Scheduling][students]

## Running locally (no Docker, MySQL)

Full details, alternatives (Docker, Eclipse), and troubleshooting are in
[`docs/DEVELOPER_ONBOARDING.md`](docs/DEVELOPER_ONBOARDING.md#option-d--fully-local-no-docker-local-mysql--tomcat--angular-dev-server).
Prerequisites: JDK 17, Maven, a local MySQL 8.x server, Node.js + npm. Pick
**one** shell below and stick to it for every step — the JDBC URL's `&`
characters need different escaping in each, and mixing shells mid-recipe is
the most common way this goes wrong.

> `mvn jetty:run-war` does **not** work on this repo — the pom's packaging is
> `jar` (it also builds a solver-server jar), and the Jetty plugin's `run-war`
> goal silently skips (`BUILD SUCCESS` with nothing actually started) on
> non-`war` packaging. Use a real Tomcat 9 instead, as below.

### 1. Backend (MySQL + Tomcat)

<details open><summary><b>Git Bash</b> (recommended on Windows — plain <code>cmd.exe</code> treats <code>&amp;</code> as a command separator even inside quotes, breaking this)</summary>

```bash
# 1a. Create the database and user
mysql -u root -p <<'SQL'
CREATE DATABASE timetable CHARACTER SET utf8mb4;
CREATE USER 'timetable'@'localhost' IDENTIFIED BY 'unitime';
GRANT ALL PRIVILEGES ON timetable.* TO 'timetable'@'localhost';
FLUSH PRIVILEGES;
SQL

# 1b. Load the schema, then a data set (woebegon-data.sql = demo dataset; use
# blank-data.sql instead for an empty install)
mysql -u timetable -punitime timetable < Documentation/Database/MySQL/schema.sql
mysql -u timetable -punitime timetable < Documentation/Database/MySQL/woebegon-data.sql

# 1c. Build the WAR (targets Java 17 — set JAVA_HOME if your default is older)
export JAVA_HOME="/c/Program Files/Java/jdk-17"
mvn -B package -D ignore.symbol.file      # produces target/UniTime.war

# 1d. Get a Tomcat 9 (one-time; unzip anywhere, e.g. next to the repo)
curl -LO https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.121/bin/apache-tomcat-9.0.121.zip
unzip -q apache-tomcat-9.0.121.zip
cd apache-tomcat-9.0.121

# 1e. Deploy the WAR as the root app and set the DB connection.
# NOTE the backslash before every '&' — catalina.sh runs JAVA_OPTS through
# `eval`, so an unescaped '&' is parsed as "run in background", which breaks
# the command into pieces and fails with "command not found".
rm -rf webapps/ROOT webapps/ROOT.war
cp ../target/UniTime.war webapps/ROOT.war
export JAVA_HOME="/c/Program Files/Java/jdk-17"
export JAVA_OPTS='-Dconnection.url=jdbc:mysql://localhost:3306/timetable?useSSL=false\&useUnicode=true\&characterEncoding=utf-8\&allowPublicKeyRetrieval=true -Dconnection.username=timetable -Dconnection.password=unitime'
./bin/catalina.sh run
```

Stop with Ctrl+C, or `./bin/shutdown.sh`.

</details>

<details><summary><b>PowerShell</b> (native Windows, no Git Bash needed)</summary>

```powershell
# 1a. Create the database and user
@'
CREATE DATABASE timetable CHARACTER SET utf8mb4;
CREATE USER 'timetable'@'localhost' IDENTIFIED BY 'unitime';
GRANT ALL PRIVILEGES ON timetable.* TO 'timetable'@'localhost';
FLUSH PRIVILEGES;
'@ | mysql -u root -p

# 1b. Load the schema, then a data set (woebegon-data.sql = demo dataset; use
# blank-data.sql instead for an empty install)
Get-Content Documentation\Database\MySQL\schema.sql | mysql -u timetable -punitime timetable
Get-Content Documentation\Database\MySQL\woebegon-data.sql | mysql -u timetable -punitime timetable

# 1c. Build the WAR (targets Java 17 — set JAVA_HOME if your default is older)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
mvn -B package -D ignore.symbol.file      # produces target/UniTime.war

# 1d. Get a Tomcat 9 (one-time; unzip anywhere, e.g. next to the repo)
Invoke-WebRequest https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.121/bin/apache-tomcat-9.0.121.zip -OutFile tomcat9.zip
Expand-Archive tomcat9.zip -DestinationPath .
Set-Location apache-tomcat-9.0.121

# 1e. Deploy the WAR as the root app and set the DB connection.
# NOTE the caret before every '&' — catalina.bat is a batch file, parsed by
# cmd.exe, which treats an unescaped '&' in an expanded variable as a command
# separator (same underlying issue as Git Bash's eval, different shell).
Remove-Item webapps\ROOT,webapps\ROOT.war -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item ..\target\UniTime.war webapps\ROOT.war
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:JAVA_OPTS = '-Dconnection.url=jdbc:mysql://localhost:3306/timetable?useSSL=false^&useUnicode=true^&characterEncoding=utf-8^&allowPublicKeyRetrieval=true -Dconnection.username=timetable -Dconnection.password=unitime'
.\bin\catalina.bat run
```

Stop with Ctrl+C, or `.\bin\shutdown.bat`.

</details>

Backend is now at **http://localhost:8080** (first request is slow — the WAR
is being unpacked and Spring/Hibernate/GWT are initializing; watch
`logs/catalina.<date>.log` for `Server startup in [...] milliseconds`).
Log in as `admin` / `admin` (demo users are listed in
[`Documentation/Docker/README.md`](Documentation/Docker/README.md)).

### 2. Angular app

```bash
cd ui
npm install
npm start
```

Open **http://localhost:4200** — `ui/src/proxy.conf.json` already proxies
`/api`, `/login`, etc. to `http://localhost:8080`, matching step 1d above.

### Tutorials
- [Installation Instructions][install]
- [Building Tymora][build]
- [Setting up Tymora in Eclipse][eclipse]
- [Customization][customization]
- [Localization][localization]

### Links
- [Tymora 4.6 documentation][docs]
- [Online Documentation][help]
- [Online Demo][demo]
- [Downloads][downloads]
- [Nightly Builds][builds]
- [XML Interfaces][xml]
- [Publications][publications]

[courses]: https://www.unitime.org/uct_courses.php
[exams]: https://www.unitime.org/uct_exams.php
[events]: https://www.unitime.org/uct_events.php
[students]: https://www.unitime.org/uct_students.php
[help]: https://help.unitime.org
[install]: https://help.unitime.org/installation
[demo]: https://demo.unitime.org
[builds]: https://builds.unitime.org
[xml]: https://www.unitime.org/uct_interfaces.php
[publications]: https://www.unitime.org/publications.php
[downloads]: https://sourceforge.net/projects/unitime/files
[build]: https://help.unitime.org/building-unitime
[eclipse]: https://help.unitime.org/eclipse
[docs]: https://bit.ly/unitime46docs
[apereo]: https://www.apereo.org
[customization]: https://help.unitime.org/customizations
[localization]: https://help.unitime.org/localization

