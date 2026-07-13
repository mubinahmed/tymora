# Curriculum Manager Guide

**Role:** *Curriculum Manager* (`Curriculum Mgr`).

You model **expected student demand** so that course timetabling can happen
*before* real registrations exist. A curriculum says, in effect, "students in this
program, at this year level, are expected to take these courses in these numbers."
That demand drives the course solver toward a timetable students can actually
enroll in without conflicts.

> New to the terminology? Read the [Glossary](glossary.md) (*Students & demand*).
> Your work lives under the **Curricula** menu (and Courses → Input Data →
> Curricula).

---

## Why curricula matter

The course solver minimizes *student* conflicts. Early in the cycle there are no
real student schedules yet — curricula provide the expected demand that stands in
for them. A good curriculum model leads to a timetable that later sections cleanly.

---

## Core tasks

Under **Curricula**:

1. **Curricula** (`Curricula`) — create and maintain curricula. A curriculum is
   defined by:
   - an **Academic Area** (and optionally **Major(s)**),
   - one or more **Classifications** (year levels), and
   - for each classification, the **courses** expected and the **number of
     students** expected to take each.
   You can group courses (e.g. "choose 1 of these electives").
2. **Projection Rules** (`Projection Rules`) — rules that scale historical or base
   enrollment numbers into projected demand for the term being planned (by
   academic area / classification / major).

---

## Typical workflow

1. Start from the previous term's curricula (rolled forward by the administrator)
   or build new ones from your academic structure.
2. For each program/year level, list the required and elective courses and the
   expected headcount.
3. Apply/adjust **projection rules** to reflect expected changes (growth, program
   changes).
4. Review the aggregated demand — this is what Department Schedule Managers and the
   course solver consume.
5. Refine as enrollment intentions firm up.

---

## How your data is used downstream

- **Department Schedule Managers** use curriculum demand to decide how many
  sections of each course to offer and how large they should be.
- The **course solver** uses expected co-enrollment (students taking course A and
  course B) to avoid timetabling those courses at the same time.
- **Reservations** can target a curriculum, holding seats for its students.

---

## Common pitfalls

- **Keep areas/classifications/majors aligned** with the administrator's academic
  structure — curricula are built from those reference lists.
- **Demand should be realistic, not aspirational.** Inflated numbers create
  impossible constraints; too-low numbers let conflicts slip through.
- Coordinate with Department Schedule Managers — your projected demand and their
  offered sections/limits must be consistent.
- Curricula are **session-scoped**; confirm you're editing the term you intend.