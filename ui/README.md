# UniTime Angular front-end (`ui/`)

Wave 0 foundation + first live screen for the GWT→Angular migration
(see [`../ANGULAR_MIGRATION.MD`](../ANGULAR_MIGRATION.MD)). Talks to the existing
backend through the additive JSON facade (`/api/rpc/*`) — **no GWT code is changed.**

- Angular 21 (standalone, **zoneless**, signals), PrimeNG 21 (Aura theme).
- Same-origin auth: rides the existing `JSESSIONID`; CSRF via Spring's `XSRF-TOKEN` cookie.

## Run (dev)

Needs a running UniTime backend (the facade lives at `/api/rpc/*`).

1. Point the dev proxy at your server in `src/proxy.conf.json` (default
   `http://localhost:8080`; add the context path if your deploy uses one, e.g.
   `http://localhost:8080/UniTime`).
2. Install + serve:
   ```bash
   cd ui
   npm install      # first time
   npm start        # ng serve, proxying /api and legacy paths to the backend
   ```
3. Open http://localhost:4200. Sign in through the UniTime login when a data
   screen redirects you (the interceptor handles 401 → `login.action`).

Build for CI/prod: `npm run build` (output in `dist/ui/browser`).

## Run (Docker)

The Angular app ships as its own container (`Dockerfile` + `nginx.conf`) wired into
`Documentation/Docker/docker-compose.yml` alongside `unitime-db` and `unitime-web`:

```bash
cd Documentation/Docker
docker compose up --build
```

- `unitime-ui` (nginx) is exposed on **http://localhost:4200**.
- nginx serves the SPA and **reverse-proxies** `/api`, `/login`, `/logout.action`,
  `/gwt.jsp`, `/unitime` to `unitime-web:8080`, so the browser stays same-origin and the
  `JSESSIONID` session cookie is shared with the facade. No CORS.
- `/signin` (the Angular login route) is a client-side path — nginx falls back to
  `index.html`; only the `/login` **POST** reaches Spring Security.

## What's implemented

**Foundation (`src/app/core`, `src/app/layout`)**
- `rpc.service.ts` — one client for the whole backend: `execute` (command pattern) +
  `submitAsync`/`poll`/`cancel`/`executeAsync` (solver async) + `service()` (classic
  GWT RemoteServices via `/api/service`).
- `http.interceptor.ts` — `withCredentials`, error normalization, 401 → in-app `/signin`.
- `features/auth/login.ts` — **login screen** (`/signin`). Posts `username`/`password` to
  Spring Security's form-login URL (`/login`, CSRF disabled) via `AuthService.login`,
  re-checks auth, and returns to the requested route. Shell chrome is hidden until
  authenticated (login is full-page); the topbar has a sign-out button
  (`AuthService.logout` → `/logout.action`). SSO deployments would swap the form for a
  "Sign in with…" link — this covers the default/LDAP username-password configs.
- `app.config.ts` — zoneless CD, router, HttpClient + XSRF, PrimeNG/Aura.
- `auth.service.ts` — user / academic-session / build-version signals.
- `auth.guard.ts` — route guard (loads auth; enforcement hook for later waves).
- `page.service.ts` — localized page title + help URL via `PageNameBackend`.
- `menu.service.ts` — backend-driven nav (`MenuRpcRequest`) → PrimeNG menu. Migrated
  screens route in-app; everything else hands off to the legacy/GWT URL
  (**the strangler switch** — add a key to `MIGRATED` as each screen lands).
- `app.ts` / `app.html` — shell: topbar (title/help/session/user), sidebar menu,
  content outlet, footer (version).

**Screens (`src/app/features`)** — Wave 1 Rooms pilot
- `home` — landing showing auth/session state, live from RPC.
- `rooms/buildings` — PrimeNG table from `GetBuildingsRequest` with full **create /
  edit / delete** via `UpdateBuildingRequest` (`building-dialog`), confirm-on-delete,
  toast feedback, permission-gated actions (`canAdd`/`canEdit`).
- `rooms/room-groups` + `rooms/room-features` — list (from `SearchRoomGroupsRequest`
  / `SearchRoomFeaturesRequest`) with create/edit/delete via `UpdateRoomGroupRequest`
  / `UpdateRoomFeatureRequest`, sharing one **`room-property-dialog`** (both DTOs
  extend `RoomPropertyInterface`). The dialog includes a **room-membership editor**:
  a PickList of the session's rooms (loaded via `RoomFilterRpcRequest`) whose target
  is the current members, diffed to `addLocations`/`dropLocations` on save. The dialog
  also has **scope** (Global vs a department), a feature **type** picker, and
  **future-session** propagation — all fed by a cached `RoomPropertiesService`
  (`RoomPropertiesRequest` → departments / feature types / future sessions).

**Screens — Wave 2 Administration**
- `departments` — table from `GetDepartmentsRequest` with full create/edit/delete via
  `UpdateDepartmentRequest` (`department-dialog`): code/abbreviation/name, status
  (`DepartmentPropertiesRequest.statuses`), external-manager block, and the
  scheduling/preference flags. External-funding flag shown when `fundingDeptEnabled`.
- `admin/:type` — **one generic `simple-edit` screen for all ~25 admin data types.**
  A single protocol (`SimpleEditInterface` `LoadData`/`SaveRecord`/`DeleteRecord`,
  keyed by a `type` string) drives them; the table columns and the record editor are
  built dynamically from the returned `Field[]` metadata (type → text / textarea /
  number / toggle / list; `Flag` bits → hidden / read-only / required). Every menu
  item with `page="admin"` auto-routes here via its `type` parameter — so a large
  slice of the admin menu lights up from one component. These requests are addressed
  by **FQN** (their simple names collide across interfaces). *Specialized field types*
  (date/time/person/students/multi/parent) currently render as text inputs.

**Screens — Wave 3 Classic RemoteServices**
- `reservations` — first screen on the **classic RemoteService** path (not the command
  pattern). Uses `RpcService.service('reservation.gwt', method, args)` →
  `findReservations` / `delete`. `ReservationInterface` is abstract/polymorphic; the
  facade emits runtime fields but no type discriminator, so the list shows common base
  fields. The multi-step create/edit wizard (offering + type + students/curricula/groups)
  is deferred.
- `curricula` — second classic-service screen (`curricula.gwt`): `findCurricula` /
  `deleteCurriculum`. Table of abbreviation/name/academic-area/majors/department. The
  full curriculum editor (classifications + course-projections grid) is deferred.

**Screens — Wave 4 Course Offerings**
- `course-offering/:id` — Edit Course Offering (command pattern). Reached by id from
  the legacy offering search/detail during coexistence. Loads the full
  `CourseOfferingInterface` (`GetCourseOfferingRequest`), edits core descriptive/control
  fields (number, title, type, consent, expected students, by-reservation, notes) with
  dropdowns from `CourseOfferingPropertiesRequest`, and **saves the whole object back**
  (`UpdateCourseOfferingRequest`) so interdependent fields it doesn't render are
  preserved. *Deferred:* subject-area move, cross-listing, coordinators, credit wizard
  (shown read-only).
- `offerings` — Instructional Offerings **search** (Wave 4 entry point): subject-area
  picker + course-number filter → offering list → row links to `/course-offering/:id`.
  Backed by a **new additive command bean** (`SearchOfferingsRequest` /
  `SearchOfferingsBackend` in `org.unitime.timetable.rest.offerings`) that reuses the
  legacy search's model access (`SubjectArea.getUserSubjectAreas` / `getCourseOfferings`)
  and the same permission — the first case of wrapping a legacy Struts screen in a
  command bean. Auto-registered via Spring component-scan; reached through the existing
  `/api/rpc` facade (no new servlet). *Deferred:* create-new-offering.

**Screens — Wave 5 Instructor Scheduling**
- `instructorattributes` — Instructor Attributes (command pattern). The list backend
  requires a `departmentId` (permission-checked), so the screen **picks a department
  first**, then lists/creates/edits/deletes that department's attributes via
  `GetInstructorAttributesRequest` / `UpdateInstructorAttributeRequest` (`attribute-dialog`:
  code/name/type). The dialog also assigns **instructors** — a PickList of the
  department's instructors (`GetInstructorsRequest`) diffed to
  `addInstructors`/`dropInstructors` on save (same pattern as room membership).
  *Deferred:* parent-attribute selection and global-scope attributes.
- `teachingRequests` — Assigned Teaching Requests (command pattern), filter-driven: pick
  a subject area (`TeachingRequestsPagePropertiesRequest`), then list via
  `TeachingRequestsPageRequest` (filter option `subjectId`). Read-only table of
  course / sections / load / assigned instructors (`n/nrInstructors`), with conflict and
  under-assigned cues. *Deferred:* the assignment editor (solver-adjacent).
- `teachingAssignments` — Teaching Assignments (command pattern), filter-driven by
  **department**: lists instructors with teaching preference, assigned/max load (over-load
  cue), request count, and assigned courses via `TeachingAssignmentsPageRequest`.
  *Deferred:* the interactive assign/unassign editor + solver suggestions.

## Generated models

`src/app/core/generated/models.generated.ts` (439 interfaces + 37 enums) is produced
by reflecting over the compiled `org.unitime.timetable.gwt.shared` **and** `gwt.client`
classes (some request DTOs are nested in client page widgets, e.g.
`DepartmentsEdit.UpdateDepartmentRequest`), so the TS shapes match the facade's Gson output exactly (`iField→field`, enums→string unions,
`List→[]`, `Map→{ [key: string]: V }`, inheritance→`extends`). `core/models.ts`
re-exports it; **don't hand-edit the generated file.**

Regenerate (from `prototype/angular-facade/`, needs the built webapp at
`target/unitime-4.8/WEB-INF`):
```bash
# compile once
javac -d out -cp "<webapp-classpath>" java/proto/TsModelGenerator.java
# run: args = <classesRoot> <outputFile>
java -cp "out;<webapp-classpath>" proto.TsModelGenerator \
  ../../target/unitime-4.8/WEB-INF/classes \
  ../../ui/src/app/core/generated/models.generated.ts
```
(`run-proof.sh` in that folder builds the same classpath if you need a template.)

## Adding the next screen

1. Types already exist in the generated models (regenerate if you added backend DTOs).
2. Create a feature component that calls `rpc.execute<Response>('RequestName', req)`.
3. Add a lazy route in `app.routes.ts`.
4. Register the backend page key → route in `MenuService.MIGRATED` so the nav
   switches from the legacy page to the Angular one.
