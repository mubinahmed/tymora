# UniTime Documentation

UniTime is a comprehensive **University Timetabling System** — an open-source
(Apache 2.0, Apereo Foundation) web application for building and managing course
timetables, examination timetables, campus events, and student class schedules.

- Project site: <https://www.unitime.org>
- Online help: <https://help.unitime.org>
- Live demo: <https://demo.unitime.org>
- This repository: UniTime **4.8**

---

## What is in this folder

This documentation set was written to help two audiences get productive quickly.

### For developers

| Guide | Read it when you… |
|-------|-------------------|
| [Developer Onboarding Guide](DEVELOPER_ONBOARDING.md) | Are setting up the codebase, want to understand the architecture, build the WAR, run it locally, or make your first change. |

### For end users (by role / persona)

Each guide is written for one of UniTime's built-in roles. Start with the
[Glossary](user-guides/glossary.md) if the terminology is new to you.

| Persona | Guide | What they do |
|---------|-------|--------------|
| 📖 Everyone | [Glossary & Core Concepts](user-guides/glossary.md) | Shared vocabulary: academic session, offering, subpart, class, solver, etc. |
| 🛠️ System / Session Administrator | [Administrator Guide](user-guides/administrator.md) | Sets up academic sessions, users, rooms, roles/permissions, and reference data. |
| 📅 Department Schedule Manager | [Department Schedule Manager Guide](user-guides/department-schedule-manager.md) | Enters course offerings & classes, sets preferences, runs the course solver. |
| 📝 Examination Manager | [Examination Manager Guide](user-guides/examination-manager.md) | Defines exams and periods, runs the exam solver, publishes exam schedules. |
| 🏫 Event Manager | [Event Manager Guide](user-guides/event-manager.md) | Manages room bookings, event requests, and approvals. |
| 📈 Curriculum Manager | [Curriculum Manager Guide](user-guides/curriculum-manager.md) | Maintains curricula and projection rules that model student demand. |
| 🎓 Student Advisor | [Advisor Guide](user-guides/advisor.md) | Enters and approves course recommendations for students. |
| 👩‍🏫 Instructor | [Instructor Guide](user-guides/instructor.md) | Views teaching schedule, sets availability, completes the instructor survey. |
| 👨‍🎓 Student | [Student Guide](user-guides/student.md) | Submits course requests and builds a personal class schedule. |

---

## How the pieces fit together (end-to-end)

```
                    ┌──────────────────────────────────────────────┐
   Administrator →  │  Academic Session, Departments, Subject Areas,│
                    │  Buildings/Rooms, Users & Roles, Date/Time     │
                    │  Patterns  (reference data)                    │
                    └───────────────────────┬────────────────────────┘
                                             │
      Curriculum Mgr →  Curricula / projections (expected demand)
                                             │
 Dept Schedule Mgr →  Instructional Offerings → Configs → Subparts → Classes
                       + Preferences (time/room/distribution) + Reservations
                                             │
                             Course Solver → committed class timetable
                                             │
        ┌────────────────────────────────────┼───────────────────────────┐
        │                                     │                           │
  Exam Mgr → Exams + periods           Student Scheduling            Event Mgr →
  → Exam Solver → exam timetable   (Course Requests → Sectioning)   room bookings
                                    → Student personal schedules      & approvals
```

Everything is scoped to an **Academic Session** (a term at a campus). At the end
of a term, an administrator "rolls forward" the session to seed the next one.

---

## Quick start for the impatient

**Run the demo locally with Docker** (see the [developer guide](DEVELOPER_ONBOARDING.md#3-running-locally) for detail):

```bash
cd Documentation/Docker
docker-compose build && docker-compose up
# open http://localhost:8888  — log in as admin / admin
```

**Build from source** (Java 17 + Maven):

```bash
mvn -B package
# produces target/UniTime.war
```