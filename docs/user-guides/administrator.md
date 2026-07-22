# Administrator Guide

**Roles:** *System Administrator* (`Sysadmin`) and *Session Administrator*
(`Administrator`).

You are the person who sets up Tymora and keeps its foundations correct. Nothing
the other personas do is possible until the administrator has created the academic
session and the reference data it depends on.

- **System Administrator** — top-level, cross-session, technical control: roles &
  permissions, solver servers, application configuration, utilities, and
  everything a Session Administrator can do.
- **Session Administrator** — full administrative control *within a single
  academic session*: reference data, users, rooms, and data for that term.

> New to the terminology? Read the [Glossary](glossary.md) first.
> Everything below lives under the **Administration** menu unless noted.

---

## Your responsibilities at a glance

| Area | What you maintain |
|------|-------------------|
| Academic sessions | Create/roll-forward terms; set statuses and periods. |
| Organization | Departments, subject areas, solver groups. |
| Facilities | Buildings, rooms, room types/features/groups, travel times. |
| Scheduling reference data | Date patterns, time patterns, exact-time settings. |
| Academic structure | Academic areas, classifications, degrees, programs, majors, minors, concentrations. |
| People | Timetable managers, roles, permissions, instructor roles, student groups/accommodations/advisors. |
| System | Application configuration, logging, solver configurations, data exchange, utilities. |

---

## Getting started: standing up a new session

1. **Create the Academic Session** — Administration → Academic Sessions →
   Academic Sessions. Define the term, year, campus/initiative, and dates. *Or*
   use **Roll Forward Session** to copy structure from a previous term.
2. **Departments & Subject Areas** — create the departments that own courses, then
   the subject areas (course prefixes) under them.
3. **Solver Groups** — group the departments that should be timetabled together;
   these own the solutions.
4. **Buildings & Rooms** — enter buildings, then rooms with their type, capacity,
   features, and groups. Set room availability where needed.
5. **Date & Time Patterns** — define the meeting-time shapes (time patterns) and
   week/date ranges (date patterns) that classes may use.
6. **Academic structure** — academic areas, classifications, majors, etc. These
   feed curricula and student data.
7. **Statuses & Periods** — set the session's course/exam/event statuses (which
   control what each persona may do *right now* in the workflow) and examination
   periods.

Once this scaffold exists, hand off to the Department Schedule Managers,
Curriculum Managers, Exam Managers, and Event Managers.

---

## Managing users and access

Tymora access is role-based (see [Glossary → People & access](glossary.md)).

1. **Timetable Managers** (Administration → Academic Sessions → Managers) — create
   a manager record for each staff member and link them to the department(s) they
   handle.
2. **Assign roles** — give the manager one or more roles (e.g. *Department
   Schedule Manager*). The role's rights are what actually grant access.
3. **Roles & Permissions** (Administration → Other → Roles / Permissions) —
   *System Administrator only.* Create custom roles or adjust which rights a role
   carries. Rights map to features and even individual buttons.
4. **Change Role / Switch User** (Preferences menu) — switch among your own roles,
   or impersonate another user ("Chameleon") to troubleshoot what they can see.

### Built-in roles you will assign

| Role | For |
|------|-----|
| System Administrator | You / IT — global technical control. |
| Session Administrator | Per-session administrative owner. |
| Department Schedule Manager | Departmental course timetabling. |
| Examination Manager | Exam timetabling. |
| Event Manager | Room bookings & events. |
| Curriculum Manager | Curricula & projections. |
| Student Advisor | Advising / course recommendations. |
| View All | Read-only oversight across areas. |
| Instructor / Student | Tied to real people (often via integration). |
| Anonymous | Public, unauthenticated timetable lookup. |

---

## System-level tasks (System Administrator)

- **Application Configuration** (Administration → Defaults → Configuration) —
  global `tmtbl.*` settings, header/footer, feature toggles.
- **Solver management** (Administration → Solver) — manage solver servers,
  parameter groups/parameters, solver **Configurations**, and **Distribution
  Types** (the constraint catalog everyone else uses).
- **Data Exchange** (Administration → Academic Sessions → Data Exchange) — bulk
  XML import/export using the interfaces documented in
  `Documentation/Interfaces/`. Use this to integrate with a Student Information
  System.
- **Utilities** — Page/Access/Hibernate statistics, **Clear Cache**, **Test HQL**
  (ad-hoc reporting sandbox), and **Scripts** (saved automation).
- **Change Log** — audit trail of who changed what.
- **Point In Time Data / Snapshots** — capture the state of a session for
  historical reporting.

---

## End-of-term / maintenance workflow

1. Verify the current session's timetables are committed and exams/events closed.
2. **Roll Forward** to create the next session, choosing what to carry over
   (offerings, rooms, curricula, managers…).
3. Take a **Point In Time snapshot** of the closing session for reporting.
4. Review roles/permissions and manager assignments for the new term.
5. Adjust statuses to open the new session up to the appropriate personas.

---

## Tips & pitfalls

- **Statuses gate the workflow.** If a manager "can't edit" something, check the
  session/course/exam/event **status** before touching permissions.
- **Rights, not users, hold access.** To grant a capability, ensure the *role*
  carries the right, then ensure the user's *manager* has the role.
- **Reference data is shared and load-bearing.** Deleting a date/time pattern,
  room, or distribution type in use will affect existing offerings — check usage
  first.
- Use **Switch User** to reproduce a user's problem exactly rather than guessing.
