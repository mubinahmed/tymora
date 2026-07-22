# Tymora UI → Angular Migration Roadmap

*Backend kept as-is · GWT code untouched · incremental (strangler-fig) migration*

---

## 1. What the codebase actually is today

Tymora is **not** a single-stack UI. It is a hybrid that has accreted three UI generations, all served from one WAR:

| Layer | Count | How it talks to the backend | Notes |
|-------|-------|------------------------------|-------|
| **GWT single-page screens** (modern) | ~57 registered pages (`gwt/client/Pages.java`), 311 client `.java` files | GWT-RPC **Command pattern** → `/unitime/gwt.rpc` | The bulk of the "real" app UI |
| **Legacy Struts + JSP screens** | 86 JSPs, 87 Struts actions | Server-rendered HTML, form posts | Older admin/data-entry screens |
| **Partial JSON/XML API** | `ApiServlet` + ~15 connectors (`api/connectors/*`) | REST-ish JSON/XML, token/basic auth | Covers a *subset* of domains only |

### The two backend call mechanisms the GWT client uses

1. **GWT-RPC Command pattern (dominant).** The client calls `RPC.execute(new SomeRequest(...), callback)`. The request travels to `GwtRpcServlet` (`/unitime/gwt.rpc`), which looks up a Spring bean by the request's class name and runs:

   ```java
   GwtRpcImplementation impl = getImplementation(request);   // Spring bean, keyed by request class
   T response = impl.execute(request, sessionContext);        // pure business logic
   ```

   There are **152 `@GwtRpcImplements` command beans**. Crucially, each implementation takes `(request, SessionContext)` and returns a response POJO — **it has no dependency on GWT serialization**. `GwtRpcServlet` even exposes a static, servlet-free entry point:

   ```java
   public static <T extends GwtRpcResponse> T execute(
       GwtRpcRequest<T> request, ApplicationContext ctx, SessionContext sessionCtx)
   ```

2. **Classic GWT-RPC `RemoteService` (legacy-modern).** Only 4 services still use this style: `SectioningService`, `CurriculaService`, `ReservationService`, `LimitAndProjectionSnapshotService` (used by the curricula, reservations, student-sectioning, HQL screens).

### Data contracts (DTOs)

The request/response objects live in `gwt/shared/*` (~40 interface files, plus nested request/response classes). They are **plain POJOs** — `iCamelCase` fields, `implements IsSerializable, GwtRpcResponse` — e.g. `DepartmentInterface`, `RoomInterface`, `EventInterface`. They are already Gson-serializable: `GwtRpcServlet` builds a Gson instance with a naming strategy that maps `iName → name` and ISO date adapters.

### Cross-cutting facts that shape the plan

- **Auth:** Spring Security, **session-cookie based** (`JSESSIONID`), with LDAP + OAuth2/OIDC providers behind a `SessionContext`/`HttpSessionContext` abstraction. `springSecurityFilterChain` is a `DelegatingFilterProxy`.
- **Long-running operations (the solver):** no WebSockets. Uses an **async + polling** contract already present on the RPC service: `executeAsync(request) → Long id`, `waitForResults(id)`, `cancelExecution(id)`.
- **Binary / file endpoints:** dedicated servlets — `/calendar`, `/pattern`, `/upload`, `/export`, `/picture`, `/mapServlet` — return iCal, images, exports. Angular reuses these as plain URLs; nothing to migrate.
- **Menu / navigation:** already backend-driven (`MenuInterface`).
- **i18n:** `Localization` + GWT `Constants`/`Messages` property files.

---

## 2. The central strategy

> **Strangler-fig migration behind a thin, additive JSON facade that reuses the existing command beans.**

Angular is a separate client and cannot speak the GWT-RPC binary wire protocol. Rewiring the backend, or making Angular emulate GWT-RPC, is off the table. The clean path exploits the fact that the **command implementations are already serialization-agnostic Spring beans**:

```
Angular (HttpClient, JSON)
        │  POST /api/rpc/{RequestName}   { ...json... }
        ▼
NEW RestRpcController  ── Gson.fromJson → concrete GwtRpcRequest
        │
        ▼
GwtRpcServlet.execute(request, appCtx, sessionCtx)   ← EXISTING, unchanged
        │
        ▼
the same 152 GwtRpcImplementation beans             ← EXISTING business logic, unchanged
        │
        ▼
GwtRpcResponse POJO ── Gson.toJson → JSON to Angular
```

**This touches zero GWT client code and zero business logic.** The only new backend artifact is a *facade* (one controller + serialization config + async/poll bridge). Under the "keep backend as-is" constraint this is the honest reading: **no existing code changes; the facade is purely additive** and can be deleted at the end if desired.

### A decision to confirm with stakeholders

"Keep backend as-is" has two possible strictness levels:

- **(A) No changes to existing backend logic, additive facade allowed** — *recommended*. Enables reusing all 152 command beans. ~1 new controller.
- **(B) Literally zero new backend code** — Angular must use only the existing `ApiServlet` connectors (~15 domains). This covers a fraction of the screens; the rest cannot be migrated. Not viable for a full UI migration.

The roadmap below assumes **(A)**.

---

## 3. The facade (Phase 1 keystone) — design detail

A single additive Spring `@RestController` (or one servlet mapped at `/api/rpc/*`), living in a **new** package so it never edits existing files:

- `POST /api/rpc/{simpleRequestClassName}` — body = JSON of the request POJO.
  1. Resolve the request `Class` from a startup-built registry (scan the same beans `@GwtRpcImplements` already registers; the request type is the bean key).
  2. `gson.fromJson(body, requestClass)` using the **same** naming strategy/date adapters already in `GwtRpcServlet` (extract them into a shared `GsonProvider`, or duplicate in the facade to avoid editing existing code).
  3. Call `GwtRpcServlet.execute(request, appCtx, HttpSessionContext.getSessionContext(...))`.
  4. `gson.toJson(response)`.
- `POST /api/rpc/{name}/async` → `{executionId}`; `GET /api/rpc/async/{id}` (long-poll → result); `DELETE /api/rpc/async/{id}` (cancel). Wraps the existing async trio for the solver screens.
- Wrap the 4 classic `RemoteService`s with 4 tiny endpoint methods that delegate to their existing service beans.
- Errors: map `GwtRpcException`/`PageAccessException`/`AccessDeniedException` → HTTP 4xx/5xx (mirror `ApiServlet.checkError`).

**Generate TypeScript models, don't hand-write them.** With ~40 shared interfaces plus nested requests/responses, hand-maintaining TS types will drift. Use a generator (e.g. `typescript-generator` Maven plugin, or a small reflective generator run at build time) that reads `gwt/shared/*` and emits `.ts` interfaces with the same `iName → name` convention. Reading the DTOs to generate types does **not** modify GWT code.

### Auth: serve Angular same-origin

Package the Angular build **inside the same WAR / same origin** as the current app. Then:

- Reuse the existing Spring Security session + login flows (form, LDAP, OAuth2/OIDC) unchanged — Angular rides the same `JSESSIONID`. No CORS, no second identity system.
- Add **CSRF token** handling for state-changing calls (Spring Security CSRF cookie → Angular `HttpInterceptor`). This is the main new security wiring.
- The existing token/basic auth (`ApiToken`) remains available for programmatic API use.

---

## 4. Screen inventory & sequencing

Group the ~57 GWT pages + legacy JSP screens into domains, then migrate domain-by-domain (each domain ships independently behind routing):

| Domain | Representative screens | Backend readiness | Suggested order |
|--------|------------------------|-------------------|-----------------|
| **Rooms & Buildings** | rooms, roomgroups, roomfeatures, buildings, travel times, room pictures | Command beans **+** existing `RoomsConnector`/`BuildingsConntector` | **Pilot (1st)** — self-contained, high value, best-covered |
| **Departments / Admin data** | departments, simple-edit admin tables, scripts, tasks, password | Command beans; simple CRUD | 2nd — proves the CRUD template |
| **Curricula** | curricula, projection rules | Classic `CurriculaService` | mid |
| **Reservations** | reservations, reservation edit | Classic `ReservationService` | mid |
| **Course offerings** | courseOffering, instrOfferingConfig, multipleClassSetup, assignClassInstructors | Command beans | mid (interdependent, larger) |
| **Instructors / Teaching** | teaching requests/assignments/changes, instructor attributes, instructor survey | Command beans | mid |
| **Events** | event timetable, room availability, calendars | Command beans + `EventsConnector` + `/calendar` | mid |
| **Student sectioning** | student sectioning, course requests, advisor requests, sectioning status/reports, dashboards | Classic `SectioningService` + command beans | later (complex, real-time-ish) |
| **Solver / Timetabling** | solver, solver log, assigned/not-assigned classes, suggestions, CBS, solution changes/reports, timetable grid | Command beans **+ async/poll** | **last** — most complex, needs the async bridge |
| **Legacy Struts/JSP screens** | 86 JSPs / 87 actions | Server-rendered; **no command layer** | Interleaved; some need new command beans or ApiServlet connectors first |

> **Legacy caveat:** the Struts/JSP screens are the one place the "no backend change" line gets tested — several have no JSON-capable command layer behind them. For each, choose: (a) it's already superseded by a GWT screen; (b) wrap its action logic in a new command bean (additive); or (c) defer. Inventory these explicitly before committing dates.

---

## 5. Phased plan

### Phase 0 — Foundations (spikes & scaffolding)
- Stand up the Angular workspace (Angular + your component lib — Angular Material or PrimeNG), same-origin build wired into the WAR/Maven build.
- Build the **facade** for **one** request type end-to-end; prove JSON round-trip, session auth, CSRF.
- Stand up the **TS model generator**; commit generated types.
- Establish the **shared UI kit**: layout shell, backend-driven menu (`MenuInterface`), auth guard, `HttpInterceptor` (CSRF + error mapping), an RPC client service (`rpc<T>(request)`), i18n loader (reuse property files), theming to match the current look.
- **Routing coexistence:** Angular router at a distinct base path; a redirect filter maps old `*.gwt`/action URLs → new routes as each domain lands (and vice-versa during transition) so bookmarks survive.

### Phase 1 — Pilot domain (Rooms & Buildings)
- Migrate the full domain to Angular against the facade.
- Nail the reusable patterns: data-table + filter, edit dialog, permission-driven visibility, optimistic errors, unit/e2e test harness (Karma/Jest + Playwright/Cypress).
- Ship behind the router with old GWT screens still reachable — validate in production for one release.

### Phase 2 — Domain-by-domain migration
- Work down the table above. Each domain: generate/verify TS models → build screens → wire redirects → retire the corresponding GWT page(s) from the menu (not by deleting GWT code, just by routing past it).
- Add the **async/poll bridge** before the Solver domain.
- Handle legacy Struts/JSP screens per the caveat in §4.

### Phase 3 — Cutover & decommission
- Flip the default landing/menu fully to Angular.
- Remove GWT compilation from the build once no page routes to it (the GWT source can remain in the tree, unbuilt, satisfying "don't change GWT code").
- Optionally retire Struts filter and JSPs once all screens are covered.
- Facade becomes the permanent API, or is folded into a first-class REST API if desired.

---

## 6. Cross-cutting workstreams (run continuously)

- **i18n:** reuse the existing `Constants`/`Messages` `.properties` files; convert to Angular i18n JSON at build time so translations aren't re-authored.
- **Theming:** match current styling (`WebContent/styles/unitime.css`) initially to reduce change-management friction; modernize after functional parity.
- **Permissions:** the backend already returns permission info per response/menu; drive Angular route guards and control visibility from it — do **not** reimplement authorization on the client.
- **Testing:** contract tests on the facade (JSON ↔ POJO), component tests per screen, e2e smoke per domain. A regression risk is Gson (de)serialization of polymorphic/enum/nested DTO fields — cover these explicitly.
- **Observability:** the existing `QueryLog` logging in `GwtRpcServlet` won't capture facade calls; add equivalent logging in the facade so RPC analytics continue.

---

## 7. Key risks & mitigations

| Risk | Mitigation |
|------|-----------|
| Gson can't cleanly (de)serialize some command DTOs (polymorphism, cyclic refs, GWT-only types) | Spike serialization across a sampling of the 152 commands early; add custom Gson adapters in the facade (not in shared code) where needed |
| "No backend change" interpreted strictly (option B) | Confirm option A with stakeholders **before** Phase 0 — the whole plan depends on it |
| Legacy Struts/JSP screens with no command layer | Explicit inventory in Phase 0; wrap-in-new-command or defer decisions per screen |
| Solver async semantics differ from HTTP | Build & load-test the async/poll bridge before the Solver domain |
| Dual UI drift during multi-quarter coexistence | Strangler routing + redirects keep exactly one UI authoritative per screen; menu is the single switch |
| Session/CSRF friction for the SPA | Same-origin hosting + CSRF interceptor decided in Phase 0 |

---

## 8. Effort shape (order-of-magnitude)

This is a **multi-quarter** program, not a sprint: ~57 modern pages + ~86 legacy screens across ~10 domains. The facade + foundations are a few weeks; each domain is weeks-to-months depending on screen count and interdependency (Rooms small; Offerings/Solver large). The facade design means backend effort is near-constant and small — **the cost is front-end rebuild, not backend rework**, which is exactly what "keep the backend as-is" optimizes for.

---

### One-line summary
Stand up an **additive JSON facade** over the existing 152 GWT-RPC command beans, auto-generate TypeScript models from the shared DTOs, host Angular **same-origin** to reuse Spring Security sessions, then **strangler-migrate domain-by-domain** starting with Rooms — deleting nothing from GWT, changing no business logic.
