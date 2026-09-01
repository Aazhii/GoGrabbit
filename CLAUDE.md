# GoGrabbit — instructions for working in this repo

GitHub "good first issue" watcher. Full design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Why the stack looks the way it does: [docs/DECISIONS.md](docs/DECISIONS.md).
What's deliberately deferred: [docs/ROADMAP.md](docs/ROADMAP.md).

## Stack

Java 25 + Spring Boot 4.1 (Gradle, Kotlin DSL) backend; TypeScript + React
(Vite) frontend; PostgreSQL + Flyway; Quartz for per-repo scheduling; Docker
Compose for local/deploy. No auth in v1 — do not add auth-shaped code
(user tables, login, tokens-as-identity) until Phase 2 in the roadmap says so.

## Conventions

- Keep `docs/ARCHITECTURE.md` and `docs/DECISIONS.md` in sync with the code:
  if an implementation detail diverges from what's documented there (a
  different library, a schema change, a different scheduling approach),
  update the doc in the same change — don't let them drift into fiction.
- Migrations are Flyway SQL files under `backend/src/main/resources/db/migration`,
  never hand-edited schema or Hibernate `ddl-auto: update` in anything but
  local scratch testing.
- Notification channels (`EmailNotifier`, `TelegramNotifier`,
  `DiscordNotifier`) share one interface (see ARCHITECTURE.md) — a new
  channel (e.g. Slack, per the roadmap) means implementing that interface,
  not branching existing dispatch code.
- `WatchedRepo` CRUD always goes through `SchedulerService` to
  create/reschedule/remove the matching Quartz trigger — never write
  directly to the repository and leave the scheduler out of sync.

## Build / run / test

Not yet scaffolded — this section gets filled in as soon as the backend and
frontend projects exist. Until then, treat `docs/ARCHITECTURE.md` as the
source of truth for intended structure, not this file.
