# UniTime — Course Timetable Generation Demo: Setup & Run Guide

This document is a **step-by-step runbook** for preparing UniTime and running a
**course timetabling** demo for a university audience. It covers two paths:

- **Path A — Fast demo** using the shipped **woebegon** sample university (minutes to running).
- **Path B — Build a session from scratch**, entering/loading the data yourself
  (the full "how a university sets up a term" story).

Then it covers the **solver run** (Load → Solve → Review → Save → **Commit**),
a suggested **demo script**, and **troubleshooting**.

> Screen and action names below are quoted from `JavaSource/menu.xml`. This build
> has an in-progress **Angular UI**: many screens are already Angular, the rest fall
> through to the legacy pages via the same menu — the workflow is identical either way.
> Deeper background lives in `docs/user-guides/` (esp. `administrator.md`,
> `department-schedule-manager.md`, `glossary.md`) and `docs/DEVELOPER_ONBOARDING.md`.

---

## 0. Key concepts (60-second primer for the demo)

| Term | What it is |
|---|---|
| **Academic Session** | One term at one campus, e.g. *2010 Fall, woebegon*. Everything is scoped to a session. |
| **Session Status** (`DepartmentStatusType`) | A capability gate on the session: whether managers may **edit data**, **run the solver** (`Timetabling`), or **commit** (`Timetable Published`). If a screen is read-only, check the **status** first, not permissions. |
| **Department → Subject Area** | A department (e.g. `0101`) owns subject areas (e.g. `ALG`, `BIOL`). |
| **Solver Group** | The entity that *owns* a course solution. Departments are assigned to a solver group; **the course solver loads and saves one solver group's data**. |
| **Timetable Manager + Role** | A user account with a role (e.g. *Department Schedule Manager*) whose **rights** allow solving/committing, plus the department(s) they manage. |
| **Instructional Offering hierarchy** | `Instructional Offering → Course Offering(s) → Configuration → Scheduling Subpart (Lecture/Lab/Recitation) → Class (section)`. |
| **Preferences** | Required → Strongly Preferred → Preferred → **Neutral** → Discouraged → Strongly Discouraged → Prohibited, applied to time / room / building / feature / group / date pattern / distribution / instructor. |
| **Solver** | The cpsolver engine. You pick a **Configuration**, **Load** data, **Start** solving, keep the **best** solution, then **Save** and **Commit**. |
| **Commit** | Makes a saved solution the **official** timetable for the session — feeds public/instructor/student views, student sectioning, and exam/event scheduling. |

---

## 1. Prerequisites

1. **UniTime running** and reachable (this instance: `http://localhost:8888`, Angular dev UI proxies to it).
2. A database seeded with **schema + reference data**:
   - `Documentation/Database/MySQL/schema.sql` (tables)
   - `Documentation/Database/MySQL/blank-data.sql` (roles, rights, status types, distribution types, preference levels — the minimum to log in and work), **or**
   - `Documentation/Database/MySQL/woebegon-data.sql` (a *complete* pre-loaded demo — see Path A).
3. **Admin login**: `admin` / `admin` (default for the Docker/demo build).
4. A modern browser. Have the **menu** open — the whole app is menu-driven.

> Docker shortcut: `Documentation/Docker/docker-compose.yml` stands up MySQL (auto-seeded
> with `schema.sql` + `woebegon-data.sql`) and Tomcat. This is the quickest way to a running demo.

---

## 2. Path A — Fast demo with the woebegon sample university

Use this when you want a **populated, already-solved** timetable to show immediately.
The `woebegon-data.sql` dump contains a full session: departments, buildings/rooms,
offerings, classes, preferences, and a committed solution.

### A.1 Load the dataset
- If using Docker: it's already loaded — skip to A.2.
- Otherwise load the dump into a `timetable` MySQL database:
  ```bash
  mysql -u root -p timetable < Documentation/Database/MySQL/woebegon-data.sql
  ```
  (The script does `use timetable;`, wipes all tables, then inserts the demo.)

### A.2 Log in and select the session/role
1. Browse to the app, log in as `admin` / `admin`.
2. On the **role/session selection** page, pick **System Administrator — 2010 Fall (woebegon)**
   (or a *Department Schedule Manager* role to tell the scheduler's story).

### A.3 Show the existing timetable (the "after" picture)
- **Courses → Course Timetabling → Timetable Grid** (`timetableGrid`) — the committed grid.
- **Courses → Course Timetabling → Assigned Classes** (`assignedClasses`) and
  **Not-assigned Classes** (`notAssignedClasses`).
- **Courses → Course Timetabling → Reports** (`solutionReports`) and **Conflict Statistics / CBS** (`cbs`).
- **Courses → Input Data → Instructional Offerings** (`instructionalOfferingSearch.action`) — the source data.

### A.4 (Optional) Re-generate live for the audience
To *show the solver working*, load the same data into the solver and re-solve:
- Go to **Courses → Course Timetabling → Solver** (`solver`, type = course) and follow **Section 4** below.
- You can **Save As New** so you don't disturb the shipped committed solution, then compare.

That's the fastest possible demo. The rest of this document (Path B + Section 4) is for
telling the **full setup story** or building a **fresh** session.

---

## 3. Path B — Build a session from scratch (full data setup)

Start from a **blank** database (`schema.sql` + `blank-data.sql`). Perform the steps
**in this order** — each depends on the ones before it. All setup screens are under the
**Administration** menu unless noted.

> Tip: You can enter data by hand through the screens (good for the demo narrative) **or**
> bulk-load via **Data Exchange** (Section 6) — the two are interchangeable.

### Step 1 — Create the Academic Session
**Administration → Academic Sessions → Academic Sessions** (`sessionList.action`) → **Add**.

Required fields (validated server-side in `SessionCreateBackend`):
- **Academic Initiative** (campus), **Academic Year** (numeric), **Academic Term** (e.g. `Fal`).
- **Session begin** date, **Classes end** date, **Session end** date.
- **Exam begin**, **Event begin**, **Event end** dates.
- **Status** → choose **`Input Data Entry`** to start (lets managers enter data).

Date rules enforced: session end after begin; classes end within the session; event end after event begin.

> **Session Status controls everything downstream.** Seeded statuses (from `blank-data.sql`):
> `Initial Data Load` → **`Input Data Entry`** (edit data) → **`Timetabling`** (run the solver)
> → **`Timetable Published`** (commit + public views) → `Session Finished`. Manage the catalog at
> **Administration → Other → Status Types** (`deptStatusTypeEdit.action`).

### Step 2 — Departments, then Subject Areas
1. **Academic Sessions → Departments** (`departmentList.action` / Angular `departments`) — create departments (code, abbreviation, name), e.g. `0101 - Instructional Planning`.
2. **Academic Sessions → Subject Areas** (`subjectList.action` / `admin?type=subjectArea`) — create subject areas (abbreviation, titles) and **assign each to a department**, e.g. `ALG`, `BIOL`.

### Step 3 — Solver Groups (assign departments)
**Academic Sessions → Solver Groups** (`solverGroupEdit.action`).
- Create a solver group and **assign the department(s)** you want timetabled together.
- **Why:** the course solver loads/saves **per solver group**. No departments in a solver group ⇒ nothing to solve.

### Step 4 — Timetable Managers & roles
**Academic Sessions → Managers** (`timetableManagerList.action`).
- Create/verify a manager account, give it a **role** whose rights include **Solver / Course Timetabling**
  (e.g. *Department Schedule Manager* or *System Administrator*), and **assign the department(s)**.
- The chain that makes solving possible: *authenticated user → manager → role with solve rights → assigned departments → those departments' solver group*.

### Step 5 — Buildings & Rooms (+ types/features/groups)
1. **Academic Sessions → Buildings** (`buildings`) — create buildings.
2. **Courses → Input Data → Rooms** (`rooms`) — create rooms with **capacity**, building, and **room type**.
3. Optional but great for the demo:
   - **Room Features** (`roomfeatures`) and **Room Groups** (`roomgroups`) — e.g. "has projector", "science labs".
   - **Administration → Other → Room Types / Room Feature Types** (`admin?type=roomType|featureType`).
   - **Travel Times** (`traveltimes`) — used by back-to-back distance constraints.

### Step 6 — Date Patterns, Time Patterns, Exact Time
**Administration → Academic Sessions →**
- **Date Patterns** (`datePatternEdit.action`) — which weeks/days a class meets (Full Term, first-half, etc.).
- **Time Patterns** (`timePatternEdit.action`) — allowed meeting-time shapes (e.g. `3 × 50`, `2 × 75`).
- **Exact Time** (`exactTimeEdit.action`) — exact-minute meeting rules.
- These must exist **before** classes can carry time/date preferences.

### Step 7 — Academic Areas / Classifications / Majors (only if demoing curricula or students)
**Administration → Academic Sessions →** `admin?type=area | classification | major | minor | degree | program`.
Needed for curricula, reservations, and student demand — skip for a pure course-timetabling demo.

### Step 8 — Instructors
**Courses → Input Data → Instructors** (`instructorSearch.action`) — add instructors you'll assign to classes.
(*Instructional Types* — Lecture/Recitation/Lab — are seeded reference data at
**Administration → Other → Instructional Types**, `itypeDescList.action`.)

### Step 9 — Instructional Offerings, Configurations, Subparts, Classes
**Courses → Input Data → Instructional Offerings** (`instructionalOfferingSearch.action`).
For each course:
1. **Make it offered** for the term, set the **course/offering limit** and coordinator.
2. Add a **Configuration** (an alternative way to take the course).
3. Add **Scheduling Subparts**, each with an **instructional type** (Lecture / Recitation / Lab).
4. Create **Classes** (sections) under each subpart with a **section number** and **limit**.
5. **Cross-list** courses that share an offering, if any.

> "Arrange Hours" classes (no time pattern) won't appear on the grid by time — that's expected.

### Step 10 — Preferences (this is what the solver optimizes)
On each class/subpart (within the Instructional Offerings screen) set as needed:
- **Time** preferences (preferred/required time patterns), **Room**, **Building**, **Room Feature/Group** preferences, and the **Date Pattern**.
- **Instructor** assignment (and instructor availability via **Instructor Survey**, `instructorSurvey`).

Cross-class rules:
- **Courses → Input Data → Distribution Preferences** (`distributionPrefs.action`) — pick a
  **Distribution Type** (e.g. *Back-To-Back & Same Room*, *Same Instructor*, *Spread*, *Precedence*)
  + a preference level + the classes it links. Catalog managed at
  **Administration → Solver → Distribution Types** (`distributionTypeList.action`).
- **Reservations** (`reservations`) — hold seats for a curriculum/group/course/students.

> **Golden rule for a clean demo:** prefer **soft** preferences (*Preferred / Discouraged*) over
> **hard** ones (*Required / Prohibited*). Too many hard constraints ⇒ unassigned classes.

### Step 11 — Flip the session to `Timetabling`
**Administration → Academic Sessions → Academic Sessions** → edit your session →
set **Status = `Timetabling`**. This unlocks the solver (`canTimetable()`).

You are now ready to solve → **Section 4**.

---

## 4. Run the solver (Load → Solve → Review → Save → Commit)

**Courses → Course Timetabling → Solver** (`solver`, parameter `type=course`).
Backed by `SolverPageBackend` + `CourseTimetablingSolverService` (engine: `TimetableSolver`, wraps cpsolver).

### 4.1 Pick a Configuration
Choose a solver **Configuration** (basic settings preset). Course presets seeded by the demo include
**`Interactive`**, **`Check`**, and **`Default`**. Configurations are administered at
**Administration → Solver → Configurations** (`solverSettings.action`); parameter defs/groups at
`admin?type=solverParamDef | solverParamGroup`.

### 4.2 Load and Start
- **Load** — load the solver group's data into the solver without optimizing (inspect first), **or**
- **Start** — load **and** begin optimizing (`General.StartSolver = true`).

Watch the **progress bar** and the live objective/quality indicators as it runs.

### 4.3 Control while solving
- **Stop** — pause optimization.
- **Save Best** / **Restore Best** — keep or recall the best solution found so far.
- **Reload** — reload data (e.g. after a preference change) with a chosen configuration.

### 4.4 Review the solution (before committing)
From **Courses → Course Timetabling**:
- **Timetable Grid** (`timetableGrid`) — the visual room/time grid.
- **Assigned Classes** (`assignedClasses`) / **Not-assigned Classes** (`notAssignedClasses`).
- **Conflict Statistics / CBS** (`cbs`) — what's driving the cost, and why classes are unassigned.
- **Solver Log** (`solverlog?type=course`), **Reports** (`solutionReports`),
  **Changes** (`solutionChanges`), **History** (`assignmentHistory`).

### 4.5 Save
Save the solution to the database:
- **Save** — persist to the current solution.
- **Save As New** — persist as a new solution (keeps prior ones for comparison — ideal in a demo).

### 4.6 Commit (publish the official timetable)
Committing marks a solution as the session's **official** timetable (uncommitting any previous one for
that solver group). Do it either directly from the solver (**Save & Commit** / **Save As New & Commit**)
or from **Courses → Course Timetabling → Saved Timetables** (`listSolutions`) → select the solution →
**Commit** (`SolutionOperation.COMMIT` in `ListSolutionsBackend`).

> **Commit requires** the session status to allow it (`DepartmentStatusType.canCommit()`, e.g.
> `Timetable Published`). After committing, the timetable appears in public/instructor/student views and
> becomes the basis for **student sectioning** and **exam/event** scheduling. You can **Uncommit** and
> re-commit while the status permits.

### 4.7 (Optional) Publish
Set the session **Status = `Timetable Published`** to expose the committed timetable to everyone.

---

## 5. Suggested 15-minute demo script

1. **Frame it** (1 min): "UniTime builds a conflict-free class timetable from courses, rooms,
   instructors, and preferences." Show the concept table (Section 0).
2. **The inputs** (4 min): Open **Instructional Offerings** — show an offering's
   Configuration → Subparts → Classes. Open one class's **preferences** (time/room). Open
   **Distribution Preferences** for a back-to-back rule. Show **Rooms** with capacities/features.
3. **The engine** (5 min): Go to **Solver**, pick **Default**, **Start**. Narrate the progress bar
   and objective dropping. **Save Best** when it settles.
4. **The result** (3 min): **Timetable Grid** (conflict-free), **Not-assigned Classes** (ideally empty),
   **CBS** to explain trade-offs, **Reports** for a summary.
5. **Publish** (2 min): **Save As New & Commit**, then flip status to **Timetable Published** and show a
   public/instructor view. Mention downstream: student sectioning + exams/events build on this.

Have the **woebegon** session loaded as a fallback so you always have a populated system to show.

---

## 6. Bulk-load data via Data Exchange (optional, for larger demos)

**Administration → Academic Sessions → Data Exchange** (`dataImport.action`) imports XML.
The importer is chosen by the XML **root element** (`DataExchangeHelper`). Ready-to-use examples and
DTDs live in `Documentation/Interfaces/Examples/` and `Documentation/Interfaces/`.

Recommended load order (root element → example file):
1. `session` / `sessionSetup` → `sessionImport.xml` / `sessionSetup.xml` (create session + reference data)
2. `departments` → `departmentImport.xml`
3. `subjectAreas` → `subjectAreaImport.xml`
4. `academicAreas`, `academicClassifications`, `posMajors`, `posMinors` → the matching `*Import.xml`
5. `buildingsRooms` → `buildingRoomImport.xml`
6. `offerings` → `courseOfferingsWithMeetings.xml`
7. `preferences` → `preferences.xml`
8. `curricula`, `reservations`, `roomSharing`, `traveltimes` → matching examples
9. `students` / `studentEnrollments` → `woebegonStudents.xml` (student demand)
10. `timetable` (root; `coursett`) → `courseTimetableImport.xml` — import an **already-solved** timetable

This is the fastest way to stand up a *custom* demo university without hand-entering every screen.

---

## 7. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| A data screen is **read-only** | The **session status** doesn't allow editing. Set it to `Input Data Entry` (or a status with the edit flag). Not a permissions problem. |
| **Solver menu / Load disabled** | Session status must allow timetabling — set to **`Timetabling`**. Also confirm your role has the `Solver`/`CourseTimetabling` right. |
| **"Nothing to load" / empty solver** | The department(s) aren't in a **Solver Group**, or your manager isn't assigned those departments. Fix Steps 3–4. |
| **Many Not-assigned Classes** | Too many **hard** (Required/Prohibited) preferences, or rooms too small/scarce. Relax to soft preferences; check room capacities; read **CBS** for the binding constraints. |
| **Can't Commit** | Session status lacks the **Commit** capability. Move toward **`Timetable Published`**. |
| **Classes missing from the grid** | "Arrange Hours" classes have no time pattern — expected; they don't show by time. |
| **Committed timetable not visible to students** | Publish the session (**`Timetable Published`**) and ensure student sectioning/status is enabled. |

---

## 8. Quick reference — screens & actions

| Task | Menu path | Action / page |
|---|---|---|
| Academic Sessions | Administration → Academic Sessions | `sessionList.action` |
| Status Types | Administration → Other | `deptStatusTypeEdit.action` |
| Departments | Administration → Academic Sessions | `departmentList.action` / `departments` |
| Subject Areas | Administration → Academic Sessions | `subjectList.action` / `admin?type=subjectArea` |
| Solver Groups | Administration → Academic Sessions | `solverGroupEdit.action` |
| Managers | Administration → Academic Sessions | `timetableManagerList.action` |
| Buildings | Administration → Academic Sessions | `buildings` |
| Rooms / Features / Groups | Courses → Input Data | `rooms` / `roomfeatures` / `roomgroups` |
| Date / Time / Exact Time | Administration → Academic Sessions | `datePatternEdit.action` / `timePatternEdit.action` / `exactTimeEdit.action` |
| Instructors | Courses → Input Data | `instructorSearch.action` |
| Instructional Types | Administration → Other | `itypeDescList.action` |
| Instructional Offerings | Courses → Input Data | `instructionalOfferingSearch.action` |
| Classes | Courses → Input Data | `classSearch.action` |
| Distribution Preferences | Courses → Input Data | `distributionPrefs.action` |
| Distribution Types | Administration → Solver | `distributionTypeList.action` |
| Reservations | Courses → Input Data | `reservations` |
| Solver | Courses → Course Timetabling | `solver?type=course` |
| Solver Configurations | Administration → Solver | `solverSettings.action` |
| Manage Solvers | Administration → Solver | `manageSolvers.action` |
| Saved Timetables (Commit) | Courses → Course Timetabling | `listSolutions` |
| Timetable Grid | Courses → Course Timetabling | `timetableGrid` |
| Assigned / Not-assigned | Courses → Course Timetabling | `assignedClasses` / `notAssignedClasses` |
| Conflict Statistics (CBS) | Courses → Course Timetabling | `cbs` |
| Solution Reports | Courses → Course Timetabling | `solutionReports` |
| Data Exchange (bulk load) | Administration → Academic Sessions | `dataImport.action` |

---

## 9. Related docs in this repo
- `docs/user-guides/administrator.md` — standing up a new session (authoritative ordering).
- `docs/user-guides/department-schedule-manager.md` — the set-up → preferences → solve → review → commit flow.
- `docs/user-guides/glossary.md` — every core concept.
- `docs/DEVELOPER_ONBOARDING.md` — stack, DB seed files, auth model, how to run (Docker/Maven).
- `Documentation/Docker/README.md` — one-command demo stack (login `admin`/`admin`, seeds woebegon).
- `Documentation/Interfaces/` — Data Exchange DTDs and `Examples/` for every import format.
