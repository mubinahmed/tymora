# Wave 0 Facade Prototype — end-to-end, one request

Proves the migration keystone from [`ANGULAR_MIGRATION.MD`](../../ANGULAR_MIGRATION.MD) §5:
**Angular reaches the existing GWT-RPC command beans through one additive JSON
endpoint, changing no GWT code and no business logic.**

Target request: `PageNameRpcRequest` → `PageNameBackend` → `PageNameInterface`
(read-only, no permission gate — the smallest honest end-to-end).

```
POST /api/rpc/PageNameRpcRequest   {"name":"Rooms"}
  → RestRpcServlet: Gson → PageNameRpcRequest(iName="Rooms")
  → GwtRpcServlet.execute(...)               ← unchanged dispatcher
  → PageNameBackend.execute(...)             ← unchanged business bean
  → PageNameInterface
  → Gson → {"helpUrl":"https://help48.unitime.org/rooms","name":"Rooms"}
```

## What's here

| File | Role |
|------|------|
| `JavaSource/org/unitime/timetable/rest/RestRpcServlet.java` | **Production facade** (now in the main source tree). Builds a request-class registry from the Spring context, deserializes JSON, dispatches via the unchanged `GwtRpcServlet.execute(...)`, serializes the response. Reuses the *exact* `iField→field` + ISO-date Gson contract from `GwtRpcServlet`. Also hosts the async submit/poll/cancel endpoints. |
| `JavaSource/org/unitime/timetable/rest/AsyncRpcExecutor.java` | **Async execution manager** for long-running commands (solver). Runs work on background daemon threads with the same thread-local setup/teardown (locale, session id, Hibernate cleanup) as `GwtRpcServlet.Execution`; exposes non-blocking submit/poll/cancel. |
| `JavaSource/org/unitime/timetable/rest/PolymorphicTypeAdapterFactory.java` | **Polymorphism support** for the facade Gson. Abstract DTO hierarchies (e.g. `ReservationInterface`) get an `@type` discriminator on write and are resolved to the concrete subclass on read — enabling polymorphic reads *and* writes. Discovers subtypes by classpath-scanning shared/client; skips subtypes Gson can't build (e.g. shadowed fields). |
| `JavaSource/org/unitime/timetable/rest/RestServiceServlet.java` | **Classic RemoteService bridge** (`/api/service/{path}/{method}`). Reflectively invokes the 4 method-based GWT services (Curricula/Reservation/Sectioning/Snapshot) — resolves the Spring bean by its `@RemoteServiceRelativePath`, matches the method by name+arity, Gson-deserializes positional args to the declared param types, invokes, serializes. The bean's `@Autowired SessionContext` handles auth per-request. |
| `WebContent/WEB-INF/web.xml` | **Patched** — `restRpcServlet` registered and mapped to `/api/rpc/*` (a longer prefix than the existing `/api/*`, so it wins for those routes without disturbing `ApiServlet`). |
| `java/proto/FacadeProto.java` | **Runnable proof.** DB-free `main()` exercising the real compiled DTOs + the real `PageNameBackend`. |
| `webxml-snippet.xml` | Reference copy of the `web.xml` entries (already applied). |
| `angular/page-name.model.ts` | Generated TS interfaces (what the model generator emits). |
| `angular/rpc.service.ts` | Generic Angular client — one method hits all 152 commands. |
| `angular/page-name.component.ts` | Minimal component making the exact call. |
| `run-proof.sh` | Compile + run against the built webapp. |

## Endpoints

```
POST   /api/rpc/{RequestName}          sync command            -> 200 <response json>
POST   /api/rpc/async/{RequestName}    submit long-running     -> 202 { "executionId": "N" }
GET    /api/rpc/async/{executionId}    non-blocking status     -> 202 { "status":"RUNNING" }
                                                                   200 { "status":"DONE", "result": {...} }
                                                                   200 { "status":"ERROR", "error":"..." }
                                                                   200 { "status":"CANCELLED" }
                                                                   404 { "error":"..." }   (unknown/expired)
DELETE /api/rpc/async/{executionId}    request cancellation    -> 200 { "status":"CANCELLED" } | 404
```

The async trio mirrors `GwtRpcServlet`'s `executeAsync`/`waitForResults`/`cancelExecution`
but polls instead of blocking (proxy-friendly for a SPA). Background work runs with a
**detached** `SessionContext` (`GwtRpcHelper`) because it outlives the HTTP request, and
replays the request thread's locale + academic-session id, closing Hibernate sessions in
`finally` — exactly as the original `Execution` does. Finished executions are retained 30 min
then swept.

## Run the proof

Requires the built webapp at `target/unitime-4.8/WEB-INF` (from `mvn package`).

```bash
cd prototype/angular-facade
./run-proof.sh
```

Verified output (Java 25, gson 2.11.0, this repo's build):

```
  PASS  registry resolves 'PageNameRpcRequest' -> ...MenuInterface$PageNameRpcRequest
  PASS  deserialize {"name":"Rooms"} -> PageNameRpcRequest
  PASS  field naming: JSON 'name' -> iName ('Rooms')
  PASS  PageNameBackend.execute(...) returned a response
  JSON  outbound = {"helpUrl":"https://help48.unitime.org/rooms","name":"Rooms"}
  PASS  serialize response: iName -> 'name'
  PASS  serialize response: iHelpUrl -> 'helpUrl' (no 'iHelpUrl' leaks)
-- async trio --
  PASS  async submit -> poll reaches DONE
  PASS  async DONE result carries the response
  PASS  async error -> poll reaches ERROR
  PASS  async ERROR carries the message
  PASS  async cancel accepted
  PASS  async cancel -> poll reports CANCELLED
  PASS  async poll of unknown id -> null (404 at HTTP layer)
-- polymorphism --
  PASS  serialize adds @type discriminator
  PASS  deserialize into abstract base -> concrete IndividualReservation
  PASS  round-trip preserves fields (id=42)
ALL 16 CHECKS PASSED
```

`RestRpcServlet.java` compiles clean against the full webapp classpath, and the
proof drives the real `PageNameBackend` (it reads `application.properties` and
returns the real help URL) — so both halves are exercised against production code.

## What this proves vs. what deployment adds

**Proven here (the risky/novel parts):**
- The `@GwtRpcImplements` beans are reachable by request-class name with no GWT dependency.
- Gson round-trips the real GWT DTOs both directions with the existing naming strategy.
- The facade compiles against the real classpath; the real command bean runs unchanged.

**Added at deploy time (standard servlet wiring, not novel):**
- `web.xml` mapping (`webxml-snippet.xml`) + the live `WebApplicationContext` bean lookup.
- `HttpSessionContext` auth (same JSESSIONID) + Spring Security CSRF for state-changing calls.
- Angular app served same-origin so `withCredentials` reuses the session.

## Next steps toward completing Wave 0

1. ~~Move `RestRpcServlet` into `JavaSource`, add the `web.xml` mapping~~ ✓ done. Smoke-test live (`mvn package` + deploy, then `POST /api/rpc/PageNameRpcRequest`).
2. ~~Add the async trio (`/async` submit, poll, cancel) over `executeAsync`/`waitForResults`/`cancelExecution`~~ ✓ done (`AsyncRpcExecutor`). Load-test against a real solver run before Wave 8.
3. Stand up the TS model generator over `gwt/shared/*` (this `page-name.model.ts` is the target shape).
4. **Serialization spike** — run this same round-trip across a sample of the 152 commands (polymorphic/enum/nested DTOs) to surface Gson edge cases early (roadmap risk #1).