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
  `GitHubService` over `RestClient` plus a small hand-rolled retry/backoff
  and manual rate-limit-header checks keeps the dependency surface small
  and the rate-limit handling explicit rather than hidden in a library. A
  bounded retry loop is a few lines; pulling in Resilience4j for a single
  outbound call site isn't worth it yet — revisit once notification
  dispatch adds per-channel circuit breakers, where the library's value
  (multiple wrapped call sites, declarative config) actually shows up.

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
  that shape than a Next.js app would justify. Server state is handled with
  plain `fetch` plus a small `useIssueSearch` hook — no query library is
  installed (runtime deps are `react` and `react-dom`, nothing else).
  TanStack Query remains a *future* option if saved searches or background
  refresh land; an earlier draft of this doc claimed it was already in use,
  which was never true.

## Deferred: `StructuredTaskScope` for notification fan-out

- Structured concurrency is still a preview API in Java 25 (JEP 505,
  fifth preview; finalization currently expected around JDK 27). Shipping
  on a preview API would force `--enable-preview` into the production
  build for a fan-out of three tasks that `CompletableFuture.allOf` on a
  virtual-thread executor already handles correctly. Revisit once it's
  finalized — it gives cleaner cancellation/cleanup semantics for the same
  job.


## Issue search runs through the backend, not Octokit in the browser

- Phase 1's core feature is issue-first discovery: search GitHub issues
  globally, rather than adding repos one by one to find out whether they
  have anything to work on.
- The search call lives in the Spring backend (`GitHubService.searchIssues`),
  not in the frontend via Octokit, for three reasons: `GITHUB_TOKEN` stays
  server-side (anything in a Vite bundle is public); the per-minute search
  budget is one shared, meterable pool instead of per-browser; and it reuses
  the `RestClient` bean, retry, rate-limit logging and error mapping that
  already exist.
- Worth stating plainly, because "prefer Octokit" was the starting
  assumption: **there is no official GitHub SDK for Java.** Octokit is
  official for JS/TS, Ruby, .NET and Terraform only. `org.kohsuke:github-api`
  is community-maintained, not official. Calling the REST API directly from
  Java — what this codebase already does — *is* the officially supported
  path, so there is nothing "more official" to migrate to.

## REST `/search/issues`, not the GraphQL search connection

- GraphQL would let us select exact fields and use cursor pagination, and
  it exposes `rateLimit` introspectively. But it shares one 5,000 points/hr
  budget with everything else, always requires auth, and returns a union
  type needing `... on Issue` casts — and there is no first-class GraphQL
  client in this Java stack.
- REST gives a clean, separate per-minute search budget and a flat JSON
  shape Jackson maps directly. Both APIs cap at 1,000 reachable results, so
  GraphQL buys nothing on the constraint that actually binds.

## `advanced_search=true` is sent explicitly

- GitHub made advanced search the default for issue queries on 2025-09-04;
  the endpoint itself is **not** deprecated. Passing the parameter
  explicitly is a no-op against today's default but pins the parsing
  semantics against a future flip.
- The semantic that changed and would bite silently: a space between
  multiple `repo:` / `org:` / `user:` qualifiers now means **AND**, not OR.
  A future "search several repos in one call" optimisation would return
  zero rows unless it uses an explicit `OR`. One query per repo, or explicit
  `OR`, is the safe shape.

## Phase 1 search stores nothing

- The search path touches no database table. No caching layer, no
  `search_history`, no background refresh — a search is one synchronous
  request to GitHub and the response is mapped straight to JSON.
- This is the deliberate simplest thing that fully solves Phase 1. The
  extension point for the future watch/trigger system is `GitHubService`,
  which both the search path and the existing repo-watch path already go
  through. No scheduler, queue, worker or webhook handler was added, and
  none is needed until that feature is actually built.

## Pagination is capped at GitHub's 1,000-result ceiling

- GitHub only allows 1,000 results to be paged through per search
  (`per_page` max 100 × 10 pages); asking beyond that returns 422, while
  `total_count` can report a far larger number.
- The backend validates `page * perPage <= 1000` and returns a 400 with a
  clear message rather than letting GitHub 422. `hasNextPage` is computed
  server-side against that ceiling, and the UI shows "N matches — GitHub
  only lets you page through the first 1,000" instead of rendering a page
  count it cannot deliver.
- `incomplete_results: true` (GitHub's search timed out, results partial)
  is passed through and surfaced rather than silently treated as complete.

## Explicit HTTP timeouts on the GitHub client

- The `RestClient` had no connect or read timeout at all, so a hung request
  was unbounded — and `executeWithRetry` would retry it three times over,
  turning one stall into three. Now 5s connect / 15s read, configurable via
  `github.connect-timeout` / `github.read-timeout`.
- Rate-limit rejections (GitHub 403/429) map to HTTP 429 with `Retry-After`
  rather than the generic 502, because "wait a minute" and "GitHub is
  broken" need different handling in the UI. 4xx is still never retried —
  a retry cannot fix a bad query.
