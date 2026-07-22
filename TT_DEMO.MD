# Tymora — Course Timetable Generation Demo: **Marina Bay Institute of Technology**

This document is a **step-by-step runbook** that builds a complete **course timetable**
from scratch for a worked example: **Marina Bay Institute of Technology (MBIT)** — a
*fictional* university in Singapore. Every screen below is given with the **exact data to
enter** and the **user/role that performs it**, so you can reproduce the whole session end
to end and run the solver on it.

> **MBIT is fictional.** The institution, staff names, and course codes are invented for
> this demo and do not represent any real Singapore university. Swap in your own data freely.

The flow: define your **users** (§2), enter the **MBIT dataset** (§3) through the build
steps (§4), run the **solver** (§5), optionally run an **examination** pass (§6), then a
**demo script** (§8) and **troubleshooting** (§9).

> Screen and action names are quoted from `JavaSource/menu.xml`. This build has an
> in-progress **Angular UI**: many screens are already Angular, the rest fall through to the
> legacy pages via the same menu — the workflow is identical either way. Background lives in
> `docs/user-guides/` (esp. `administrator.md`, `department-schedule-manager.md`, `glossary.md`).

---

## 0. Key concepts (60-second primer)

| Term | What it is |
|---|---|
| **Academic Session** | One term at one campus, e.g. *2026 Aug, MBIT*. Everything is scoped to a session. |
| **Session Status** (`DepartmentStatusType`) | A capability gate on the session: whether managers may **edit data**, **run the solver** (`Timetabling`), or **commit** (`Timetable Published`). If a screen is read-only, check the **status** first, not permissions. |
| **Department → Subject Area** | A department (e.g. School of Computing `0100`) owns subject areas (e.g. `CS`, `IS`, `AI`). |
| **Solver Group** | The entity that *owns* a course solution. Departments are assigned to a solver group; **the course solver loads and saves one solver group's data**. |
| **Timetable Manager + Role** | A user account with a role (e.g. *Department Schedule Manager*) whose **rights** allow solving/committing, plus the department(s) they manage. |
| **Instructional Offering hierarchy** | `Instructional Offering → Course Offering(s) → Configuration → Scheduling Subpart (Lecture/Tutorial/Lab) → Class (section)`. |
| **Preferences** | Required → Strongly Preferred → Preferred → **Neutral** → Discouraged → Strongly Discouraged → Prohibited, applied to time / room / building / feature / group / date pattern / distribution / instructor. |
| **Solver** | The cpsolver engine. You pick a **Configuration**, **Load** data, **Start** solving, keep the **best** solution, then **Save** and **Commit**. |
| **Commit** | Makes a saved solution the **official** timetable — feeds public/instructor/student views, student sectioning, and exam/event scheduling. |

---

## 1. Prerequisites

1. **Tymora running** and reachable. The live instance used while writing this guide is
   `http://localhost:8080` (deployed Tomcat). If you run the Angular dev server it listens on
   `http://localhost:4200` and proxies `/api` + legacy pages to `:8080` (`ui/proxy.conf.json`).
2. A **blank** database seeded with schema + reference data (we build MBIT from scratch):
   - `Documentation/Database/MySQL/schema.sql` (tables)
   - `Documentation/Database/MySQL/blank-data.sql` (roles, rights, status types, distribution
     types, preference levels — the minimum to log in and build a session).
3. **Admin login**: `admin` / `admin` (default for the Docker/demo build).
4. A modern browser, with the **menu** open — the whole app is menu-driven.

---

## 2. Users & who does what

Tymora is **role-driven**: every action requires a user whose **role** grants the right, and
(for department data) whose **assigned department** matches the data. For this demo you use
three identities.

### 2.1 The demo users
| Login | Name | Role | Scope | Does |
|---|---|---|---|---|
| `admin` / `admin` | *System Administrator* | **System Administrator** | Whole system | All global/session **setup**: session, status types, departments, subject areas, solver groups, **manager accounts**, buildings/rooms, room types/features, date/time patterns, distribution types, solver configurations. Also **Switch User** (below) and flipping session status. |
| `weiling` | *Tan Wei Ling* | **Department Schedule Manager** | Depts **0100 SoC + 0400 SCI** | Course **data entry** (offerings, configs, subparts, classes), **preferences**, distribution preferences, run the **course solver**, review, **Save**, **Commit**. |
| `priya` | *Priya d/o Menon* | **Examination Manager** | Exam rights for **SoC + SCI** | Create **exams**, exam **distribution preferences**, **room availability**, run the **exam solver**, generate **exam PDF reports**. |

> In a small deployment one person may hold both the Schedule Manager and Examination Manager
> roles — the two personas here just show **which rights each action needs**. The `admin`
> account creates the other two in **Step 4**.

### 2.2 Two mechanisms for "acting as the right user"
- **Change Role** — **Log Out ▸ menu ▸ Change Role** (`selectPrimaryRole.action`). If your
  account holds several roles (e.g. an Administrator authority per session), pick the **active**
  one before acting. When `admin` logs in, choose **System Administrator — 2026 Aug (MBIT)**.
- **Switch User (Chameleon)** — **System Administrator only** (`chameleon.action`, menu
  *"Switch User"*). Lets the admin **assume a manager's identity** without a separate login —
  the practical way to demo the manager steps. Switch to *Tan Wei Ling* for §4 Steps 7–9 and
  §5, to *Priya Menon* for §6, then Switch User back to admin for the session-status flips.

> The System Administrator has broad rights and can perform many manager actions directly, but
> using the **manager roles** mirrors real operations and avoids status/permission surprises
> (e.g. editing offerings, committing). Each step below names the **acting user**.

### 2.3 Who does what — quick matrix
| Activity | Acting user |
|---|---|
| Session, status types, departments, subject areas, solver groups, managers | **Administrator** (`admin`) |
| Buildings, rooms, room types/features/groups, travel times, date/time patterns, distribution types, solver configs | **Administrator** (`admin`) |
| Instructors, offerings, classes, preferences, distribution preferences | **Schedule Manager** (*Wei Ling*) |
| Run course solver, review, Save, **Commit** | **Schedule Manager** (*Wei Ling*) |
| Flip session status (`Input Data Entry` → `Timetabling` → `Timetable Published`) | **Administrator** (`admin`) |
| Exams: create, distribution prefs, room availability, solve, PDF reports | **Exam Manager** (*Priya*) |

---

## 3. The MBIT dataset (enter this in §4–§6)

Everything you need to type is collected here so the section is reusable. AY2026/2027,
Semester 1 (the Singapore August intake).

### 3.1 Academic Session
| Field | Value |
|---|---|
| Academic Initiative (campus) | `MBIT` (Marina Bay) |
| Academic Year | `2026` |
| Academic Term | `Aug` (Semester 1) |
| Session begin (instruction starts) | **2026-08-10** (Mon) |
| Classes end (last teaching day) | **2026-11-13** (Fri) |
| Session end | **2026-11-27** |
| Exam begin | **2026-11-16** |
| Event begin / Event end | **2026-08-10** / **2026-11-27** |
| Initial Status | **`Input Data Entry`** |

> Singapore Sem 1 shape: 13 teaching weeks with a **recess week (21–27 Sep 2026)** and finals in
> late November. Model recess/holidays as a **date pattern** that excludes those weeks (§3.6).

### 3.2 Departments
| Code | Name | Abbrev |
|---|---|---|
| `0100` | School of Computing | SoC |
| `0200` | School of Engineering | SoE |
| `0300` | Business School | BIZ |
| `0400` | School of Science | SCI |

### 3.3 Subject Areas (→ department)
| Subject | Title | Department |
|---|---|---|
| `CS` | Computer Science | 0100 SoC |
| `IS` | Information Systems | 0100 SoC |
| `AI` | Artificial Intelligence | 0100 SoC |
| `EE` | Electrical Engineering | 0200 SoE |
| `ME` | Mechanical Engineering | 0200 SoE |
| `ACC` | Accountancy | 0300 BIZ |
| `FIN` | Finance | 0300 BIZ |
| `MA` | Mathematics | 0400 SCI |
| `PC` | Physics | 0400 SCI |

### 3.4 Solver Group
| Solver Group | Departments assigned |
|---|---|
| **`MBIT-Core`** | **0100 School of Computing** + **0400 School of Science** |

> We timetable Computing and Science together because CS students take `MA`/`PC` courses and the
> two schools share lecture theatres — putting them in one solver group lets the engine resolve
> those room/time interactions in a single run. (Engineering/Business can be a second group later.)

### 3.5 Buildings, Rooms, Room Types, Features, Groups
Room **types**: `Lecture Theatre`, `Seminar Room`, `Tutorial Room`, `Computer Lab`, `Teaching Lab`.
Room **features**: `Workstations`, `Projector`, `Lab Benches`, `Wheelchair Access`.
Room **groups**: `Computer Labs`, `Lecture Theatres`.

| Building | Code | Room | Type | Capacity | Features | In group |
|---|---|---|---|---|---|---|
| Lecture Theatre Complex | `LT` | `LT-19` | Lecture Theatre | 300 | Projector, Wheelchair Access | Lecture Theatres |
| Lecture Theatre Complex | `LT` | `LT-27` | Lecture Theatre | 200 | Projector | Lecture Theatres |
| Computing Block 1 | `COM1` | `COM1-0201` | Seminar Room | 45 | Projector | — |
| Computing Block 1 | `COM1` | `COM1-0203` | Tutorial Room | 30 | Projector | — |
| Computing Block 2 | `COM2` | `COM2-0108` | Computer Lab | 30 | Workstations, Projector | Computer Labs |
| Computing Block 2 | `COM2` | `COM2-0110` | Computer Lab | 30 | Workstations, Projector | Computer Labs |
| Science Building | `S16` | `S16-0430` | Tutorial Room | 25 | Projector | — |
| Science Building | `S16` | `S16-0436` | Teaching Lab | 24 | Lab Benches | — |

> **Travel Times** (`traveltimes`): set a walk time between `COM2` (labs) and `LT`/`S16` so the
> solver spaces back-to-back classes realistically. ~6–8 min is reasonable on a compact campus.

### 3.6 Date Patterns
| Name | Coverage |
|---|---|
| **Full Term** | Teaching weeks 1–13 (Aug 10 → Nov 13), **excluding recess week 21–27 Sep** |
| First Half | Weeks 1–6 (Aug 10 → Sep 20) |
| Second Half | Weeks 8–13 (Sep 28 → Nov 13) |

### 3.7 Time Patterns (MBIT teaching grid: Mon–Fri, 08:00–18:00)
| Name | Shape | Typical use | Preferred start times |
|---|---|---|---|
| **Lecture 2h** | 1 meeting × 120 min | Main lectures | Mon/Tue/Wed 09:00 or 14:00 |
| **Lecture 2×1h** | 2 meetings × 60 min | Twice-weekly lectures | Mon+Thu / Tue+Fri 10:00 |
| **Tutorial 1h** | 1 × 60 min | Tutorials | Weekday afternoons 13:00–17:00 |
| **Lab 2h** | 1 × 120 min | Computer / teaching labs | Weekday afternoons 14:00–18:00 |
| **Seminar 3h** | 1 × 180 min | Seminar-style modules | Tue/Thu 14:00 |

### 3.8 Instructors
| Name | Subject | Notes / availability |
|---|---|---|
| Prof. Lim Cheng Hock | CS | Prefers **no Friday** teaching |
| Dr. Nurul Aisyah Binte Rahman | CS / AI | Mornings preferred |
| Assoc. Prof. Rajesh Kumar | MA | — |
| Dr. Chen Yong Xin | PC | Mornings only (lab prep afternoons) |
| Dr. Siti Nadiah Bte Osman | IS | — |

### 3.9 Courses (Instructional Offerings → Config → Subparts → Classes)
All under a single **Standard** configuration each. `itype` = instructional type.

| Course | Title | Subpart (itype) | # Classes | Sec limit | Time pattern | Room requirement | Instructor |
|---|---|---|---|---|---|---|---|
| **CS1010** | Programming Methodology | Lecture | 1 | 300 | Lecture 2h | Type = Lecture Theatre (`LT-19`) | Prof. Lim |
| | | Tutorial | 4 | 30 | Tutorial 1h | Tutorial/Seminar room | — |
| | | Lab | 4 | 30 | Lab 2h | Feature = **Workstations** (Computer Labs) | — |
| **CS2040** | Data Structures & Algorithms | Lecture | 1 | 200 | Lecture 2h | Type = Lecture Theatre (`LT-27`) | Dr. Nurul |
| | | Tutorial | 3 | 30 | Tutorial 1h | Tutorial room | — |
| | | Lab | 3 | 30 | Lab 2h | Feature = Workstations | — |
| **CS3243** | Introduction to AI | Lecture | 1 | 120 | Lecture 2h | Lecture Theatre / large Seminar | Dr. Nurul |
| | | Tutorial | 2 | 45 | Tutorial 1h | Seminar/Tutorial room | — |
| **MA1521** | Calculus for Computing | Lecture | 1 | 250 | Lecture 2×1h | Type = Lecture Theatre (`LT-19`) | A/P Rajesh |
| | | Tutorial | 5 | 25 | Tutorial 1h | Tutorial room | — |
| **PC1201** | Physics for Engineers | Lecture | 1 | 150 | Lecture 2h | Lecture Theatre | Dr. Chen |
| | | Lab | 4 | 24 | Lab 2h | Feature = **Lab Benches** (`S16-0436`) | — |
| **IS1103** | Digital Ethics | Seminar | 2 | 45 | Seminar 3h | Seminar Room (`COM1-0201`) | Dr. Siti |

Section numbering suggestion: Tutorials `T01…`, Labs `B01…`, Seminars `S1/S2`.

### 3.10 Preferences to set (soft-first — this is what the solver optimizes)
- **CS1010 Lecture** — Room type **Required = Lecture Theatre**; **Preferred** time Mon/Wed 09:00;
  building **COM1 preferred** (near the tutorials). Instructor Prof. Lim → **Prohibited: Friday**.
- **CS1010 Labs** — Room feature **Required = Workstations**; **Preferred** afternoons.
- **CS2040 Labs / CS1010 Labs** — Room group **Preferred = Computer Labs**.
- **PC1201 Labs** — Room feature **Required = Lab Benches** (only `S16-0436` qualifies).
- **MA1521 Lecture** — **Preferred** morning; date pattern **Full Term**.
- **Instructor availability** — Dr. Chen **Preferred: mornings** on all `PC` classes.

### 3.11 Distribution Preferences (cross-class rules)
| Distribution Type | Level | Applies to |
|---|---|---|
| **Same Room** | Preferred | The 4 **CS1010 Lab** classes (keep labs in one room) |
| **Back-To-Back** | Preferred | **CS2040 Lecture** + its Tutorials |
| **Different Time** (Same Period → Prohibited) | Required | **CS1010 Lecture** ↔ **MA1521 Lecture** (shared cohort must not clash) |
| **Spread In Time** | Preferred | All **MA1521 Tutorials** |

Set these at **Courses → Input Data → Distribution Preferences** (`distributionPrefs.action`).

### 3.12 (Optional) Examination data
| Exam | Course | Type | Length | Seating |
|---|---|---|---|---|
| CS1010 Final | CS1010 | Final | 120 min | Normal |
| CS2040 Final | CS2040 | Final | 120 min | Normal |
| MA1521 Final | MA1521 | Final | 120 min | Exam seating |

Exam periods span **2026-11-16 → 2026-11-27** (§3.1). Exam room availability, distribution
preferences between exams, and PDF reports are covered in §6.

---

## 4. Build the MBIT session (do the steps in order)

Start from a **blank** database (`schema.sql` + `blank-data.sql`). Each step names its **acting
user** and depends on the ones before it. You can enter data by hand (good for the narrative)
**or** bulk-load via **Data Exchange** (§7) — interchangeable.

### Step 1 — Create the Academic Session — **[User: `admin` — System Administrator]**
Log in as `admin` / `admin`; **Change Role → System Administrator**. Then **Administration →
Academic Sessions → Academic Sessions** (`sessionList.action`) → **Add**, using **§3.1**. Set
**Status = `Input Data Entry`**. Date rules enforced: session end after begin; classes end
within the session; event end after event begin.

> **Session Status controls everything downstream.** Seeded statuses (from `blank-data.sql`):
> `Initial Data Load` → **`Input Data Entry`** (edit) → **`Timetabling`** (solve) →
> **`Timetable Published`** (commit + public views) → `Session Finished`. Catalog at
> **Administration → Other → Status Types** (`deptStatusTypeEdit.action`).

### Step 2 — Departments, then Subject Areas — **[User: `admin`]**
1. **Departments** (`departmentList.action` / Angular `departments`) — create the four departments
   from **§3.2**.
2. **Subject Areas** (`subjectList.action` / `admin?type=subjectArea`) — create the nine subject
   areas from **§3.3** and **assign each to its department**.

### Step 3 — Solver Group (assign departments) — **[User: `admin`]**
**Academic Sessions → Solver Groups** (`solverGroupEdit.action`) — create **`MBIT-Core`** and assign
**School of Computing (0100)** + **School of Science (0400)** (§3.4). *No departments in a solver
group ⇒ nothing to solve.*

### Step 4 — Timetable Managers & roles — **[User: `admin`]**
**Academic Sessions → Managers** (`timetableManagerList.action`). Create the two manager accounts
from **§2.1** and assign each its role + departments:
- **Tan Wei Ling** → role *Department Schedule Manager*, departments **0100 + 0400**.
- **Priya d/o Menon** → role *Examination Manager*, departments **0100 + 0400**.

The chain that makes solving possible: *authenticated user → manager → role with solve rights →
assigned departments → those departments' solver group*. From here, `admin` can **Switch User**
(§2.2) into either manager to perform their steps.

### Step 5 — Buildings & Rooms (+ types / features / groups) — **[User: `admin`]**
1. **Buildings** (`buildings`) — create `LT`, `COM1`, `COM2`, `S16` (§3.5).
2. **Administration → Other → Room Types / Room Feature Types** (`admin?type=roomType|featureType`) —
   ensure the types and features from §3.5 exist.
3. **Rooms** (`rooms`) — create the 8 rooms with **capacity + building + room type**, then tag their
   **features** and **groups** (§3.5).
4. **Travel Times** (`traveltimes`) — add the COM2 ↔ LT/S16 walk time.

### Step 6 — Date Patterns, Time Patterns, Exact Time — **[User: `admin`]**
**Administration → Academic Sessions →**
- **Date Patterns** (`datePatternEdit.action`) — create **Full Term** (weeks 1–13, exclude recess),
  **First Half**, **Second Half** (§3.6).
- **Time Patterns** (`timePatternEdit.action`) — create the five patterns in **§3.7** and tick the
  preferred start times.
- **Exact Time** (`exactTimeEdit.action`) — leave defaults unless you need exact-minute rules.

These must exist **before** classes can carry time/date preferences.

### Step 7 — Instructors — **[User: *Wei Ling* — Schedule Manager]** *(admin: Switch User → Tan Wei Ling)*
**Courses → Input Data → Instructors** (`instructorSearch.action`) — add the five instructors from
**§3.8** to departments 0100/0400. (*Instructional Types* Lecture/Tutorial/Lab/Seminar are seeded
reference data the Administrator manages at **Administration → Other → Instructional Types**,
`itypeDescList.action`.)

### Step 8 — Instructional Offerings, Configurations, Subparts, Classes — **[User: *Wei Ling*]**
**Courses → Input Data → Instructional Offerings** (`instructionalOfferingSearch.action`). For each
course in **§3.9**:
1. **Make it offered** for the term; set the **course/offering limit** (= sum of section limits) and
   coordinator.
2. Add the **Standard Configuration**.
3. Add **Scheduling Subparts** with the right **instructional type** (Lecture / Tutorial / Lab / Seminar).
4. Create the **Classes** (sections) with **section number** and **limit** per §3.9.
5. Assign **instructors** to the lecture/seminar classes.

> "Arrange Hours" classes (no time pattern) won't appear on the grid by time — expected.

### Step 9 — Preferences (§3.10) and Distribution Preferences (§3.11) — **[User: *Wei Ling*]**
On each class/subpart set **Time / Room / Building / Feature / Group** preferences and the **Date
Pattern** per §3.10. Then add the cross-class **Distribution Preferences** from §3.11 at
`distributionPrefs.action` (catalog at **Administration → Solver → Distribution Types**,
`distributionTypeList.action`, maintained by `admin`).

> **Golden rule for a clean demo:** prefer **soft** preferences (*Preferred / Discouraged*) over
> **hard** ones (*Required / Prohibited*). Too many hard constraints ⇒ unassigned classes. The only
> hard constraints in this dataset are the room *type/feature* requirements and the CS1010↔MA1521
> no-clash rule — all well justified.

### Step 10 — Flip the session to `Timetabling` — **[User: `admin`]** *(Switch User back to admin)*
**Academic Sessions → Academic Sessions** → edit MBIT → **Status = `Timetabling`**. This unlocks the
solver (`canTimetable()`) for the managers. You're ready to solve → **§5**.

---

## 5. Run the solver (Load → Solve → Review → Save → Commit) — **[User: *Wei Ling* — Schedule Manager]**

*(admin: Switch User → Tan Wei Ling.)* **Courses → Course Timetabling → Solver** (`solver`,
parameter `type=course`). Backed by `SolverPageBackend` + `CourseTimetablingSolverService`
(engine: `TimetableSolver`, cpsolver).

1. **Pick a Configuration** — start with **`Default`** (seeded presets: `Interactive`, `Check`,
   `Default`; administered by `admin` at `solverSettings.action`).
2. **Load** (inspect first) **or Start** (load **and** optimize, `General.StartSolver=true`). Select
   solver group **`MBIT-Core`**. Watch the **progress bar** and objective drop.
3. **Control while solving:** **Stop** (pause), **Save Best** / **Restore Best**, **Reload** (after a
   preference change).
4. **Review before committing** (from **Courses → Course Timetabling**):
   - **Timetable Grid** (`timetableGrid`) — the room/time grid.
   - **Assigned / Not-assigned Classes** (`assignedClasses` / `notAssignedClasses`) — aim for zero unassigned.
   - **Conflict Statistics / CBS** (`cbs`) — what drives the cost and why classes are unassigned.
   - **Solver Log** (`solverlog?type=course`), **Reports** (`solutionReports`), **Changes**
     (`solutionChanges`), **History** (`assignmentHistory`).
5. **Save** — **Save** (current) or **Save As New** (keeps prior solutions for comparison — ideal in a demo).
6. **Commit** — **Save & Commit** / **Save As New & Commit** from the solver, or **Courses → Course
   Timetabling → Saved Timetables** (`listSolutions`) → select → **Commit**. Commit requires a status
   that allows it (`DepartmentStatusType.canCommit()`).
7. **Publish** — **[User: `admin`]** set **Status = `Timetable Published`** to expose the committed
   timetable to everyone and enable downstream student sectioning + exams/events.

**Sanity check for MBIT:** with the §3 dataset every class should assign. If CS1010 labs pile onto one
room, confirm both `COM2-0108` and `COM2-0110` carry the **Workstations** feature; if MA1521 and
CS1010 lectures fight for `LT-19`, that's the **Different Time** rule working — give one a soft
building/time nudge or a second theatre.

---

## 6. (Optional) Examination timetabling — **[User: *Priya* — Examination Manager]**

*(admin: Switch User → Priya Menon.)* Once the course timetable is committed, the same session can
drive **examinations**. These screens are now Angular (verified against `:8080`):

1. **Create exams** — **Examinations → Examinations** (`examList.action` → `/examinations-list`): add
   the exams from **§3.12** (course, **Final** type, **120 min**, seating). Detail/edit at
   `/exam-detail/:id` and `/exam-edit/:id`.
2. **Exam distribution preferences** — **`examDistributionPrefs.action` → `/exam-distribution-prefs`**:
   group exams that must share/avoid a period, e.g. *Same Period → Prohibited* between **CS1010 Final**
   and **MA1521 Final** (shared cohort). Full add/edit/delete with a subject→course→exam picker.
3. **Room availability** — **`roomAvailability.action` → `/exam-room-availability`**: check which rooms
   are free during the exam periods (availability + compare-with-assignments modes).
4. **Solve exams** — the exam **Solver** (`solver?type=exam`) assigns periods/rooms; review with the
   exam grid / assigned / unassigned screens.
5. **PDF reports** — **`examPdfReport.action` → `/exam-pdf-reports`**: pick reports (Schedule by
   Course/Period/Room, conflict reports, verification), an output format (PDF/CSV/XLS), the exam type
   and subject scope, then **Generate**. The job runs asynchronously; download the output from the
   queue table when it finishes.

> Enabling the exam period and exam status on the session is an **Administrator** action
> (session/status setup); running the exam workflow above is the **Examination Manager**'s.

---

## 7. Bulk-load MBIT via Data Exchange (optional) — **[User: `admin`]**

**Administration → Academic Sessions → Data Exchange** (`dataImport.action`) imports XML; the importer
is chosen by the XML **root element** (`DataExchangeHelper`). DTDs and ready-to-adapt examples live in
`Documentation/Interfaces/` and `Documentation/Interfaces/Examples/`. Recommended load order:

1. `session` / `sessionSetup` → create the session + reference data (statuses, patterns)
2. `departments` → the four MBIT departments
3. `subjectAreas` → the nine subject areas
4. `buildingsRooms` → buildings, rooms, types, features, groups
5. `offerings` → `courseOfferingsWithMeetings.xml` — the six courses, configs, subparts, classes
6. `preferences` → time/room/distribution preferences
7. `traveltimes`, `roomSharing` → the COM2↔LT walk time, sharing
8. `timetable` (root `coursett`) → import an **already-solved** timetable to skip the solve

Copy an example file, retarget it to the MBIT codes in §3, and import. This is the fastest way to
stand up a *custom* demo university without hand-entering every screen.

---

## 8. Suggested 15-minute demo script

1. **Frame it** (1 min) — **[narrator]**: "Tymora builds a conflict-free class timetable for MBIT
   from courses, rooms, instructors, and preferences." Show the concept table (§0) and the user
   matrix (§2.3).
2. **The inputs** (4 min) — **[as *Wei Ling*]**: Open **Instructional Offerings** → **CS1010**'s
   Config → Subparts → Classes. Show the **Lab** classes' **Workstations** requirement and the
   **Distribution Preference** (CS1010 ↔ MA1521 must not clash). Show **Rooms** with
   capacities/features (§3.5).
3. **The engine** (5 min) — **[as *Wei Ling*]**: **Solver** → **Default** → **Start** on
   **MBIT-Core**. Narrate the progress bar and objective dropping. **Save Best** when it settles.
4. **The result** (3 min) — **[as *Wei Ling*]**: **Timetable Grid** (conflict-free), **Not-assigned
   Classes** (empty), **CBS** to explain trade-offs, **Reports** for a summary.
5. **Publish + exams** (2 min): **Save As New & Commit** (*Wei Ling*), then **[as `admin`]** flip to
   **Timetable Published** and show a public view; mention the exam pass (§6, *Priya*) builds on the
   committed timetable.

---

## 9. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| A data screen is **read-only** | The **session status** doesn't allow editing (`admin` sets it to `Input Data Entry`), **or** you're acting as the wrong user — use **Switch User** to the manager who owns that department. |
| **Solver menu / Load disabled** | Session status must be **`Timetabling`** (`admin`); confirm you're acting as *Wei Ling* whose role has the `Solver`/`CourseTimetabling` right. |
| **"Nothing to load" / empty solver** | Departments 0100/0400 aren't in solver group **MBIT-Core**, or *Wei Ling* isn't assigned them. Fix Steps 3–4. |
| **Access denied on an action** | Wrong active role — **Change Role**, or (as admin) **Switch User** into the manager the action needs (§2.3 matrix). |
| **Many Not-assigned Classes** | Too many **hard** (Required/Prohibited) preferences, or rooms too small/scarce. Relax to soft; check capacities (CS1010 lecture needs a 300-seat theatre = `LT-19`); read **CBS**. |
| **CS1010 labs won't place** | Both `COM2-0108` and `COM2-0110` must carry the **Workstations** feature and be in the **Computer Labs** group; the Lab subpart needs the feature as **Required**. |
| **CS1010 & MA1521 lectures clash** | That's the **Different Time / Same Period-Prohibited** rule (§3.11). Give one lecture a soft time/building nudge, or add a second theatre. |
| **Can't Commit** | Session status lacks the **Commit** capability — `admin` moves it toward **`Timetable Published`**. |
| **Classes missing from the grid** | "Arrange Hours" classes have no time pattern — expected; they don't show by time. |

---

## 10. Quick reference — screens, actions & who runs them

| Task | Menu path | Action / page | Who |
|---|---|---|---|
| Academic Sessions | Administration → Academic Sessions | `sessionList.action` | `admin` |
| Status Types | Administration → Other | `deptStatusTypeEdit.action` | `admin` |
| Departments | Administration → Academic Sessions | `departmentList.action` / `departments` | `admin` |
| Subject Areas | Administration → Academic Sessions | `subjectList.action` / `admin?type=subjectArea` | `admin` |
| Solver Groups | Administration → Academic Sessions | `solverGroupEdit.action` | `admin` |
| Managers | Administration → Academic Sessions | `timetableManagerList.action` | `admin` |
| Switch User (Chameleon) | (top bar) Switch User | `chameleon.action` | `admin` |
| Change Role | Log Out ▸ Change Role | `selectPrimaryRole.action` | any |
| Buildings | Administration → Academic Sessions | `buildings` | `admin` |
| Rooms / Features / Groups | Courses → Input Data | `rooms` / `roomfeatures` / `roomgroups` | `admin` |
| Date / Time / Exact Time | Administration → Academic Sessions | `datePatternEdit.action` / `timePatternEdit.action` / `exactTimeEdit.action` | `admin` |
| Instructional Types | Administration → Other | `itypeDescList.action` | `admin` |
| Distribution Types | Administration → Solver | `distributionTypeList.action` | `admin` |
| Solver Configurations | Administration → Solver | `solverSettings.action` | `admin` |
| Instructors | Courses → Input Data | `instructorSearch.action` | *Wei Ling* |
| Instructional Offerings | Courses → Input Data | `instructionalOfferingSearch.action` | *Wei Ling* |
| Classes | Courses → Input Data | `classSearch.action` | *Wei Ling* |
| Distribution Preferences | Courses → Input Data | `distributionPrefs.action` | *Wei Ling* |
| Reservations | Courses → Input Data | `reservations` | *Wei Ling* |
| Solver (course) | Courses → Course Timetabling | `solver?type=course` | *Wei Ling* |
| Saved Timetables (Commit) | Courses → Course Timetabling | `listSolutions` | *Wei Ling* |
| Timetable Grid | Courses → Course Timetabling | `timetableGrid` | *Wei Ling* |
| Assigned / Not-assigned | Courses → Course Timetabling | `assignedClasses` / `notAssignedClasses` | *Wei Ling* |
| Conflict Statistics (CBS) | Courses → Course Timetabling | `cbs` | *Wei Ling* |
| Solution Reports | Courses → Course Timetabling | `solutionReports` | *Wei Ling* |
| Examinations list | Examinations | `examList.action` → `/examinations-list` | *Priya* |
| Exam Distribution Prefs | Examinations | `examDistributionPrefs.action` → `/exam-distribution-prefs` | *Priya* |
| Exam Room Availability | Examinations | `roomAvailability.action` → `/exam-room-availability` | *Priya* |
| Exam PDF Reports | Examinations | `examPdfReport.action` → `/exam-pdf-reports` | *Priya* |
| Data Exchange (bulk load) | Administration → Academic Sessions | `dataImport.action` | `admin` |

---

## 11. Related docs in this repo
- `docs/user-guides/administrator.md` — standing up a new session (authoritative ordering).
- `docs/user-guides/department-schedule-manager.md` — set-up → preferences → solve → review → commit.
- `docs/user-guides/glossary.md` — every core concept.
- `docs/DEVELOPER_ONBOARDING.md` — stack, DB seed files, auth model, how to run (Docker/Maven).
- `Documentation/Interfaces/` — Data Exchange DTDs and `Examples/` for every import format.
