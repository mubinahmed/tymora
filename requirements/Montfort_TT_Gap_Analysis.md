# Montfort Secondary School ITQ — Gap Analysis

**Source requirement:** `requirements/Montfort_TT.docx` — *Invitation to Quote (ITQ) for Cloud-Based Integrated Timetabling and Relief Management Software*, Montfort Secondary School (St. Gabriel's Foundation, Singapore). Contract base period 11 Sep 2026 – 10 Sep 2027 (two 1-year options).

**System assessed:** the current UniTime/"Tymora" codebase — the legacy GWT/Struts application plus the in-progress Angular 21 front end and the JSON-RPC backend facade added during the migration.

**Purpose:** map each ITQ requirement to (a) what the current code can already do, (b) what needs configuration/adaptation, and (c) the genuine gaps that would require new development.

---

## 1. Executive summary

UniTime is a mature, server-side **academic timetabling and examination-scheduling** engine built for **universities**. Its core scheduling technology (the `cpsolver` constraint solver), its data model (academic sessions, subject areas, rooms, instructors, distribution preferences), and its **event/room-booking module** cover a substantial part of the Montfort **Timetabling** and **Venue Management** scope — but with a university course-catalog framing that must be re-mapped onto a secondary-school "class × period × teacher" model.

The Montfort ITQ, however, is **two products in one**: an *intelligent timetabling + venue* system **and** a *daily relief / cover-management* system, both fronted by a *synchronised mobile app with push notifications*. UniTime provides essentially **none** of the relief-management, mobile-app, or push-notification functionality. Those are the dominant gaps.

| Scope area | Coverage of current code | Verdict |
|---|---|---|
| A. Timetabling — generation engine & constraints | Strong core; school-specific constraints partial | **Partial (adapt)** |
| A. Timetabling — collaborative/concurrent editing, audit | Concurrent access + change log exist; live co-editing does not | **Partial** |
| A. Venue configuration, auto-assignment, conflict prevention | Strong (rooms, features, room-in-time availability, solver) | **Good (adapt)** |
| A. General venue booking (non-teaching rooms, all staff) | Event Management module exists | **Good (adapt)** |
| A. Venue utilisation reporting | Partial (room usage data exists; report packaging thin) | **Partial** |
| A. Async server-side generation + completion notification | Server-side solver yes; email/push notify partial | **Partial** |
| **B. Relief Planning (entire module)** | **Not present** | **Gap (build)** |
| **C. Mobile app + push notifications** | **Not present** | **Gap (build)** |
| Training / support / MOE IT security / data relinquishment | Process & compliance, not product features | **Out of code scope** |

**Bottom line:** roughly the **Timetabling + Venue** half of the ITQ is achievable by adapting existing UniTime capability; the **Relief Planning** module and the **Mobile/push** layer are net-new products that the current code does not address.

---

## 2. Objectives & Outcomes (headline)

| # | ITQ objective | Current code | Gap |
|---|---|---|---|
| 1 | Intelligent & collaborative timetabling with integrated venue management (cloud, multi-admin real-time, ≤150 staff, configurable constraints, automated lesson-block fixing, real-time conflict detection, integrated venue booking preventing double-booking) | Core solver + rooms + preferences + committed-solution conflict checking all exist. Web-hosted (Tomcat/WAR). | Real-time *collaborative* multi-admin editing (simultaneous live edits) is not how UniTime works — it is solve-then-commit. "Automated lesson-block fixing" maps to fixed time/room assignments but needs a school-friendly UI. |
| 2 | Automated & equitable relief planning with conflict-free venue allocation (native relief module, full daily assignments ≤5 min, rules engine, digital handover) | — | **Entire relief module is a gap.** |
| 3 | Seamless mobile integration (synced app, personalised schedules, push notifications, remote admin) | — | **No mobile app, no push infrastructure.** |

---

## 3. Scope A — Timetabling

### 3.1 Collaborative environment & scalability

| Requirement | Current code | Assessment |
|---|---|---|
| **Multi-user concurrency** — simultaneous real-time create/edit without data conflicts; changes immediately visible to others | UniTime supports many concurrent authenticated users and role-scoped access. The solver works on a *working copy*; edits to the input data (offerings, rooms, preferences) are transactional (Hibernate). | **Partial.** Concurrent access to the app: yes. *Live collaborative editing of the same timetable with instant cross-user visibility and merge* is **not** the model — UniTime is load → solve → commit, not Google-Docs-style co-editing. Would need re-architecting or clear scoping. |
| **Version control / audit tracking of all modifications** | A change-log facility exists (`ChangeLog`) recording entity edits by user/time; solutions are versioned and can be saved/committed/reverted. | **Partial-to-good.** Entity-level audit exists; a full per-field "who changed what on the timetable" trail may need extension. |
| **~150 staff + admin accounts, consistent performance under full concurrent load** | UniTime routinely runs universities with thousands of users; 150 accounts is well within capacity. Roles/authorities model (`role × academic session`) supports per-staff accounts. | **Good.** Scale is not a concern; per-teacher self-service accounts need provisioning but the auth model supports it. |

### 3.2 Scheduling constraints (configurable)

UniTime expresses constraints as **distribution preferences**, **time/room preferences**, **instructor availability**, and solver parameters. Mapping to the ITQ list:

| ITQ constraint | Current code | Assessment |
|---|---|---|
| Max teaching hours per subject / per teacher / per student cohort, per day and week | Instructor time preferences + distribution constraints exist; explicit *max-hours-per-day/week* caps per teacher/cohort are not first-class school constraints. | **Partial.** Achievable via distribution constraints / prohibited times, but not as a simple "max N periods/day" knob — would need a school constraint type. |
| Max consecutive teaching hours (teachers & students) | Distribution preference types include back-to-back / spread constraints. | **Partial.** Related constraints exist ("minimize back-to-back", spread-in-time); an exact "no more than N consecutive" cap may need a dedicated constraint. |
| Banding of subjects/classes by academic structure | Subject areas, course offerings, class instructional types; reservations/curricula group students. | **Adapt.** Concepts exist but framed for university curricula; needs school "banding/streaming" mapping. |
| Customisable cycle lengths (odd/even week, 10-day cycle) | **Date patterns** and **time patterns** support week-parity and multi-week cycles; UniTime handles alternating-week meeting patterns. | **Good (adapt).** Multi-day/parity cycles are natively representable via date/time patterns. |
| Equal distribution of subject periods across the cycle | "Spread in time" distribution constraint spreads meetings evenly. | **Good.** Directly supported. |
| Lessons conducted concurrently across multiple classes/groups | "Meet together" / same-time distribution constraints; cross-listed offerings. | **Good (adapt).** Supported as same-time constraints. |

### 3.3 Manual adjustment & lesson-block/card fixing

| Requirement | Current code | Assessment |
|---|---|---|
| Manual adjustment with **real-time conflict detection** (class/teacher/room clashes) with immediate validation alerts | The interactive solver / "Assigned Classes" and suggestions UI checks conflicts when a user reassigns a class; committed-solution checks flag clashes. | **Good (adapt).** Conflict checking on manual assignment is a core UniTime feature; alert UX needs school-friendly presentation. |
| **Fix lesson blocks to specific time slots** before and after generation; fixed blocks preserved by the engine and not displaced by optimisation | Hard time/room **preferences (Required/Prohibited)** pin a class; the solver respects "required" assignments and can lock committed classes. | **Good.** "Required" preferences + committed/locked classes achieve fixed blocks that the solver won't move. |

### 3.4 Venue assignment & booking

| Requirement | Current code | Assessment |
|---|---|---|
| Configure/manage a comprehensive venue inventory; assignments reflected across scheduling in real time | Full **Rooms** model: buildings, rooms, capacities, room features, room groups, room departments, availability. Managed via room screens (migrated to Angular in this project). | **Good.** Venue inventory is a first-class, mature part of the system. |
| **Pre-set conditions per venue** — subject/activity restrictions (e.g. Computer Lab → CPA, FCE Lab → Food & Nutrition) applied automatically during generation | **Room features** + **room groups** + room/course **preferences** let a class require a room feature/room; the solver enforces it. | **Good (adapt).** "Computer Lab for CPA" = a room feature required by the CPA class. Native mechanism; needs school configuration + simpler UI to express rules. |
| **Automated venue assignment with conflict prevention** (no double-booking across concurrent slots) | The solver assigns rooms and enforces **room-in-time** uniqueness (no two classes in one room at one time). | **Good.** Core solver guarantee. |
| **Manual venue override** with traceable, conflict-checked changes | Manual room reassignment via the interactive interface with conflict checks; change log records it. | **Good (adapt).** Supported; traceability via change log. |
| **General venue booking** — non-teaching rooms (Conference/Alumni Room) bookable by **all staff** when not scheduled | UniTime's **Event Management** module books rooms for events/meetings, respects class occupancy, and is open to event-manager roles. | **Good (adapt).** The events module is the natural home; would need to open booking to all-staff roles and a simpler booking UX. |
| **Venue utilisation report** — usage over periods, filter/summarise/export, spot underused rooms | Room usage is queryable; some usage/statistics reports exist, plus exportable grids. A packaged "utilisation report" is not a headline feature. | **Partial.** Underlying data exists; a dedicated filterable/exportable utilisation report would need building. |

### 3.5 Processing & automation

| Requirement | Current code | Assessment |
|---|---|---|
| **Server-side generation** on cloud infra, independent of user device | The solver runs **server-side** on the solver server (`cpsolver`), not in the browser. | **Good.** Exactly how UniTime works. |
| **Asynchronous processing** — start, disconnect, solve continues in background | Solver jobs run in a background solver-server thread; users can leave and return; progress is polled. | **Good.** Native. |
| Auto-notify on completion (email/push) | Solver status is shown in-app; automatic **email/push on completion** is not a standard feature. | **Partial.** In-app status yes; email/push notification would need adding. |
| **Timetable publication** — on approval, push notifications to all staff with their timetables | Committing a solution publishes it; personal timetables are viewable in-app. **Push** notification on publish does not exist. | **Partial.** Publication yes; push distribution is a gap (ties to mobile/notification layer). |

---

## 4. Scope B — Relief Planning  ⟶  **Net-new module (primary gap)**

> **Build status (MVP core delivered):** an initial working module now exists in the codebase —
> `absence_reason`/`staff_absence`/`relief_assignment`/`relief_configuration` entities (dbupdate v274),
> a greedy rule-ranked **relief-generation engine** (`server/relief/ReliefGenerationHelper.java`),
> JSON-RPC endpoints (`StaffAbsence`/`ReliefBoard`/`ReliefConfig`/`ReliefReport`), and Angular screens
> (`features/relief/*`: board, staff-absences, my-leave, config, report) plus an Absence Reasons admin
> lookup. **Delivered:** leave submission (admin + teacher self-service), configurable absence reasons,
> autonomous daily generation, the rules engine (weekly cap, exclude-non-teaching, exemptions, academic
> continuity/same-dept), oversight board with manual reassignment + conflict checks, and absence/relief
> reporting with CSV export. **Still deferred:** digital handover, class-absence auto-release, class
> merging, mobile/push, and the *scheduled emailed* daily report (on-screen + CSV report is delivered;
> scheduling is a fast follow-up via UniTime's PeriodicTask facility). Rows below marked ✅ are delivered.

UniTime has **no cover/relief/substitution module**. Every item below is a build, though several can *reuse* existing timetable, teacher, and venue data as inputs.

| Requirement | Current code | Assessment |
|---|---|---|
| Natively integrated relief module using **live** timetable + venue data | The data it would consume (class schedules, teacher assignments, room availability) exists and is queryable. | **Gap (build on existing data).** |
| **Leave reporting** — teachers submit relief requests via web/mobile portal | No absence/leave request feature. | **Gap.** |
| **Multi-category absence reasons** (Medical, Hospitalisation, PD, UPA, Official Duties, Child Care, No Pay, Reservist, Other), admin-configurable | No absence-reason model. | **Gap.** |
| **Autonomous relief generation** — full daily assignments, no manual transfers, **≤5 minutes** | No relief engine. (The general solver technology *could* be repurposed, but the relief allocation problem/rules are not modelled.) | **Gap.** |
| **Configurable rules engine** — weekly workload caps; administrative filtering (exclude non-teaching/part-time/special-duty); role-based exemptions (KP/HOD reduced or exempt); academic continuity (prefer teacher already teaching the class/subject); departmental & level grouping; venue suitability; class merging with capacity-aware venue | None of these relief-allocation policies exist. Teacher/department/subject data exists as raw inputs. | **Gap.** This is the heart of the ITQ and is entirely new work. |
| **Digital handover interface** — absent teacher uploads lesson objectives/materials/expectations to the relief teacher; instant delivery; direct teacher-to-teacher comms | No handover, file-sharing, or messaging feature. | **Gap.** |
| **Class Absence / Special Events** — mark whole class absent (exam, learning journey, excursion); auto-release teachers & venues back to the pool; dynamically re-pool released teachers | No such marking/release mechanism (the events module can occupy rooms but does not release teachers into a relief pool). | **Gap.** |
| **Administrative oversight** — real-time relief timetables + manual adjustment | No relief timetable to oversee. | **Gap.** |
| **Automated reporting** — absence trends, workload distribution, recurring patterns; filter/summarise/export | No relief/absence reporting. | **Gap.** |
| **Daily Staff Absence Report** — auto-generated & **emailed** to leaders/admin at a set time each day, tabulated (absentee counts; per-staff reason/duration; per affected lesson: period, timing, subject, classes, venue, relief teacher, assigner) | No such report or scheduled email. | **Gap.** |

---

## 5. Scope C — Mobile Integration & Administration  ⟶  **Net-new (primary gap)**

| Requirement | Current code | Assessment |
|---|---|---|
| **Native mobile app**, continuously synced with web across timetabling, relief, venue | No mobile app. The Angular front end is responsive web only. | **Gap.** |
| Teachers get **read-only personalised schedules** + venue on publication | Personal timetables are viewable in the web app. | **Partial (web only).** Data exists; mobile delivery + read-only personal view packaging is new. |
| **Push notifications** on any amendment (schedule/venue/relief) | No push infrastructure (no FCM/APNs, no notification service). | **Gap.** |
| Staff submit leave, view relief assignments & venues remotely; admins manage venue/relief on mobile | Depends on the (missing) relief module + mobile app. | **Gap.** |
| Digital handover shown on mobile with allocated venue | Depends on missing handover feature. | **Gap.** |

---

## 6. Non-functional / contractual (outside product-code scope)

These are procurement, compliance, and service obligations — not features of the timetabling code — but they gate acceptance and should be tracked by the bid team, not the engineering backlog:

| Requirement | Note |
|---|---|
| On-site training (user guides + one ≥1h session for ≤10 users) | Service deliverable; UniTime has extensive docs to adapt (`Documentation/`). |
| Technical support (phone/email/WhatsApp Mon–Fri 9–6; relief pre-8am, timetabling ≤3h) | Operational SLA, not code. |
| **MOE IT Security Specifications** (Appendix B, "School-managed Systems — Unclassified"), Statement of Compliance (Appendix C) | **Compliance gap to assess.** Cloud hosting, auth, data handling, logging must be reviewed against MOE spec. UniTime supports LDAP/CAS/OAuth2 SSO, role-based authz, and audit logging — a helpful baseline, but a formal gap review vs. Appendix B is required. |
| Data relinquishment & secure decommissioning on contract end (Clause 2; certified destruction report) | Hosting/ops process; needs a documented data-export + destruction procedure. |
| Cloud hosting, GeBIZ submission, InvoiceNow/Vendors@Gov, trial/sandbox URL | Procurement/hosting logistics. A hosted **sandbox URL** for evaluation is explicitly encouraged — the deployed demo instance could serve this. |

---

## 7. Consolidated gap register (build priorities)

**Reusable today (adapt & configure):**
1. Cloud-hosted timetabling engine (server-side, async solver) — *core strength.*
2. Venue inventory, room features/groups, subject-restricted rooms, conflict-free room assignment.
3. Fixed lesson blocks (required preferences / committed classes).
4. Multi-week / odd-even / N-day cycles (date & time patterns).
5. Even period distribution, same-time (concurrent) lessons.
6. Manual reassignment with conflict detection + change-log audit.
7. General room booking via the Event Management module.
8. Role-based accounts for ~150 staff (auth model scales).

**Needs adaptation / extension (moderate build):**
9. School-specific constraints: max hours per day/week per teacher/subject/cohort; max consecutive periods (new constraint types).
10. Subject "banding/streaming" mapping onto subject-area/curricula model.
11. Venue utilisation report (filter/summarise/export packaging).
12. Live co-editing & fuller per-field audit / version control.
13. Email/push notification on solve-completion and timetable publication.
14. MOE IT-security compliance review (Appendix B/C).

**Net-new modules (major build — the bulk of the differentiated ITQ value):**
15. **Relief/Cover Management** end to end: leave submission, configurable absence reasons, autonomous ≤5-min relief allocation engine, the full rules engine (workload caps, filtering, role exemptions, academic continuity, dept/level grouping, venue suitability, class merging), oversight UI, absence analytics.
16. **Daily Staff Absence Report** with scheduled email distribution.
17. **Digital handover** (materials upload + teacher-to-teacher delivery/messaging).
18. **Class-absence / special-event** marking with automatic teacher + venue release into the relief pool.
19. **Mobile app** with continuous sync and **push notifications**.

---

## 8. Recommendation

The current code is a **credible foundation for the Timetabling + Venue half** of the Montfort ITQ — the hard, mature part (a real constraint solver, a room model, cycle/pattern handling, conflict detection) is already in place and largely just needs a **secondary-school-friendly re-skin and a handful of new constraint types**. The migration work already done (Angular front end, room/session/solver-group screens, JSON-RPC facade) is directly leveraged here.

The **Relief Planning module and the Mobile/push layer are essentially greenfield** and represent the majority of the net-new engineering. They are also the requirements that most differentiate this ITQ from a generic timetabling tool, so a competitive response must plan explicit new development there rather than relying on existing UniTime capability.

A phased approach fits the contract: **Phase 1** adapt timetabling + venue + general booking onto the existing engine and stand up the sandbox URL; **Phase 2** build the relief module and daily absence reporting; **Phase 3** deliver the synced mobile app and push notifications — running the MOE security compliance review in parallel from the outset.
