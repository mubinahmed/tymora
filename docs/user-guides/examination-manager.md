# Examination Manager Guide

**Role:** *Examination Manager* (`Exam Mgr`).

You build the **examination timetable**: which exams happen, in which exam
periods, and in which rooms — without clashing for students or instructors, and
respecting room capacity and distribution rules. The flow mirrors course
timetabling but for exams.

> New to the terminology? Read the [Glossary](glossary.md) first (see *Exams &
> events* and *Solving*). Your work lives under the **Examinations** menu.
> Exams are scheduled *after* the course timetable is committed.

---

## The big picture

```
1. Define exams & periods   →   2. Set room availability & preferences   →
3. Run the exam solver      →   4. Review conflicts   →   5. Publish (PDF/reports)
```

---

## 1. Define exams

Under **Examinations → Input Data**:

1. **Examinations** (`Examinations`) — create exams and link each to the
   class(es)/offering(s) it covers. Set the exam length, seating type, and its
   **Examination Type** (e.g. *Final*, *Midterm*) and expected size.
2. **Instructors** — confirm instructors attached to exams (used for
   instructor-conflict checking).
3. **Examination Periods** — the available time slots for the session are defined
   by the administrator; confirm they cover your needs.

## 2. Rooms and preferences

- **Rooms** (Examinations → Input Data → Rooms) — review exam-eligible rooms,
  their features and groups.
- **Room Availability** — mark rooms available/unavailable for the exam window.
- **Distribution Preferences** (`Distribution Preferences`, exam variant) —
  constraints across exams, e.g. *Same Room*, *Same Period*, *Different Period*,
  *Precedence*. Set the distribution type and preference level.
- On each exam, set preferred/required **periods** and **rooms**.

---

## 3. Run the exam solver

Under **Examinations → Examination Timetabling**:

1. **Examination Solver** — load exam data, pick a solver configuration, and run.
   It assigns each exam to a period and room(s), avoiding student and instructor
   conflicts and honoring your preferences.
2. Save solutions to compare.

---

## 4. Review and fix

- **Timetable Grid** — visual layout of the exam schedule.
- **Assigned Exams / Not-assigned Exams** — placement results.
- **Conflict Statistics (CBS)** — why exams conflict or can't be placed (student
  back-to-back/overlap conflicts, room shortages, period pressure). Fix the top
  contributors first.
- **Changes**, **Solver Log**, **Reports** — detail and diagnostics.

Iterate inputs → re-run → review until conflicts are acceptable.

---

## 5. Publish

- **Pdf Reports** (Examinations → Pdf Reports) — generate the printable exam
  schedules distributed to students, instructors, and rooms.
- **Reports** (HQL) — custom tabular/exportable reports.

Once committed, the exam schedule appears in instructor and student views and in
public **Lookup Examinations**.

---

## Common pitfalls

- **Commit courses first.** Exam scheduling relies on the committed class
  timetable and its enrollments to compute student conflicts.
- **Period coverage.** If exams won't fit, there may be too few examination
  periods or rooms for the exam load — coordinate with the administrator.
- **Over-constraining** with *Required*/*Prohibited* period/room preferences leaves
  the solver no feasible placement; prefer softer preferences and read the CBS.
- Check **Examination Type** — final vs. midterm exams are scheduled against
  different period sets.