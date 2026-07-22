# Tymora Glossary & Core Concepts

Read this first if you are new to UniTime. Every persona guide assumes this
vocabulary. Terms are grouped by theme and ordered roughly the way you meet them.

---

## The container: sessions and structure

**Academic Session**
: A specific term at a specific campus — e.g. *Fall 2026, Main Campus*. It is the
top-level container: **almost all data (offerings, exams, events, rooms,
solutions) belongs to one session.** You always work "inside" a selected session.

**Roll Forward**
: The administrator action that copies the structure of one session into the next
(e.g. Fall 2026 → Fall 2027), so you don't rebuild everything each term.

**Subject Area**
: A course prefix such as `MATH` or `HIST`, owned by a **Department**. Courses are
identified as *subject area + course number* (e.g. `MATH 101`).

**Department**
: The organizational unit that owns subject areas and is responsible for
timetabling its courses. Managers are assigned to departments.

---

## Courses: from offering to class

These nest inside each other — this hierarchy is the heart of course timetabling:

```
Instructional Offering            (e.g. "MATH 101")
 └─ Course Offering(s)            (cross-listed course names for the same offering)
     └─ Configuration             (an alternative way to take the course)
         └─ Scheduling Subpart    (a component: Lecture, Recitation, Lab …)
             └─ Class              (a concrete section with time/room/instructor)
```

**Instructional Offering**
: A course as offered in a session. May bundle several **Course Offerings** when
courses are **cross-listed** (shared classes under different names).

**Configuration (Instructional Offering Config)**
: One valid structure of components a student takes together. A course can have
alternative configurations (e.g. a "with lab" vs. "without lab" version).

**Scheduling Subpart**
: An instructional component within a configuration — Lecture, Recitation, Lab,
Seminar, etc. Its kind is the **Instructional Type (itype)**.

**Class**
: A concrete, scheduled section of a subpart. It has a section number, an
enrollment limit, and (after timetabling) a time, room, and instructor.

---

## Preferences & constraints

**Preference Level**
: How strongly something is wanted or avoided. The scale runs
*Required → Strongly Preferred → Preferred → Neutral → Discouraged → Strongly
Discouraged → Prohibited.* Applied to times, rooms, buildings, features, etc.

**Time Pattern**
: The allowed shapes of a meeting time — which days and how long (e.g. "MWF 50
min", "TR 75 min"). A class with no time pattern is **Arrange Hours**.

**Date Pattern**
: The set of weeks/dates within the term over which a class actually meets (full
term, first half, alternating weeks, etc.).

**Distribution Preference / Distribution Type**
: A constraint that links two or more classes (or exams) — e.g. *Same Room*,
*Back-To-Back*, *Precedence*, *Spread In Time*, *Same Instructor*. Each carries a
preference level (often *Required* or *Prohibited*).

**Reservation**
: Reserves seats in an offering/config/class for a specific audience — a student
group, a curriculum, a course, individual students, or an override.

---

## Solving: turning inputs into a timetable

**Solver**
: The optimization engine (built on **cpsolver**) that assigns times, rooms,
instructors, or students subject to all the preferences and constraints. UniTime
has four solvers: **Course**, **Examination**, **Student Sectioning** (batch), and
**Instructor Scheduling**.

**Solution / Saved Timetable**
: A set of assignments produced by a solver. You can save several, compare them,
and finally **commit** one to make it the official timetable for the session.

**Solver Group**
: The unit that owns solutions — typically maps to the departments a manager
timetables together.

**Conflict-Based Statistics (CBS)**
: The solver's explanation of *why* some classes/exams couldn't be assigned or why
conflicts remain — the main diagnostic tool when a timetable isn't clean.

---

## Students & demand

**Curriculum**
: A model of expected student demand, built from **Academic Areas**,
**Classifications** (year levels), and **Majors**. Used to timetable *before* real
enrollments exist. **Projection Rules** scale historical numbers to future terms.

**Course Request**
: A student's ranked list of desired courses (with alternates), the input to
student scheduling.

**Student Sectioning / Scheduling Assistant**
: Assigning students to specific class sections. **Batch** sectioning runs as a
solver over all students; **online** sectioning happens in real time as students
register. The **Scheduling Assistant** is the student-facing tool.

**Advisor Course Recommendations**
: Course suggestions an advisor records for a student, guiding or pre-filling the
student's requests.

---

## Exams & events

**Examination Period**
: A defined exam time slot within a session. **Examination Type** categorizes exams
(e.g. *Final*, *Midterm*). The **Exam Solver** assigns exams to periods and rooms.

**Room / Building**
: Physical spaces. Rooms have a **Room Type** (Classroom, Computing Lab, …),
capacity, **Room Features** (projector, fixed seating, …), and **Room Groups**.
Room **availability** governs when a room can be used for classes/exams/events.

**Event / Meeting**
: A non-class use of a room — *Special*, *Course-Related*, or *Unavailable*. Events
move through an **approval** workflow managed by the Event Manager.

---

## People & access

**Timetable Manager**
: A staff person record linked to one or more **roles** and **departments**.
Assigning a role to a manager is how they gain their permissions.

**Role**
: A named bundle of permissions. Built-in roles include *System Administrator*,
*Session Administrator*, *Department Schedule Manager*, *Examination Manager*,
*Event Manager*, *Curriculum Manager*, *Student Advisor*, *Instructor*, *Student*,
*View All*, and *Anonymous*. See each [persona guide](.).

**Right / Permission**
: A single capability (e.g. `InstructionalOfferings`, `Solver`, `Events`). Roles
are made of rights. What you can see and do — down to individual buttons — depends
on the rights of your current role.

**Current Authority**
: Your active *role + academic session* combination. After logging in you choose
one (Preferences → **Change Role**); it determines everything you can access.
Users with several roles switch between them without logging out.
