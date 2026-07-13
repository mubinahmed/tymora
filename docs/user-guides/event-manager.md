# Event Manager Guide

**Role:** *Event Manager* (`Event Mgr`).

You manage **non-class use of rooms** — meetings, seminars, exams-as-events,
one-off bookings — and the **approval workflow** for event requests submitted by
staff, instructors, and students. Event management shares the same room inventory
as course and exam timetabling, so it prevents double-booking automatically.

> New to the terminology? Read the [Glossary](glossary.md) (*Exams & events*).
> Your work lives under the **Events** menu.

---

## What is an event?

An **Event** is a booking of one or more rooms for one or more **meetings** (a
meeting = a date + time + room). Events come in types:

- **Special event** — a general booking (meeting, defense, guest lecture…).
- **Course-related event** — tied to a course/class (extra session, review).
- **Unavailable** — blocks a room so it can't be booked/scheduled.

Every event has a **status** (e.g. pending → approved / rejected) that you drive.

---

## Core tasks

All under the **Events** menu:

1. **Events** (`Events`) — search, view, create, edit, and delete events. This is
   your main console.
2. **Add Event** — create a new **special**, **course-related**, or **unavailable**
   event: choose rooms, add meeting dates/times, add contacts and notes.
3. **Room Availability** (`Room Availability`) — see, per room, what is booked
   (classes, exams, and events together) and when a room is free.
4. **Rooms** — review room features and groups to pick the right space.
5. **Timetable** — a calendar/grid view of room usage.
6. **Reports** (HQL) — export event and room-usage data.

---

## Approval workflow

Requesters (instructors, students, other staff) submit event requests. As Event
Manager you:

1. Review incoming requests in **Events** (filter by status = pending).
2. Check for conflicts via **Room Availability** — UniTime flags overlaps with
   classes, exams, and other events.
3. **Approve**, **reject**, or ask for changes; add notes explaining decisions.
4. Approved events immediately occupy the room and appear on the room timetable
   and in the requester's view.

Notification emails are sent according to the event's contacts and your session's
event configuration (set under Preferences / by the administrator).

---

## Common pitfalls

- **Events respect the committed class and exam timetables.** A room shows busy if
  a class or exam already uses it — that's intended, not a bug.
- **Session and event status** control whether requests can be submitted and
  approved for the current term; if requesters can't submit, check the event
  status with your administrator.
- Use **Unavailable** events to reserve/close rooms for maintenance or holds — this
  keeps them out of both event booking and (where configured) timetabling.
- Set clear **contacts** on each event so approval notifications reach the right
  people.