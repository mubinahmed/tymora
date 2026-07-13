# Department Schedule Manager Guide

**Role:** *Department Schedule Manager* (`Dept Sched Mgr`) — the largest functional
role in UniTime.

You build and maintain the **course timetable** for your department: what courses
run, how they are structured into classes, when and where each class meets, and
who teaches it. You enter the data, express your scheduling preferences, run the
**course solver**, and commit the resulting timetable.

> New to the terminology? Read the [Glossary](glossary.md) first — especially the
> Offering → Config → Subpart → Class hierarchy, preferences, and the solver.
> Your work lives under the **Courses** menu.

---

## The big picture

```
1. Set up offerings & classes   →   2. Express preferences   →
3. Run the solver               →   4. Review & fix conflicts →   5. Commit
```

You provide the *inputs*; the solver produces a *timetable*; you review it using
Conflict-Based Statistics and commit the best solution.

---

## 1. Set up your course data

All under **Courses → Input Data**.

1. **Instructional Offerings** (`Instructional Offerings`) — make each course
   *offered* this term. For each offering:
   - Set the offering's enrollment/limit and coordinator.
   - Add **Configurations** (alternative structures) if the course can be taken
     more than one way.
   - Under each configuration, define **Scheduling Subparts** (Lecture,
     Recitation, Lab…) and their instructional type.
   - Create the **Classes** (sections) under each subpart, with section numbers
     and enrollment limits.
   - **Cross-list** courses that share classes.
2. **Classes** (`Classes`) — search and edit the individual sections directly.
3. **Instructors** — assign teaching staff; view instructor details, attributes,
   and the **Instructor Survey** (their stated availability/preferences).
4. **Rooms** — review the rooms available to your department (features, groups,
   travel times) so your room preferences are realistic.

---

## 2. Express your scheduling preferences

The solver only produces good timetables if you tell it what "good" means.
Preferences use the scale *Required → Preferred → Neutral → Discouraged →
Prohibited* (see [Glossary](glossary.md#preferences--constraints)).

- **On each class:** preferred/required **time patterns**, **rooms**, buildings,
  **room features/groups**, and **date pattern**. Assign instructors here too.
- **Distribution Preferences** (`Distribution Preferences`) — constraints across
  multiple classes: e.g. *Same Room*, *Back-To-Back*, *Precedence*, *Spread In
  Time*, *Same Instructor*, *Can Share Room*. Pick a distribution **type** and a
  preference level, then list the classes it applies to.
- **Reservations** (`Reservations`) — hold seats for a student group, curriculum,
  course, or specific students so sectioning respects them.

> **Tip:** prefer expressing intent with *Preferred/Discouraged* over
> *Required/Prohibited*. Too many hard constraints leave the solver no room and
> produce unassigned classes.

---

## 3. Run the course solver

Under **Courses → Course Timetabling**:

1. **Solver** — load your department's input data, choose a solver
   **configuration** (set by the administrator), and start it. The solver assigns
   times and rooms honoring your preferences and constraints.
2. Watch progress; you can pause, adjust, and continue.
3. **Save** promising solutions (**Saved Timetables**) so you can compare
   alternatives.

There is also an **Instructor Scheduling** solver (Courses → Instructor
Scheduling) for assigning teaching requests to instructors, if your institution
uses it.

---

## 4. Review and fix

- **Timetable Grid** — visual week grid of assignments; the fastest way to eyeball
  the result.
- **Assigned Classes / Not-assigned Classes** — what got placed and what didn't.
- **Conflict Statistics (CBS)** — *why* classes are unassigned or conflicted. Work
  the top contributors first; usually the fix is relaxing an over-strict
  preference or freeing a room/time.
- **Changes / History** — what changed between solver runs.
- **Solver Log** and **Reports** — detail and printable/exportable summaries.

Iterate: adjust inputs (step 1–2) → re-run (step 3) → review (step 4).

---

## 5. Commit the timetable

When a solution is good, **commit** it. Committing makes it the official timetable
for the session — it becomes visible in public/instructor/student views and is the
basis for student sectioning and exam/event scheduling. You can uncommit and
recommit if further changes are needed (subject to session status).

---

## Auditing

**Courses → Course Audit** gives read-only access to the solver, CBS, logs, and
reports — useful for reviewing another manager's timetable or double-checking your
own without risk of changing it.

---

## Common pitfalls

- **Everything is session-scoped.** Confirm you're in the right academic session
  (and your department) before editing.
- **Can't edit an offering?** The session's **course status** (set by the
  administrator) may not currently allow data entry. Check status before assuming
  a permission problem.
- **Lots of unassigned classes?** Almost always over-constrained inputs — too many
  *Required*/*Prohibited* preferences, or reservations/limits that can't all be
  satisfied. Read the CBS.
- **"Arrange Hours" classes** have no time pattern and won't be placed on the grid
  by time — that's expected for some subparts.
- Assign instructors and set limits *before* committing; downstream sectioning and
  reporting rely on them.