# Decisions

Why each piece of the stack was chosen, mapped against the original
Node.js/TypeScript sketch this project started from. Verified against
current (2026-09) compatibility info, not assumed.

## Language / framework: Java 25 + Spring Boot 4.1

- Requested stack. Spring Boot 4.0.x officially supports up to Java 25;
  4.1.x (current stable, released 2026-06) adds Java 26 support and is
  first-class-tested on Java 25 — so Java 25 (LTS) + Spring Boot 4.1 is a
  fully supported, non-bleeding-edge combination, not a mismatch.
- Java 25 virtual threads (stable since 21) are a good fit for this
  workload: many concurrent, blocking HTTP calls (GitHub polling +
  three notification channels) without the thread-pool tuning a
  platform-thread model would need.

## Build tool: Maven (switched from the originally planned Gradle)

- Originally planned Gradle (Kotlin DSL) for faster incremental builds.
  In practice, `start.spring.io`'s Gradle project generator was returning
  a 500 (`gradleBuild` bean creation failure) for every combination tried
  as of 2026-09, and there was no local Docker daemon available in this
  environment to bootstrap a Gradle wrapper another way (no vendored
  wrapper jar to hand-write safely). Maven generation worked cleanly and
  its wrapper (`mvnw`) is plain scripts + a properties file — nothing
  binary to get right by hand. Reliably getting a working build beats the
  marginal dev-loop speed difference. Revisit if Gradle generation gets
  fixed upstream and the team wants the swap.

## Scheduling: Quartz, not `@Scheduled` / a hand-rolled `ThreadPoolTaskScheduler`

- The spec requires per-repo, runtime-configurable intervals with CRUD via
  REST — that's dynamic job management. `@Scheduled` is static config;
  `ThreadPoolTaskScheduler` can be driven dynamically but keeps its
  schedule only in memory, so a restart drops it (the same gap the
  original node-cron design had). Quartz's JDBC job store persists jobs
  in Postgres — schedules survive restarts with no extra reload logic.
  See [ARCHITECTURE.md](ARCHITECTURE.md) for the per-repo trigger design.

## Database access: Spring Data JPA + Flyway, not a Prisma-equivalent

- Direct swap for Prisma's role (schema-as-code, migrations, typed
  queries). Flyway migrations are plain SQL, which also lets the Quartz
  JDBC schema (its own standard DDL) live alongside the app schema in the
  same migration history.

## GitHub client: Spring's `RestClient` directly, not a GitHub SDK

- The original used Octokit for pagination/rate-limit handling. The Java
  ecosystem's equivalents (`kohsuke/github-api`, etc.) add a dependency for
  what's a handful of well-documented REST calls (`search/issues`). A thin
  `GitHubService` over `RestClient` plus Resilience4j retry/backoff and
  manual rate-limit-header checks keeps the dependency surface small and
  the rate-limit handling explicit rather than hidden in a library.

## Notification channels: HTTP calls over `RestClient`, no per-channel SDK

- **Discord**: webhook POST, no bot/library needed — same as the original.
- **Telegram**: raw Bot API `sendMessage` POST — skips the
  `node-telegram-bot-api`-equivalent Java libraries, which are built for
  full bot interactivity we don't need (we only send, never receive).
- **Email**: Resend's HTTP API via `RestClient`, matching the original's
  first choice over SMTP — one fewer protocol/library to configure
  (`spring-boot-starter-mail` + SMTP is the fallback if Resend isn't
  wanted).

## Frontend: React + Vite + TypeScript, not Next.js

- No SSR, no auth, no server-side routing need in v1 — it's a dashboard
  over a REST API. Vite's dev server and build are simpler and faster for
  that shape than a Next.js app would justify. TanStack Query handles
  server-state caching/refetch for the repos/channels/recent-issues views.

## Deferred: `StructuredTaskScope` for notification fan-out

- Structured concurrency is still a preview API in Java 25 (JEP 505,
  fifth preview; finalization currently expected around JDK 27). Shipping
  on a preview API would force `--enable-preview` into the production
  build for a fan-out of three tasks that `CompletableFuture.allOf` on a
  virtual-thread executor already handles correctly. Revisit once it's
  finalized — it gives cleaner cancellation/cleanup semantics for the same
  job.
