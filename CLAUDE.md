# CLAUDE.md

Project constitution. Read `docs/PRD.md` for requirements and `docs/DECISIONS.md` for the *why*
behind every architectural choice. Those are the source of truth; this file is the working contract.

## Project in three sentences

An **incident reporting system**: a user submits free-form Turkish text from an open source (news,
report, social media), and the system extracts **date, province, event type and numeric metrics**
from it. Raw text is stored verbatim in MongoDB (immutable audit log); the extracted structured
data is stored in PostgreSQL and drives filtered tables and per-event-type charts. A ReactJS
frontend is where all of this is entered and read; connected clients are told over one-way SSE
that something changed, and refetch.

## Repository layout

Monorepo. One `CLAUDE.md`, at the root, covering every module.

```
docker-compose.yml   full system - the entry point, `docker compose up --build`
docs/                PRD, DECISIONS, TASKS - project-wide
docs/postman/        API collection; examples captured from a running instance, never hand-written.
                     Extend it when endpoints land; `npx newman run` verifies it still fits.
backend/             Java 21 / Spring Boot; the Maven reactor root lives HERE, not at repo root
frontend/            React + TypeScript + Vite (ADR-022). Talks to the API on its own origin -
                     nginx proxies /api to the backend, so no CORS and no absolute URL (ADR-025)
```

There is no `pom.xml` at the repository root.

## Commands

**JDK 21 is required and is not this machine's default** (default is 17). Export it first or every
Maven command fails the enforcer rule. Maven runs from `backend/` (the reactor root), compose from
the repository root.

```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd backend
./mvnw verify                          # all modules: build + tests + coverage gate
./mvnw -pl analysis -am verify         # one module and what it depends on
./mvnw test -Dtest=ClassName -Dsurefire.failIfNoTests=false \
            -Dsurefire.failIfNoSpecifiedTests=false     # one test class; the two flags are needed
                                       # because every other module then runs nothing, and the
                                       # build normally treats that as a module without tests
./mvnw -pl app spring-boot:run         # run locally, `local` profile is the default
java -jar backend/app/target/incident-report.jar
curl -s localhost:8080/actuator/health
open app/target/site/jacoco-aggregate/index.html   # project-wide coverage report
```

From the repository root:

```
docker compose up --build              # the whole system
docker compose up -d postgres mongodb  # databases only, for the `local` profile
docker compose ps / logs -f app / down -v
```

From `frontend/`: `npm run dev` (Vite on :3000, proxies /api to :8080), `npm run verify`
(lint + type-check + build + coverage gate), `npm test`.

**`./mvnw verify` needs a running Docker daemon** — Testcontainers starts real Postgres and Mongo.
Neither image build does: the backend packages with `-DskipTests`, the frontend image type-checks
and bundles but runs no tests. The image build is not the quality gate; `verify` is.

Two compose files, no duplication: the root one `include`s `backend/docker-compose.yml`, which owns
the app and both databases. So keep the backend file includable (no absolute paths, no assumption it
is the only one) and put frontend/cross-service wiring in the root file only. Compose carries inline
defaults for every setting, so a fresh clone runs with no `.env`. The app image is layered
(dependencies / loader / application) — do not collapse those COPY steps back into one.

## Architecture — hard constraints

Modular monolith, built as a **Maven multi-module project**. Each module is its own artifact with
its own pom, and the module graph *is* the architecture boundary:

```
shared      depends on no other module — cross-module events, error contract
ingestion   -> shared    receives raw text, stores it verbatim, reads it, reprocess.  MongoDB only
analysis    -> shared    parses, classifies, persists, serves queries/aggregations.   PostgreSQL only
realtime    -> shared    signals connected clients that new records exist.            no database
app         -> all       the only deployable artifact: @SpringBootApplication + application*.yml
```

There is deliberately **no edge between `ingestion` and `analysis`**. Reaching across it is a
compile error, not a style violation. Never "fix" such an error by adding the dependency.

Rules that must never be broken:

1. **No cross-module class access.** Modules communicate only via domain events declared in `shared`
   and each module's explicitly public API types. Never inject another module's repository or
   internal service.
2. `ingestion` never touches PostgreSQL. `analysis` never touches MongoDB.
   Module-specific libraries go in that module's pom, never in the parent.
3. **The raw record is write-once.** Store the text byte-for-byte as submitted, before any
   normalization. No update, no delete endpoint — and nothing writes to that document again after
   the insert, not even to record how analysis went (ADR-005, ADR-021).
4. **Raw text is stored even when analysis fails.** Persisting the raw report and analyzing it are
   separate concerns; a failure is recorded on the `analysis` side and does not roll back the write,
   does not mutate the raw document, and does not fail the caller's request.
5. **Each module publishes only the data it owns** (ADR-021). `ingestion` owns the raw text;
   `analysis` owns the structured records *and the analysis outcome* — status, warnings, analyzed-at.
   Events flow **one way only**: `ingestion` -> `analysis`. Never add a return event, and never put
   an analysis field on an ingestion DTO or document.
6. **Submission returns a receipt, not a result.** `POST /incident-reports` answers with the raw
   report's id and submission time. The client reads what was extracted through
   `GET /incidents?rawReportId=...`. Reprocess answers the same way.
7. **SSE is a refresh trigger, not a data source.** No data is reachable only through the stream:
   the event carries enough to judge relevance, the client refetches. If the stream dies, nothing
   becomes unreachable — only "live" is lost (ADR-004, ADR-021).
8. Module-to-module messaging is **synchronous** (`ApplicationEventPublisher` + `@EventListener`).
   Do not introduce `@Async`, brokers, or Spring Modulith's async `@ApplicationModuleListener`.
   Synchrony is an implementation detail — it must stay invisible in the client contract.
9. **Two-way traceability** between the raw Mongo document and its derived Postgres rows is a
   requirement, not a nice-to-have. The raw -> records direction is served by the `rawReportId`
   filter, not by an ingestion endpoint that returns derived ids.
10. Event types, their trigger keywords and their metrics live in **YAML configuration**, never
    hardcoded. Adding an event type must not require a code change. The frontend has no hardcoded
    catalog either — every dropdown is fed from the metadata endpoint.

## Data model — hard constraints

An `Incident` is grained by **(raw report, date, province, event type)** — ADR-019. One text
produces one record per distinct combination it contains. `province` is nullable and always paired
with a scope:

- `SINGLE` — the numbers belong to one named province.
- `SHARED` — the text gives a total across several provinces ("her iki ilde toplam 10 kişi").
  The covered provinces are stored in `incident_shared_province`.
- `UNKNOWN` — no province appears in the text.

Rules that follow, and that queries and DTOs must respect:

- **Never split a `SHARED` figure across its provinces.** Even distribution invents data the text
  does not contain. It is not added to any single province's total, ever.
- **Never drop it either.** A province-filtered view surfaces it as a separate, labelled item, so
  per-province totals and the grand total can be reconciled. When several provinces are selected it
  is counted **once** — join through the link table with `DISTINCT`, do not sum per province.
- Build incidents through `Incident.forProvince` / `sharedAcross` / `withoutProvince`. No constructor
  can attach a single province to a `SHARED` record; the schema enforces the same via
  `incident_province_matches_scope`.
- Metrics are one row per metric (ADR-020), keyed by catalog name. Adding a metric must never
  require a migration.
- Entity `toString()` must not touch a lazy association — it explodes from inside the logging call.

## Conventions

- Java 21, Spring Boot 3.5.x, Maven (wrapper), Flyway for Postgres schema, springdoc-openapi.
- Package root `com.emreay.incidentreport`, then one package per module, then layers inside it.
- **Everything inside a file is English** — code, identifiers, comments, Javadoc, log messages, API
  paths, enum constants, commit messages, and comments in `pom.xml`, YAML, SQL and Dockerfiles.
  A Turkish comment in a config file is as wrong as a Turkish method name.
  **Only `.md` files under `docs/` and `README.md` are Turkish**; this file stays English.
  **One carve-out:** user-facing UI strings are Turkish, because the user is (PRD §2.2). They are
  content, not code — keep them in one place, never inline in components, and keep everything
  around them English.
- Never expose entities/documents through controllers — always DTOs. Prefer `record` for DTOs and events.
- Errors: `@RestControllerAdvice` returning RFC 7807 `application/problem+json`. No stack traces in responses.
- Constructor injection only. No field injection.
- `spring.jpa.hibernate.ddl-auto=validate`. Schema changes go into a new Flyway migration.
- Log with the raw report id as correlation key so a submission can be traced ingestion → analysis → SSE.

Frontend (from T-23 on):

- React + TypeScript + Vite (ADR-022), no SSR. TanStack Query for server state, React Router for
  routing and URL state, Recharts for charts, Vitest + Testing Library for tests (ADR-026).
- **No absolute API address anywhere** (ADR-025). Requests are relative; nginx (prod) and the Vite
  proxy (dev) put the API on the same origin. Adding a base-URL setting undoes that decision.
- Three layers: API client / state / view (PRD §5.4). Derived numbers are **not** the view's job —
  cumulative sums, aggregation and filtering come from the server. Never reimplement a rule the
  backend owns; two copies in two languages drift. One API layer, RFC 7807 parsed there once,
  filter state in the URL.
- Province is a breakdown dimension, not just a filter (ADR-023).

## Testing

- JaCoCo gate at **80% lines, per module**, wired into `verify` — an aggregate number would let a
  well-tested module hide an untested one. `app` also emits a project-wide aggregate report.
  Coverage is a floor, not a goal — cover real behavior, not getters. See ADR-018.
- Surefire runs with `failIfNoTests=true`: JaCoCo silently skips its check in a module with no
  `.exec` file, so a module with code and no tests would sail through. `realtime` carries an
  explicit override until it has code (T-18); do not add more.
- Architecture rules live in `app/src/test/java/.../architecture/ArchitectureRulesTest.java`
  (ArchUnit — see ADR-017). `app` is the only module that sees all the others, so cross-module
  rules can only be expressed there. Add new rules to that file, not to individual modules.
- Every extractor (date, province, number, event type, metric) gets parameterized table-driven tests
  including the negative and ambiguous cases.
- The three sample texts from the source document are **golden tests** — see the table in
  `docs/PRD.md` §11. They must also pass with their sentences shuffled.
- Unit tests use plain JUnit 5 + Mockito, no Spring context. Repository/integration tests use
  Testcontainers against real Mongo and Postgres, pinned to the same image tags compose runs.
- The frontend carries the **same 80% gate**, breaking its own build (ADR-024). Test behavior, not
  snapshots — a snapshot inflates the number without asserting anything. The API-client and state
  layers are testable without a DOM; lean on that rather than on rendering everything.

## Gotchas

- **Turkish locale.** `"İZMİR".toLowerCase()` is wrong. Always pass an explicit locale:
  `toLowerCase(Locale.of("tr"))`. The i/İ/ı/I mapping breaks silently under the default locale.
- Regex over Turkish text needs `Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE`, and `\b`
  does not behave as expected around apostrophes — province names arrive suffixed
  (`Ankara'da`, `Kocaeli'nde`, `İzmir'de`).
- Numbers may be digits *or* Turkish words, including compounds (`on iki` = 12, `kırk beş` = 45).
- **Dates have three sources, not two** (`EXPLICIT` / `RELATIVE` / `DEFAULTED`). `Son 24 saatte` is
  an *extraction*, not a fallback — never collapse it into `DEFAULTED`. The reference date is the
  report's **original submission date**, never `now()`, or reprocess shifts history (ADR-014).
- A single text may carry several provinces and several metric sets; some numbers cannot be
  attributed to any single province (`her iki ilde toplam 10 kişi`). Never double-count.
- The synchronous listener runs inside the request. Watch the transaction boundary: the Mongo write
  must not be undone by a downstream Postgres failure.
- The SSE signal is published inside that same request, so a client's own submission can reach it
  **before** the POST response does. Correct either way — the client refetches — but do not write
  frontend code that assumes the id is known first.

## Open technical challenges

`docs/PRD.md` §10 lists TC-1…TC-18 — decisions deferred out of the PRD (number↔metric matching,
relative date ranges, SSE lifecycle, live-refresh strategy, frontend deployment, …). TC-1, TC-2, TC-5
and TC-12 are already decided. Do not silently pick one of the rest: surface the trade-off, decide
explicitly, then record it in `docs/DECISIONS.md`.

## Doc discipline

Any architectural or technology decision — including resolving one of the TCs above — is recorded in
`docs/DECISIONS.md` **in the same commit**, using the existing template
(Karar · Bağlam · Gerekçe · Alternatifler · Sonuçlar · İleride). The `İleride` section is required.
Keep this file under ~200 lines; it is loaded into context on every request.
