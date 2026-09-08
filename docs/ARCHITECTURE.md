# Architecture

## Context

This project started from a Node.js/TypeScript design sketch (Express +
node-cron + Prisma). We're rebuilding it on **Java 25 + Spring Boot** for the
backend and **TypeScript + React** for the frontend, because that's the
target stack for this project and it maps well onto the problem: a
long-running, I/O-bound worker (poll GitHub, fan out notifications) that also
serves a small REST API. See [DECISIONS.md](DECISIONS.md) for the
component-by-component rationale.

Goals, in order: **correct** (no duplicate/missed notifications) → **fast**
(new issues notified within one poll interval, dashboard feels instant) →
**reliable** (survives restarts, a slow/broken notification channel doesn't
block others or lose scheduling state).

## High-level flow — issue search (Phase 1, primary path)

Issue-first discovery: the user searches GitHub issues directly across all
repositories, instead of adding repos one at a time and hoping they contain
something to work on. This path is **stateless** — it touches no database
table at all.

```
 ┌────────────────────┐        ┌─────────────────────────┐
 │   React SearchForm  │◀──────▶│  IssueSearchController   │  GET /issues/search
 │  (labels, repo,     │  JSON  │  (validate + clamp)      │  no auth (v1)
 │   created, sort)    │        └──────────┬──────────────┘
 └────────────────────┘                    ▼
                                ┌──────────────────────────┐
                                │   IssueSearchService      │  maps + drops PRs,
                                │                            │  computes paging
                                └──────────┬───────────────┘
                                            ▼
                                ┌──────────────────────────┐
                                │  GitHubSearchQueryBuilder │  pure: filters → the
                                │                            │  GitHub `q` string
                                └──────────┬───────────────┘
                                            ▼
                                ┌──────────────────────────┐
                                │  GitHubService.searchIssues│ ← the adapter seam
                                │  (RestClient, retry,       │
                                │   rate-limit aware)        │
                                └──────────┬───────────────┘
                                            ▼
                                   GitHub REST /search/issues
```

`GitHubService` is the single outbound seam. The repo-watch path below and
this search path both go through it, which is where a future watch/trigger
system plugs in — no scheduler, queue, or worker exists or is needed for
Phase 1.

Deliberately absent from this path: no persistence, no caching layer, no
background refresh. A search is one synchronous request to GitHub. See
`docs/ROADMAP.md` for what is deferred and why.

## High-level flow — repo watch path (partially built)

> Note: the Quartz scheduler in this diagram **does not exist yet** — there is
> no Quartz dependency, no `scheduler/` package and no `QRTZ_*` migration.
> Repos poll only via the manual `POST /repos/{id}/poll` endpoint. See the
> status notes in `CLAUDE.md`.

```
 ┌────────────────────┐        ┌─────────────────────┐
 │   React dashboard   │◀──────▶│   Spring Boot API    │  REST, no auth (v1)
 │  (repos, channels,  │  JSON  │ (controller layer)   │
 │   recent issues)    │        └──────────┬───────────┘
 └────────────────────┘                    │
                                            ▼
                                ┌───────────────────────┐
                                │  WatchedRepo / Channel │  Postgres (JPA)
                                │      tables            │
                                └──────────┬─────────────┘
                                            │ on create/update/delete
                                            ▼
                                ┌───────────────────────┐
                                │   Quartz Scheduler      │  one trigger per
                                │  (Postgres job store)   │  WatchedRepo,
                                └──────────┬─────────────┘  own interval
                                            │ fires PollRepoJob
                                            ▼
                                ┌───────────────────────┐
                                │    GitHubService        │  RestClient,
                                │  (search issues by      │  executeWithRetry
                                │   label, rate-limit      │  (hand-rolled,
                                │   aware)                 │   see DECISIONS)
                                └──────────┬─────────────┘
                                            ▼
                                ┌───────────────────────┐
                                │  Diff against seen_issue│  unique on
                                │  table                   │  (github_issue_id,
                                └──────────┬─────────────┘  watched_repo_id)
                                            │ new match
                                            ▼
                                ┌───────────────────────┐
                                │ NotificationDispatcher  │  fans out to all
                                │  (virtual-thread         │  active channels
                                │   executor, per-channel  │  concurrently
                                │   retry + isolation)     │
                                └───┬─────────┬─────────┬─┘
                                    ▼         ▼         ▼
                                 Email    Telegram    Discord
                                (Resend   (Bot API    (Webhook
                                 HTTP)     HTTP POST)  POST)
```

## Why Quartz instead of node-cron / `@Scheduled`

The API lets users add/remove/update watched repos at runtime, each with its
own poll interval — that's dynamic job management, not a fixed set of cron
expressions known at startup. Plain Spring `@Scheduled` is static; a
hand-rolled `ThreadPoolTaskScheduler` with tracked `ScheduledFuture`s per repo
works but loses all schedule state on restart (same weakness the original
node-cron design had). **Quartz with a JDBC job store in the same Postgres
database** gives us that dynamic add/remove/reschedule *and* survives
restarts for free — jobs pick back up on boot without the app needing to
reload and re-register every watched repo. This is a deliberate improvement
over the source design, not just a language port.

- One `JobDetail` + `Trigger` per `WatchedRepo`, keyed by `watchedRepoId`.
- `SchedulerService` creates/reschedules/deletes the trigger whenever a repo
  is created, has its `intervalMinutes` changed, or is deactivated/deleted.
- `PollRepoJob` reads the repo id from the `JobDataMap`, loads the entity,
  and delegates to `PollerService`.

## Concurrency model

- **Virtual threads** (`spring.threads.virtual.enabled=true`) handle both
  incoming HTTP requests and the blocking I/O calls made while polling
  GitHub and dispatching notifications — cheap enough that N repos polling
  concurrently, each fanning out to 3 notification channels, never exhausts
  a platform-thread pool.
- Notification dispatch per new issue runs the three channel calls
  concurrently on a virtual-thread-per-task executor and joins them; a
  failure or timeout in one channel (e.g. Telegram down) is isolated and
  logged, and doesn't delay or fail Email/Discord.
- We are **not** using `StructuredTaskScope` yet — structured concurrency is
  still a preview API in Java 25 (JEP 505; expected to finalize around JDK
  27). Using it now would require `--enable-preview` in a production build,
  which isn't worth it for a fan-out this simple. Revisit once it's final —
  it's a strict improvement (bounded cleanup, propagated cancellation) over
  the `CompletableFuture.allOf` approach we start with.

## Rate limiting & resilience

- **The search bucket is per-minute, and separate from the general REST
  budget**: 10 requests/min unauthenticated, 30/min authenticated. This is
  *not* the 60/hr vs 5000/hr figure quoted in `.env.example`, which governs
  non-search endpoints. Every issue search — including every filter change
  in the UI — spends one request from that per-minute bucket, so setting
  `GITHUB_TOKEN` matters far more here than for polling.
- `GitHubService` reads `x-ratelimit-remaining` / `x-ratelimit-reset` off
  every response; below a low-water mark it logs a warning. The threshold
  scales off `x-ratelimit-limit` rather than using the raw configured
  value, because a fixed threshold of 100 would warn on every single search
  call against a bucket of 10–30.
- GitHub answers a rate-limited caller with 403/429; `GlobalExceptionHandler`
  maps that to **HTTP 429** with a `Retry-After` header and an explanatory
  message, rather than burying it in the generic 502 upstream mapping. The
  UI surfaces this as its own error state.
- The GitHub `RestClient` sets explicit connect (5s) and read (15s)
  timeouts via `github.connect-timeout` / `github.read-timeout`. Without
  them a hung request had no bound at all, and `executeWithRetry` would
  retry it three times over.
- `GitHubService.executeWithRetry` wraps the search call with a small
  built-in retry (3 attempts, exponential backoff starting at 300ms) on
  transient failures (`ResourceAccessException`, 5xx) — not Resilience4j;
  see `docs/DECISIONS.md` for why. 4xx failures (bad query, unknown repo,
  auth) aren't retried since a retry can't fix them. Notification-channel
  resilience (retry, circuit breaker per channel) is deferred until those
  channels are implemented — multiple call sites is when a library like
  Resilience4j starts to pay for itself over hand-rolled retry.

## Data model (Postgres, via Flyway)

```sql
create table watched_repo (
    id               uuid primary key default gen_random_uuid(),
    owner            text not null,
    repo             text not null,
    labels           text[] not null,
    interval_minutes int not null default 30,
    active           boolean not null default true,
    created_at       timestamptz not null default now(),
    unique (owner, repo)
);

create table seen_issue (
    id              uuid primary key default gen_random_uuid(),
    github_issue_id bigint not null,
    watched_repo_id uuid not null references watched_repo(id) on delete cascade,
    title           text not null,
    url             text not null,
    labeled_at      timestamptz not null,
    posted_at       timestamptz not null,  -- GitHub issue's created_at; added in V2, what "posted N days ago" filters on
    notified_at     timestamptz not null default now(),
    unique (github_issue_id, watched_repo_id)
);

create table notification_channel (
    id      uuid primary key default gen_random_uuid(),
    type    text not null check (type in ('EMAIL', 'TELEGRAM', 'DISCORD')),
    target  text not null,
    active  boolean not null default true
);
```

`labels` maps to `String[]` via Hibernate's array `@JdbcTypeCode` support —
no join table needed for a v1 filter list.

Quartz's own tables (`QRTZ_*`) are added by the standard Quartz Postgres DDL,
run as its own Flyway migration.

## Module layout

```
backend/src/main/java/com/gograbbit/
├── config/          # GitHubClientConfig (RestClient + timeouts), WebConfig (CORS)
├── controller/       # IssueSearchController, RepoController, IssueController,
│                      #   GlobalExceptionHandler
├── domain/           # WatchedRepo, SeenIssue, NotificationChannel (JPA entities)
├── repository/       # Spring Data JPA repositories
├── service/          # GitHubService, GitHubSearchQueryBuilder, IssueSearchService,
│                      #   PollerService
├── dto/               # request/response records + validation
└── GoGrabbitApplication.java

Not yet built (documented elsewhere in this file as the target design):
  notification/  — EmailNotifier / TelegramNotifier / DiscordNotifier
  scheduler/     — SchedulerService, PollRepoJob (Quartz)
  ChannelController + /channels endpoints
```

```
frontend/src/
├── api.ts          # thin fetch wrapper — one function per endpoint
├── types.ts         # WatchedRepo, SeenIssue, IssueSearchResult/Response
├── App.tsx           # tab shell: Search (default) | Watch
├── components/      # SearchForm, IssueCard, Pagination, SearchResults,
│                     #   Spinner, EmptyState, ErrorNotice, WatchTab
├── hooks/            # useIssueSearch (abort + run-id guarded)
├── lib/               # relativeTime, labelColor, errors, searchForm
└── main.tsx
```

Still no router and no state/query library — tabs are a `useState`, and
there are exactly two of them. `App.tsx` was split into `components/` once
issue search landed, because a search form + result cards + pagination +
loading/error/empty states is more than one screen's worth of interaction.

Server-state caching is still manual. `useIssueSearch` owns `loading` /
`error` / `data` and cancels a superseded request with an `AbortController`
plus a monotonic run-id, so a slow earlier response cannot overwrite a
newer one. Revisit TanStack Query if saved searches or background refresh
land (Phase 2) — not before.

## API (v1, no auth)

| Method | Endpoint         | Description                                             |
|--------|------------------|-----------------------------------------------------------|
| GET    | `/repos`         | List watched repos                                       |
| POST   | `/repos`         | Add a repo `{owner, repo, labels, intervalMinutes}`       |
| PATCH  | `/repos/{id}`    | Update labels/interval/active (reschedules Quartz trigger) |
| DELETE | `/repos/{id}`    | Stop watching a repo (removes trigger)                     |
| GET    | `/issues/search` | **Phase 1 primary.** Live GitHub issue search. Params: `q`, `labels`, `state`, `owner`, `repo`, `createdWithinDays`, `createdFrom`, `createdTo`, `sort`, `order`, `page`, `perPage`. Stateless — hits GitHub, stores nothing |
| POST   | `/repos/{id}/poll` | Poll one watched repo now (currently the only way anything polls) |
| GET    | `/issues/recent` | Issues *already seen by a poll*, from the `seen_issue` table — filterable by `sinceDays`, `owner`, `repo`, `limit`, sorted by `postedAt desc`. Not a GitHub search |
| GET    | `/channels`      | List notification channels                                 |
| POST   | `/channels`      | Add a channel `{type, target}`                              |
| DELETE | `/channels/{id}` | Remove a channel                                             |
| GET    | `/actuator/health` | Health check (Spring Boot Actuator, built in)              |

## Deployment

`docker-compose.yml` with three services: `postgres`, `backend` (multi-stage
Maven build → `eclipse-temurin:25-jre`), `frontend` (Vite build → static
files served by nginx). Backend is a normal long-running JVM process — no
serverless/short-timeout constraints, so Quartz's in-process scheduler works
without extra infrastructure.

## Testing

**Current state**: JUnit 5 only. `GitHubSearchQueryBuilderTest` (25 tests)
and `IssueSearchServiceTest` (9) cover filter→query translation and
response mapping/PR-filtering as pure unit tests — no Spring context, no
Postgres, no network. `GitHubServiceTest` (3) covers the retry policy.
`GoGrabbitApplicationTests.contextLoads` is a `@SpringBootTest` and is the
one test that needs a reachable Postgres.

**Target** (not yet added — neither is in `pom.xml`): Testcontainers for a
real Postgres, and WireMock to stand in for the GitHub API so tests never
hit the real service.
