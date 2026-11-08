# Spring Boot 3 Java Demo — Blocking RestController

A demo project showcasing Spring Boot 3 development in Java with **annotated controllers** (`@RestController`) on a
classic **blocking** stack: Spring MVC, Spring Data JDBC and **virtual threads**. It is the blocking twin of
[spring-boot-3-java-webflux-demo-restcontroller](../spring-boot-3-java-webflux-demo-restcontroller) (same API, schema and tests; only
the web and data layers differ), so the two can be compared side by side. Built on the domain of
[spring-boot-java-audit-skills-demo](../spring-boot-java-audit-skills-demo), and written to respect every invariant of
[audit-skills](../../audit-skills) (see [AGENTS.md](AGENTS.md)).

## Features

- **Annotated controllers** — `@RestController` in `controller/` with plain signatures (`PostDetails`,
  `List<PostSummary>`), business logic in `service/`, `ListCrudRepository` + `@Query` (Spring Data JDBC) in
  `repository/`
- **`AuthenticatedUser me` parameters** — resolved from the verified token by a custom argument resolver
- **Virtual threads** — one virtual thread per request (`spring.threads.virtual.enabled`): straightforward blocking
  code that scales without a reactive stack
- **Plain sequential code** — bulk loads + hash-map joins for N+1-free lists, `TransactionTemplate` for atomic
  multi-table writes
- **JWT resource server** — HS256 pinned; `exp`, `iss`, `aud` and claim shapes validated; deny-by-default routes
- **Tenant isolation** — every query is scoped to the caller's enterprise, taken from the verified token
- **Idempotent writes** — required `Idempotency-Key` on post creation; likes and profile upserts are naturally idempotent
- **Global exception handler** — `@RestControllerAdvice` (also reached by filter errors: 401/403/413), RFC 9457 problem details (`application/problem+json`) with a stable `type`, a
  localized `detail` and a `correlationId` matching the `X-Correlation-Id` header; only `AppException` messages reach
  the client
- **Bean Validation (i18n)** — `@Valid` bodies and constrained `@RequestParam`/`@PathVariable`/`@RequestHeader`
  (built-in method validation); messages interpolated in the request's locale; unknown JSON fields rejected; bounded
  pagination and body size (`RequestBodyLimitFilter`)
- **Custom validators** — `@SafeText` (field-level: rejects HTML tags and control characters) and `@NoDuplicates`
  (collection-level) in `validation/`, each a `ConstraintValidator` with localized messages; unit-tested without Spring
- **Swagger / OpenAPI** — Springdoc, with a bearer-token **Authorize** button (dev profile only)
- **Spotless** — palantir-java-format; a formatting violation fails the build
- **Testcontainers** — integration tests against a real PostgreSQL

## Tech Stack

Java 21 (virtual threads) · Spring Boot 3.5 · Spring MVC · Spring Security (OAuth2 resource server) ·
Spring Data JDBC · PostgreSQL · Flyway · Springdoc OpenAPI · Testcontainers

## Blocking vs reactive

| | This project | [Reactive twin](../spring-boot-3-java-webflux-demo-restcontroller) |
|---|---|---|
| Web | Spring MVC (Tomcat, servlet) | WebFlux (Netty) |
| Concurrency | a virtual thread per request | event loop + `Mono`/`Flux` |
| Data access | Spring Data JDBC (HikariCP) | Spring Data R2DBC |
| Return types | `PostDetails`, `List<PostSummary>` | `Mono<PostDetails>`, `Flux<PostSummary>` |
| Transactions | `TransactionTemplate` | `TransactionalOperator` |
| Caller / security context | `SecurityContextHolder` (thread-bound) | `ReactiveSecurityContextHolder` (Reactor context) |
| Request locale for validation | thread-bound, messages localized directly | re-interpolated afterwards (`ViolationMessages`) |
| Body size cap | `RequestBodyLimitFilter` | `spring.codec.max-in-memory-size` |

## Data Model

A small enterprise social network: each enterprise has its users, who have a profile, publish posts with images, and
like posts.

```
┌──────────────┐ 1    N ┌──────────────┐ 1    N ┌──────────────┐ 1    N ┌──────────────┐
│  Enterprise  │───────►│     User     │───────►│     Post     │───────►│    Image     │
│──────────────│        │──────────────│        │──────────────│        │──────────────│
│ id           │        │ id           │        │ id           │        │ id           │
│ name         │        │ enterprise_id│        │ user_id      │        │ post_id      │
└──────────────┘        │ name         │        │ content      │        │ url          │
                        │ email        │        │ created_at   │        │ position     │
                        └──┬────────┬──┘        └──────┬───────┘        └──────────────┘
                         1 │        │ N                │ N
                         1 ▼        │   ┌──────────┐   │
                ┌──────────────┐    └──►│post_likes│◄──┘
                │   Profile    │        │──────────│
                │──────────────│        │ user_id  │
                │ id           │        │ post_id  │
                │ user_id (UQ) │        └──────────┘
                │ bio          │
                │ avatar_url   │
                └──────────────┘
```

| Relation | Example | Endpoint |
|---|---|---|
| **belongsTo** | Post → User | `GET /posts/{id}` (`author`) |
| **hasOne** | User → Profile | `GET /users/{id}/profile` |
| **hasMany** | User → Posts, Post → Images | `GET /users/{id}/posts`, `GET /posts/{id}/images` |
| **hasManyThrough** | Enterprise → Posts, through Users | `GET /posts` (the enterprise feed) |
| **manyToMany** | User ↔ Post, through `post_likes` | `GET /users/{id}/liked-posts`, `GET /posts/{id}/likes` |

An `idempotency_keys` table backs `POST /posts` retries (see `V1__init.sql`).

## API

Every route requires `Authorization: Bearer <token>` except the docs and `/auth/dev-token`. A resource outside the
caller's enterprise answers **404**, exactly like a missing one.

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/dev-token` | Token for a seeded user, no password — **dev profile only** |
| `GET` | `/enterprises/current` | The caller's enterprise |
| `GET` | `/users/me` | The caller (with email) |
| `PUT` | `/users/me/profile` | Create or replace the caller's profile |
| `GET` | `/users?afterId=&limit=` | Users of the caller's enterprise, oldest first |
| `GET` | `/users/{id}` | A colleague (no email) |
| `GET` | `/users/{id}/profile` | A colleague's profile |
| `GET` | `/users/{id}/posts?beforeId=&limit=` | A colleague's posts, newest first |
| `GET` | `/users/{id}/liked-posts?beforeId=&limit=` | Posts a colleague likes |
| `GET` | `/posts?beforeId=&limit=` | Enterprise feed, newest first, with image and like counts |
| `POST` | `/posts` | Publish a post with up to 10 images — requires `Idempotency-Key` |
| `GET` | `/posts/{id}` | A post with author, images and like count |
| `PUT` | `/posts/{id}` | Edit content — author only (403 otherwise) |
| `DELETE` | `/posts/{id}` | Delete with its images — author only |
| `GET` | `/posts/{id}/images` | Images in display order |
| `GET` | `/posts/{id}/likes?afterId=&limit=` | Users who like the post |
| `PUT` / `DELETE` | `/posts/{id}/likes` | Like / unlike as the caller (idempotent, 204) |

Errors: **400** `/errors/param-invalid` when a parameter is missing or mistyped; **422** `/errors/validation-failed`
when a body field or parameter breaks a constraint. Both carry `errors` keyed by field or parameter name.

Lists use keyset pagination: pass the last `id` of a page as `beforeId` (newest-first lists) or `afterId`
(oldest-first lists). `limit` defaults to 20, max 100.

**Idempotency:** send a unique `Idempotency-Key` (8–128 chars, e.g. a UUID) with each new post. Retrying with the same
key and body returns the original post with `Idempotent-Replayed: true`; the same key with a different body is a
**409**.

## Environment Variables

Local development uses `.env` support through `spring-dotenv`. Copy `.env.example` to `.env`.

| Variable | Default | Role |
|---|---|---|
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | dev profile: `demo_spring_java_blocking_restcontroller` / `postgres` / `postgres`; none otherwise | Database |
| `DB_HOST` / `DB_PORT` | `localhost` / `5432` | Database address (`DB_HOST` outside the dev profile) |
| `SERVER_PORT` | dev: `8089`, otherwise `8080` | HTTP port |
| `JWT_SECRET` | dev: a public dev key; **required** otherwise (≥ 32 bytes) | HS256 verification key |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `blocking-restcontroller-demo` / `blocking-restcontroller-demo-api` | Expected `iss` / `aud` |

## Getting Started

1. Ensure a local PostgreSQL instance is running and create the `demo_spring_java_blocking_restcontroller` database
   > 💡 Need a quick local database? Use [Dev Services](https://github.com/danygiguere/dev_services) to spin up a dockerized PostgreSQL instance.
2. Copy `.env.example` to `.env` and adjust it to your local PostgreSQL settings
3. Run `./gradlew bootRun` — it starts the **dev** profile: Flyway applies the schema and the deterministic seed
4. Open Swagger UI at **http://localhost:8089/swagger-ui.html**, call `POST /auth/dev-token` with
   `{"email":"alice@example.com"}`, and paste the `accessToken` into **Authorize**

Seeded users (5 enterprises × 10 users, 200 posts, 300 images, 598 likes):

| User | Email | Enterprise | Posts |
|---|---|---|---|
| Alice (1) | `alice@example.com` | 1 | 1–4 |
| Bob (2) | `bob@example.com` | 1 | 5–8 |
| Eve (11) | `eve@example.com` | 2 | 41–44 |
| User *n* | `user<n>@example.com` | (n − 1) / 10 + 1 | 4(n − 1) + 1 to 4n |

### Why a dockerized database instead of an in-memory one (like H2)?

- **The same database as in production:** same engine, same version, same SQL dialect. No query that works locally and breaks on deploy.
- **The same migrations everywhere:** the exact Flyway scripts run in dev, in tests and in production, so a broken migration fails on your machine first.
- **Bugs show up in dev, not in prod:** constraints, locking, transactions, error codes and engine-specific SQL behave exactly as they will in production.
- **Realistic performance:** query plans, indexes and slow queries can be checked locally (`EXPLAIN`) on the real engine.
- **Real tools:** inspect and debug the data with your usual database client. The data survives restarts.
- **One setup for dev and tests:** the same image runs locally (Dev Services) and in integration tests (Testcontainers).

> In-memory databases are fine for quick prototypes, or for tests that don't touch SQL. As soon as the code depends on real database behavior, use the real thing.

### Profiles

| | `dev` (`bootRun`, tests) | none (packaged jar) |
|---|---|---|
| Seed data (`db/seed/R__dev_seed.sql`, empties tables) | ✅ | ❌ |
| `POST /auth/dev-token` | ✅ | ❌ (404) |
| Swagger UI / API docs | ✅ | ❌ (404) |
| Bind address | `127.0.0.1` | all interfaces |
| `JWT_SECRET` | optional (public dev key) | required, startup fails otherwise |

In production, tokens come from your identity provider: this application only verifies them. To use an asymmetric
provider (RS256 + JWKS), replace the decoder in `SecurityConfig` with `NimbusJwtDecoder.withJwkSetUri(...)`
and keep the same validators.

## Testing the API

A Postman collection (`postman_collection.json`) is included at the root of the project. Its **Get dev token** request
stores the token in the `token` collection variable used by every other request.

> 🌐 **i18n:** add `Accept-Language: fr` to any request to receive error messages in French.

## Commands

| Command | Description |
|---|---|
| `./gradlew clean build` | Check formatting with Spotless, compile, test and package |
| `./gradlew bootRun` | Start the application (dev profile) |
| `./gradlew test` | Run tests (Docker required for Testcontainers) |
| `./gradlew spotlessApply` | Auto-format all source files |
| `./gradlew spotlessCheck` | Check formatting without modifying files (CI) |
