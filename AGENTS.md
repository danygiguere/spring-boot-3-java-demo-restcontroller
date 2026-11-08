# AGENTS.md

Guidance for AI agents working in this repository. Read this before making changes.

## Contents

- [Project](#project)
- [Commands](#commands)
- [Agent behaviour rules](#agent-behaviour-rules)
- [Architecture](#architecture)
- [Security model](#security-model)
- [Testing](#testing)
- [Engineering audit digest](#engineering-audit-digest)

## Project

Spring Boot 3 + Java demo built on a **blocking** stack (Spring MVC, Spring Data JDBC, virtual threads), using
**annotated controllers** (`@RestController`) with plain signatures. Blocking twin of
`../spring-boot-3-java-demo-restcontroller` (same API, schema and tests).

**Stack:** Java 21 · Spring Boot 3.5 · Spring MVC · Spring Security (OAuth2 resource server, JWT) · Spring Data JDBC ·
PostgreSQL · Flyway · Springdoc/OpenAPI · Testcontainers

**Data model:** `Enterprise` 1—N `User` 1—1 `Profile`; `User` 1—N `Post` 1—N `Image`; `User` N—N `Post` via
`post_likes`. The enterprise is the tenant.

## Commands

| Command | Description |
|---|---|
| `./gradlew clean build` | Spotless check, compile, test, package (full CI gate) |
| `./gradlew bootRun` | Start the application with the `dev` profile |
| `./gradlew test` | Run tests (Docker required) |
| `./gradlew spotlessApply` | Auto-format all sources |
| `./gradlew spotlessCheck` | Verify formatting (runs before `compileJava`) |

`compileJava` depends on `spotlessCheck` — **a formatting violation fails the build.** Run
`./gradlew spotlessApply build` to auto-fix and build. Spotless uses palantir-java-format 2.50.0.

## Agent behaviour rules

- **Be ultra concise.** In code, comments, documentation, and responses — say as little as possible to convey the point.
- **Never commit on behalf of the user.** Do not run `git commit` unless explicitly asked.
- **Write documentation and comments as correct content directly.** Do not add text explaining why something changed;
  the content stands on its own.
- **Match the surrounding code** — naming, idioms, comment density, formatting.

## Architecture

Request flow, one layer per responsibility:

```
Controller (@RestController + OpenAPI, validation)  →  Service (business logic, permissions)  →  Repository (JDBC)
```

- **Controller** (`controller/`) — `@RestController`. Methods return `Dto` / `List<Dto>` (or
  `ResponseEntity<Dto>` when headers are needed). Take the caller as an `AuthenticatedUser me` parameter
  (`AuthenticatedUserArgumentResolver`; hidden from OpenAPI). Bodies use `@Valid @RequestBody`; params carry
  constraints with an explicit `message` key (`@Positive(message = "{param.positive}")`). No business logic.
- **Service** (`service/`) — `@Service`. Business logic, tenant scoping and permission checks. Returns DTOs;
  throws `AppException.*`. Multi-table writes go through `TransactionTemplate`.
- **Repository** (`repository/`) — `ListCrudRepository<Entity, Long>` + `@Query` (Spring Data JDBC); single
  results are `Optional`. A `@Query` INSERT … RETURNING is annotated `@Transactional` (query methods are read-only).
  Tenant-facing queries take the caller's `enterpriseId`; writes take the owner's `userId` in the WHERE clause and
  return the affected row count.
- **Entity** (`domain/entity/`) — `record` with `@Table` and `@Id Long id` (null before insert). Never serialized.
- **DTO** (`dto/`) — request records (validated input, explicit writable fields) and response records (explicit
  exposed fields). No response envelope; lists are JSON arrays with keyset pagination.

Blocking rules: requests run on virtual threads (`spring.threads.virtual.enabled`); avoid `synchronized` around
blocking I/O (it pins the carrier thread). Related data for a list is loaded in bulk (`IN (:ids)`, bounded by the page
limit) and joined through maps (`PostSummaryLoader`). Keep transactions short: read replays after commit.

## Security model

- Every route is authenticated except `SecurityConfig.PUBLIC_PATHS`. Deny by default.
- The caller is `AuthenticatedUser` (`sub` → `userId`, `enterpriseId` claim) from a verified JWT. Never take a user or
  enterprise id from the request for authorization or ownership.
- Out-of-tenant resources are **404**; in-tenant resources the caller may not change are **403**.
- `dev` profile only: seed data, `POST /auth/dev-token`, Swagger UI, public dev JWT key, loopback bind. The default
  (no profile) is production-safe.

## Testing

- Integration tests extend `support/IntegrationTest`: one reusable PostgreSQL Testcontainer, `dev` profile (seed +
  dev tokens), `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `WebTestClient`.
- `as(email)` returns a client authenticated as a seeded user.
- Tests that write create their own posts as users 3, 4 and 12; never change seeded posts/likes or users 1, 2, 11, so
  seed-based assertions hold in any order.
- Validation messages are localized at validation time (Spring MVC binds the request locale to the thread); keep a
  French test for each validation path.
- Tests use `WebTestClient` against the running server (`spring-webflux` is a test-only dependency).
- Assert status and the problem JSON (`$.type`, `$.status`, `$.errors.<field>`, `$.correlationId`); cover the success
  path, validation errors, and the cross-tenant / not-owner paths.

# Engineering audit digest

Apply the invariants below to all code you review, audit, or generate.
For an in-depth audit, use the `audit` skill (`/audit`): it maps each topic
to its detailed checklist under `references/`.

## Invariants (always apply)

### Access & data security

- **Authorization** — every state-changing or sensitive action is permission-checked server-side at the point of action,
  not only in the UI or routing layer.
- **Authentication & sessions** — sessions are regenerated on login and invalidated on logout; credential and reset
  flows do not reveal whether an account exists.
- **Token & webhook validation** — stateless credentials (JWTs, API keys, signed URLs, webhook signatures) are
  cryptographically verified before any claim is trusted: algorithm pinned, exp/aud/iss validated, webhook HMACs
  computed over the raw body within a bounded timestamp window.
- **IDOR** — any resource looked up by an identifier from the request is verified as owned by or visible to the current
  principal; a valid ID is not authorization.
- **Data exposure** — responses, errors, and logs contain only fields explicitly intended for the consumer; never whole
  models, stack traces, or PII by default.
- **Crypto & data protection** — passwords use adaptive hashing; tokens come from a CSPRNG; secret comparisons are
  constant-time; no homemade crypto.
- **Output encoding** — every user-controlled value rendered into HTML, JS, CSS, URLs, headers, or emails is encoded for
  that exact context.
- **Tenant isolation** — in multi-tenant code, every query, cache key, and background job is scoped by tenant; no
  implicit global reads.
- **CSRF** — state-changing endpoints authenticated by cookies verify a CSRF token or origin (not applicable to pure
  token-based APIs).
- **Mass assignment** — request data is never bound wholesale onto models; every binding site has an explicit,
  per-context allowlist of writable fields (allowlists, never denylists).

### Input, API & dependencies

- **Injection** — queries, shell commands, templates, and paths are built with parameterization or escaping APIs, never
  by concatenating input.
- **Configuration** — production config denies by default: debug off, CORS explicit, security headers present, cookies
  Secure/HttpOnly.
- **Secrets** — credentials live in env vars or secret managers; never hardcoded, logged, or committed; rotation is
  possible.
- **API contract & validation** — every input is validated at the boundary for type, bounds, and allowed fields; unknown
  fields are rejected or ignored explicitly.
- **File handling** — file paths are never derived from raw input; uploads are constrained by type, size, and storage
  location; served files cannot escape their root.
- **SSRF** — server-side requests to user-influenced URLs validate the destination against an allowlist; redirect
  targets count as destinations.
- **Parser differentials** — a gate must interpret input exactly as its consumer does: anchored matches, exact-host
  allowlists, validate the parsed object and pass that object on — never validate raw input and re-parse it.
- **Insecure deserialization** — untrusted bytes are never fed to a deserializer that can instantiate arbitrary types
  or run code; parse data-only formats into known target types, or restrict to a type allowlist plus a verified signature.

### Correctness

- **Atomicity** — writes that must succeed together run in one transaction or have explicit compensation; partial state
  never survives a failure.
- **Idempotency** — any handler that can run twice (retries, webhooks, double submits) produces the same outcome as
  running once.
- **Background work** — jobs have bounded retries, dead-letter handling, and timeouts, and tolerate duplicate or
  out-of-order delivery.
- **State management** — no check-then-act on shared state without a lock, atomic primitive, or database constraint
  backing it.
- **Cardinality** — any operation that assumes a predicate matches exactly one row is backed by a uniqueness guarantee
  (primary key or UNIQUE constraint); every UPDATE/DELETE is provably single-target or deliberately bounded, never
  fanning out on a non-unique or missing WHERE clause.
- **Exception handling** — errors are handled or propagated, never swallowed; catches are narrow, causes preserved,
  resources released; HTTP boundaries return the status the condition means (404/401/403/422/409, never 200-with-error).
- **Discarded async work** — every promise, future, task, or publisher is awaited, returned, composed, or
  deliberately detached with error handling; a cold producer that is never subscribed silently never runs.
- **Numeric & money precision** — money and exact quantities use decimal or integer minor units, never binary float;
  rounding happens once with an explicit mode; division remainders, overflow, and unit/currency mismatches are handled.
- **Time, timezone & clock** — instants are stored in UTC and localized only at display; durations and expiry read a
  monotonic clock, not wall time; aware and naive values are never mixed, and calendar math respects DST and month length.

### Operability

- **N+1 queries** — never query inside a loop over a collection; load related data in bulk.
- **Observability** — every failure path logs with context; new endpoints and jobs emit enough signal for their failures
  to be visible in production.
- **Migration safety** — schema changes deploy without downtime: no long locks, destructive changes split across
  releases, rollback considered.
- **Resource limits** — all work driven by input size is bounded: pagination, body and upload caps, rate limits, no
  catastrophic regex; `IN` lists and bulk statements built from growing data are chunked or passed as one array parameter.
- **Algorithmic complexity** — in-memory work over growing collections uses a structure that fits the access
  pattern: hash map or set for exact lookups and joins, sorted data with binary search for ranges; no linear scan
  inside a loop, no hidden quadratic copies; binary search runs only on data sorted by the same comparator.
- **Blocking I/O in async code** — code on a cooperative scheduler (event loop, coroutines, reactor) never blocks: no
  sync I/O, sleeps, or CPU-heavy work on the loop; blocking work is offloaded and outbound calls have timeouts.
- **Schema design** — relationships enforced by real foreign keys with deliberate ON DELETE; hot query paths backed
  by indexes; integrity rules (NOT NULL, unique, checks) live in the schema, not only in app validation.
- **Statelessness** — in replicated apps, state lives in process memory or local disk only if losing it is harmless
  and no peer replica needs it; sessions, counters, locks, uploads, and schedules live in shared stores.
- **Caching** — cache keys include every dimension the value varies on (principal, tenant, locale); entries are
  invalidated when their source changes; misses don't stampede the origin; per-user data never lands in a shared or CDN cache.

Do not manufacture findings for topics that do not apply (e.g., CSRF on a
token-authenticated API). Verify every finding against surrounding code
(middleware, base classes, callers) before reporting it.

---

*From [audit-skills](https://github.com/danygiguere/audit-skills) — the
digest and the audit skills are maintained there; check its tags for newer
releases.*
