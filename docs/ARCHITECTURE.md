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

## High-level flow

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
                                │  (search issues by      │  Resilience4j
                                │   label, rate-limit      │  retry/backoff
                                │   aware)                 │
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

- `GitHubService` reads `x-ratelimit-remaining` / `x-ratelimit-reset` off
  every response; below a low-water mark it logs a warning and skips that
  cycle's remaining lookups rather than burning the budget to zero.
- Resilience4j wraps outbound calls (GitHub search, each notification
  channel) with retry + exponential backoff; a circuit breaker per
  notification channel type prevents hammering a channel that's down.

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
├── config/          # SchedulerConfig, RestClientConfig, virtual-thread executor bean
├── controller/       # RepoController, ChannelController, IssueController
├── domain/           # WatchedRepo, SeenIssue, NotificationChannel (JPA entities)
├── repository/       # Spring Data JPA repositories
├── service/          # PollerService, GitHubService, NotificationDispatchService
├── notification/     # EmailNotifier, TelegramNotifier, DiscordNotifier (+ shared interface)
├── scheduler/         # SchedulerService, PollRepoJob (Quartz Job)
├── dto/               # request/response records + validation
└── GoGrabbitApplication.java
```

```
frontend/src/
├── api.ts        # thin fetch wrapper — one function per endpoint
├── types.ts       # WatchedRepo, SeenIssue (mirror the response DTOs)
├── App.tsx         # everything: add-repo form, watched-repo table, recent-issues feed
└── main.tsx
```

Deliberately one file, one page, no router, no state/query library — v1
has three views' worth of content and one person's workflow (add a repo,
poll it, see what it found). `useState` + a manual `refresh()` after each
mutation is enough. Split `App.tsx` into `pages/`/`components/` (and
consider TanStack Query for caching) once there's more than one screen's
worth of interaction to justify it — e.g. once `NotificationChannel`
management or Quartz-driven live updates land.

## API (v1, no auth)

| Method | Endpoint         | Description                                             |
|--------|------------------|-----------------------------------------------------------|
| GET    | `/repos`         | List watched repos                                       |
| POST   | `/repos`         | Add a repo `{owner, repo, labels, intervalMinutes}`       |
| PATCH  | `/repos/{id}`    | Update labels/interval/active (reschedules Quartz trigger) |
| DELETE | `/repos/{id}`    | Stop watching a repo (removes trigger)                     |
| GET    | `/issues/recent` | Recently notified issues (history feed)                    |
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

JUnit 5 + Testcontainers (real Postgres, real Quartz JDBC store) for
integration tests; WireMock to stand in for the GitHub API and notification
webhooks so tests don't hit real external services.
