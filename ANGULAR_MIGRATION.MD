# UniTime → Angular Migration: Screen Inventory & Path

> Companion to [`docs/angular-migration-roadmap.md`](docs/angular-migration-roadmap.md), which covers the *architecture and strategy*. This file is the **operational inventory**: every screen, its current tech, the backend it calls, how ready that backend is for a JSON facade, and which migration wave it belongs to.

- **Constraint:** backend logic stays as-is; **no GWT client code is changed**. Angular talks to a new *additive* JSON facade that reuses the existing 152 GWT-RPC command beans and 4 classic `RemoteService`s.
- **Pattern:** strangler-fig. Angular is hosted **same-origin** (reuses Spring Security session + CSRF). Each screen is migrated, then routed-past — nothing is deleted from GWT until Phase 3.

---

## 1. How to read the inventory

Each screen row carries:

| Column | Meaning |
|--------|---------|
| **Screen** | User-facing screen name |
| **Tech** | `GWT` = GWT single-page screen · `Struts` = legacy Struts action + JSP |
| **Entry** | Route/page id (`Pages` enum value) or `*.action` |
| **Backend call** | `RPC` = GWT-RPC command bean · `RemoteSvc` = classic GWT `RemoteService` · `Struts` = server-side action logic (HTML today) · `API` = an existing `api/connectors/*` JSON connector also exists |
| **Facade readiness** | 🟢 direct reuse via facade · 🟡 reuse but heavy/async or partial · 🔴 no JSON-capable backend yet — needs a new command bean (additive) before UI work |
| **Wave** | Migration wave (see §4) |

Backend-mechanism reference:
- **RPC** → `POST /api/rpc/{RequestName}` → `GwtRpcServlet.execute(...)` → existing `@GwtRpcImplements` bean. **🟢 ready.**
- **RemoteSvc** (only 4: `SectioningService`, `CurriculaService`, `ReservationService`, `LimitAndProjectionSnapshotService`) → wrap each with a thin facade endpoint. **🟢 ready.**
- **Struts** → server-rendered HTML; **no JSON contract**. Either superseded by a GWT screen, or needs a new command bean. **🔴/🟡.**
- **API** → an existing `api/connectors/*` connector already emits JSON for this domain — usable directly and a proof the DTOs serialize.

---

## 2. GWT screens (57 pages — all 🟢 unless noted)

### Rooms & Facilities  — *pilot domain*
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Rooms | GWT | `rooms` | RPC + **API** (`RoomsConnector`) | 🟢 | 1 |
| Room Groups | GWT | `roomgroups` | RPC | 🟢 | 1 |
| Room Features | GWT | `roomfeatures` | RPC | 🟢 | 1 |
| Buildings | GWT | `buildings` | RPC + **API** (`BuildingsConntector`) | 🟢 | 1 |
| Travel Times | GWT | `traveltimes` | RPC | 🟢 | 1 |
| Room Pictures | GWT | `roompictures` | RPC (+ `/picture` servlet) | 🟢 | 1 |
| Edit Room Availability | GWT | `roomavailability` | RPC | 🟢 | 1 |
| Event Room Availability | GWT | `availability` | RPC | 🟢 | 1 |

### Administration & data (simple CRUD)
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Administration (simple-edit tables) | GWT | `admin` | RPC (`AdminBackend`) | 🟢 | 2 |
| Departments | GWT | `departments` | RPC | 🟢 | 2 |
| Scripts | GWT | `scripts` | RPC (+ `/taskOutput` servlet) + **API** (`ScriptConnector`) | 🟢 | 2 |
| Tasks | GWT | `tasks` | RPC | 🟢 | 2 |
| Change Password | GWT | `password` | RPC | 🟢 | 2 |
| Instructor Attributes | GWT | `instructorattributes` | RPC | 🟢 | 2 |

### Curricula
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Curricula | GWT | `curricula` | **RemoteSvc** (`CurriculaService`) + **API** (`CurriculaConnector`) | 🟢 | 3 |
| Curriculum Projection Rules | GWT | `curprojrules` | **RemoteSvc** (`CurriculaService`) | 🟢 | 3 |

### Reservations
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Reservations | GWT | `reservations` | **RemoteSvc** (`ReservationService`) | 🟢 | 3 |
| Edit Reservation | GWT | `reservation` | **RemoteSvc** (`ReservationService`) | 🟢 | 3 |

### Course offerings & classes
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Edit Course Offering | GWT | `courseOffering` | RPC | 🟢 | 4 |
| Instructional Offering Config | GWT | `instrOfferingConfig` | RPC | 🟢 | 4 |
| Multiple Class Setup | GWT | `multipleClassSetup` | RPC | 🟢 | 4 |
| Assign Class Instructors | GWT | `assignClassInstructors` | RPC | 🟢 | 4 |

### Instructor scheduling
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Assigned Teaching Requests | GWT | `teachingRequests` | RPC | 🟢 | 5 |
| Teaching Assignments | GWT | `teachingAssignments` | RPC | 🟢 | 5 |
| Teaching Assignment Changes | GWT | `teachingAssignmentChanges` | RPC | 🟢 | 5 |
| Setup Teaching Requests | GWT | `setupTeachingRequests` | RPC | 🟢 | 5 |
| Instructor Survey | GWT | `instructorSurvey` | RPC + **API** (`InstructorScheduleConnector`) | 🟢 | 5 |

### Events
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Events | GWT | `events` | RPC + **API** (`EventsConnector`) | 🟢 | 6 |
| Event Timetable | GWT | `timetable` | RPC | 🟢 | 6 |
| Room Timetable | GWT | `roomtable` | RPC | 🟢 | 6 |
| Classes (event view) | GWT | `classes` | RPC | 🟢 | 6 |
| Examinations (event view) | GWT | `exams` | RPC | 🟢 | 6 |
| Personal Timetable | GWT | `personal` | RPC | 🟢 | 6 |

*(Calendar/iCal export stays as the existing `/calendar` servlet — consumed by URL, not migrated.)*

### Students & sectioning
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Scheduling Assistant | GWT | `sectioning` | **RemoteSvc** (`SectioningService`) + **API** (`OnlineStudentSchedulingConnector`) | 🟡 (real-time-ish) | 7 |
| Student Course Requests | GWT | `requests` | **RemoteSvc** (`SectioningService`) | 🟡 | 7 |
| Advisor Course Requests | GWT | `acrf` | **RemoteSvc** (`SectioningService`) | 🟡 | 7 |
| Online Scheduling Dashboard | GWT | `onlinesctdash` | **RemoteSvc** (`SectioningService`) | 🟡 | 7 |
| Batch Scheduling Dashboard | GWT | `batchsctdash` | **RemoteSvc** (`SectioningService`) | 🟡 | 7 |
| Batch Sectioning Reports | GWT | `sctreport` | RPC | 🟢 | 7 |
| Online Sectioning Reports | GWT | `onlinereport` | RPC | 🟢 | 7 |
| Online Sectioning Test | GWT | `sectioningtest` | **RemoteSvc** (`SectioningService`) | 🟡 | 7 |
| Published Sectioning Solutions | GWT | `publishedSolutions` | RPC | 🟢 | 7 |

### Solver & timetabling — *last; needs async/poll bridge*
| Screen | Tech | Entry | Backend call | Readiness | Wave |
|--------|------|-------|--------------|-----------|------|
| Solver | GWT | `solver` | RPC **async** | 🟡 async | 8 |
| Solver Log | GWT | `solverlog` | RPC **async** | 🟡 async | 8 |
| Timetable Grid | GWT | `timetableGrid` | RPC | 🟢 | 8 |
| Assigned Classes | GWT | `assignedClasses` | RPC | 🟢 | 8 |
| Not-assigned Classes | GWT | `notAssignedClasses` | RPC | 🟢 | 8 |
| Suggestions | GWT | `suggestions` | RPC | 🟢 | 8 |
| Conflict-based Statistics | GWT | `cbs` | RPC | 🟢 | 8 |
| Solution Changes | GWT | `solutionChanges` | RPC | 🟢 | 8 |
| Assignment History | GWT | `assignmentHistory` | RPC | 🟢 | 8 |
| Saved Timetables (List Solutions) | GWT | `listSolutions` | RPC | 🟢 | 8 |
| Solution Reports | GWT | `solutionReports` | RPC | 🟢 | 8 |
| Limit & Projection Snapshot | GWT | `limitAndProjectionSnapshot` | **RemoteSvc** (`LimitAndProjectionSnapshotService`) | 🟢 | 8 |
| Point-in-Time Data Reports | GWT | `pointInTimeDataReports` | RPC | 🟢 | 8 |

---

## 3. Legacy Struts + JSP screens (86 JSPs / 87 actions)

These have **no JSON contract today** — server-rendered HTML. For each, pick a disposition:
**(S)** superseded by an existing GWT screen (retire, just re-route) · **(W)** wrap action logic in a **new** command bean (additive backend) then build Angular · **(D)** defer.

### Course catalog / offerings / classes (`user/*`, disposition mostly **W**)
`instructionalOfferingSearch` · `instructionalOfferingDetail` · `instructionalOfferingModify` · `instructionalOfferingConfigEdit` · `crossListsModify` · `courseOfferingEdit` *(partly superseded by GWT `courseOffering`)* · `classSearch` · `classes` · `classDetail` · `classEdit` · `classInstructorAssignment` · `schedulingSubpartDetail` · `schedulingSubpartEdit` · `classAssignmentsReportSearch` · `distributionPrefs` · `dispDatePattern`
→ **Wave 4** (aligns with GWT offerings domain).

### Instructors (`user/instructor*`, disposition **W**)
`instructorSearch` · `instructorDetail` · `instructorAdd` · `instructorInfoEdit` · `instructorPrefsEdit` · `instructorAssignmentPref` · `instructorListUpdate` · `managerSettings`
→ **Wave 5**.

### Examinations (`exam/*`, `user/exam*`, disposition **W**)
`examList` · `examEdit` · `examDetail` · `examGrid` · `assigned` · `unassigned` · `changes` · `cbs` · `assignmentReport` · `pdfReport` · `personalizedReport` · `examDistributionPrefs` · `roomAvailability` · `info`
→ **Wave 6/7** (pairs with Events + Solver reporting).

### Administration (`admin/*`, disposition mixed **S/W**)
`sessionList` · `sessionEdit` · `rollForwardSession` · `subjectList` · `subjectAreaEdit` · `departmentList`/`departmentEdit` *(superseded by GWT `departments` → **S**)* · `deptStatusTypeEdit` · `timetableManagerList`/`timetableManagerEdit` · `userEdit` · `applicationConfig` · `datePatterns`/`datePatternEdit` · `timePatterns`/`timePatternEdit` · `examPeriods`/`examPeriodEdit` · `exactTimeEdit` · `itypeDescList`/`itypeDescEdit` · `distributionTypeList`/`distributionTypeEdit` · `solverGroups`/`solverGroupEdit` · `solverSettings` · `dataImport` · `chameleon` · `lastChanges` · `accessStats` · `stats` · `hibernateStats` · `hibernateQueryTest`
→ **Wave 2** (admin) — many are simple CRUD; a few are dev/diagnostic and can be **D** deferred or dropped.

### Solver management / misc
`manageSolvers` (`tt/manageSolvers`) · `inquiry` · `ecbs` · `enrollmentAuditPdfReport` · `selectPrimaryRole` · `roleList`
→ **Wave 8** (solver) or **Wave 2** (role/session utilities).

### Framework / auth pages (mostly **S** — replaced by Angular shell)
`index` · `main` · `login`/`login2` · `logout` · `loginRequired` · `selectPrimaryRole` · `error`/`error-struts2` · `gwt`/`gwt2` (GWT host pages) · `header`/`layout` (Struts tiles) · `close`
→ **Wave 0/1** — replaced by the Angular shell + Spring Security login; keep server login endpoints.

---

## 4. Migration waves (summary & ordering)

| Wave | Theme | Screens | Why here | Gating work |
|------|-------|---------|----------|-------------|
| **0** | Foundations | shell, menu, auth, login, error | Everything depends on it | Facade keystone, TS-model generator, CSRF interceptor, router coexistence |
| **1** | Rooms & Facilities | 8 GWT | Self-contained, API-backed → best pilot | Prove table+edit+permission patterns |
| **2** | Administration & CRUD | 6 GWT + ~25 admin Struts | Reusable CRUD template; retire superseded screens | Decide S/W/D per admin action |
| **3** | Curricula + Reservations | 4 GWT (RemoteSvc) | Exercises the classic-RemoteService facade wrappers | Wrap `Curricula`/`Reservation` services |
| **4** | Offerings & Classes | 4 GWT + ~17 Struts | Core data-entry; largest legacy cluster | New command beans for Struts offering/class screens |
| **5** | Instructors & Teaching | 5 GWT + ~8 Struts | Depends on offerings | New command beans for instructor Struts screens |
| **6** | Events | 6 GWT + exam-event Struts | Mostly RPC-ready; calendar via existing servlet | — |
| **7** | Students & Sectioning | 9 GWT + exam reports | Real-time-ish; higher UX risk | Validate `SectioningService` under HTTP latency |
| **8** | Solver & Timetabling | 13 GWT + solver mgmt Struts | Most complex; long-running jobs | **Async/poll bridge** over `executeAsync`/`waitForResults`/`cancelExecution` |
| **9** | Cutover | — | Flip default to Angular; drop GWT from build | Remove GWT compile step (source stays), optionally retire Struts filter |

**Totals:** 57 GWT screens (🟢 ~46 direct-reuse, 🟡 ~11 async/real-time) + ~86 legacy Struts/JSP screens (mix of retire / wrap-in-command / defer).

---

## 5. The facade (build once, Wave 0)

New, additive Spring `@RestController` — **edits no existing file**:

```
POST   /api/rpc/{RequestName}          body = JSON of the GwtRpcRequest POJO
POST   /api/rpc/{RequestName}/async    → { executionId }
GET    /api/rpc/async/{executionId}    long-poll → result   (solver)
DELETE /api/rpc/async/{executionId}    cancel
GET    /api/rpc/service/{svc}/{method} thin wrappers for the 4 RemoteServices
```

Internals:
1. Registry of request classes, built at startup from the same beans `@GwtRpcImplements` registers.
2. `gson.fromJson(body, requestClass)` — reuse the `iName→name` naming strategy + ISO date adapters already in `GwtRpcServlet` (extract to a shared `GsonProvider`, or copy into the facade so no existing file changes).
3. `GwtRpcServlet.execute(request, appCtx, HttpSessionContext.getSessionContext(...))`.
4. `gson.toJson(response)`; map `GwtRpcException`/`PageAccessException`/`AccessDeniedException` → HTTP status (mirror `ApiServlet.checkError`).
5. Re-add `QueryLog` logging here so RPC analytics survive.

TypeScript models are **generated** from `gwt/shared/*` (~40 interfaces + nested requests/responses) — never hand-written.

---

## 6. Per-screen migration checklist (template)

For each screen, in wave order:

- [ ] Confirm backend call type (RPC / RemoteSvc / needs new command bean).
- [ ] Verify/extend the facade covers its request(s); add async endpoints if 🟡.
- [ ] Regenerate & commit TypeScript models for its DTOs.
- [ ] Build the Angular screen (route, component, data-table/edit, permission guards from response/menu).
- [ ] Reproduce validation, error, and empty states; wire i18n from existing `.properties`.
- [ ] Contract test (JSON ↔ POJO round-trip) + component test + e2e smoke.
- [ ] Add redirect: old `*.gwt`/`*.action` URL → new Angular route (bookmarks survive).
- [ ] Remove the screen from the menu's legacy target; route to Angular.
- [ ] Leave GWT/Struts source in place (untouched) until Wave 9.

---

## 7. Top risks (see roadmap §7 for full list)

1. **Gson (de)serialization of some DTOs** (polymorphism, enums, nested/cyclic) — spike across a sample of the 152 commands in Wave 0.
2. **"No backend change" strictness** — this plan assumes *additive facade allowed*; confirm before Wave 0.
3. **Legacy Struts screens with no command layer** (Waves 4–6) — the real long-pole; inventory S/W/D decisions early.
4. **Solver async semantics over HTTP** — load-test the async bridge before Wave 8.