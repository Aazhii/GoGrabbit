# GoGrabbit — instructions for working in this repo

GitHub "good first issue" watcher. Full design: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Why the stack looks the way it does: [docs/DECISIONS.md](docs/DECISIONS.md).
What's deliberately deferred: [docs/ROADMAP.md](docs/ROADMAP.md).

## Stack

Java 25 + Spring Boot 4.1 (Maven) backend; TypeScript + React (Vite)
frontend; PostgreSQL + Flyway; Quartz for per-repo scheduling (not wired up
yet — see status below); Docker Compose for local/deploy. No auth in v1 —
do not add auth-shaped code (user tables, login, tokens-as-identity) until
Phase 2 in the roadmap says so.

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

## Status

- **Done**: backend (tables, `GitHubService`, `PollerService`, `/repos` +
  `/issues/recent` REST endpoints, CORS via `WebConfig`, upstream-error
  mapping to 502 in `GlobalExceptionHandler`, 409 on duplicate
  owner/repo) and a simple frontend dashboard (`frontend/`: add/list/remove
  watched repos, manual poll button, recent-issues feed with "posted
  within" and "repo" filters — plain fetch, no state library, one page,
  no routing). Verified end-to-end in a real browser against the real
  GitHub API and a real Postgres, via `docker compose up --build`.
- `seen_issue.posted_at` (added in `V2__seen_issue_posted_at.sql`) holds
  GitHub's `created_at` for the issue — that's what `/issues/recent`'s
  `sinceDays` filters and sorts on. `labeled_at` is still populated from
  `updated_at` and is a separate, looser signal — don't conflate the two.
- `SeenIssueRepository.search`'s optional-filter JPQL casts every
  null-checked parameter (`cast(:x as ...)`) — Postgres's JDBC driver can't
  infer a bind parameter's type from an `is null` check alone (each
  occurrence of a named JPQL parameter becomes its own untyped `$n`), and
  drops the whole query with "could not determine data type of parameter"
  without the cast. Keep this pattern for any future optional-filter query.
- **Not done yet**: Quartz scheduling (repos currently only poll via the
  manual `/repos/{id}/poll` endpoint / the dashboard's "Poll now" button —
  nothing runs on an interval yet), notification dispatch to
  Email/Telegram/Discord (`PollerService` just logs new issues for now),
  `NotificationChannel` CRUD (backend or UI), auth (Phase 2, intentionally
  deferred).
- When Quartz lands: `RepoController`'s create/update/delete must start
  going through a `SchedulerService` that also creates/reschedules/removes
  the matching trigger, per the convention above — right now it writes
  directly to `WatchedRepoRepository` because there's no scheduler yet.
- GitHub's Search API requires an explicit `is:issue` qualifier and does
  **not** resolve renamed repos (e.g. `facebook/react` → `react/react`) —
  `GitHubService` sends `is:issue`; a stale owner/repo name will 502 with a
  GitHub-forwarded message rather than silently returning nothing.
- Frontend never calls `window.alert`/`confirm`/`prompt` — those block the
  page (and break browser automation tooling). Poll results and errors are
  shown inline (`pollResult` / `error` state in `App.tsx`) instead.

## Build / run / test

```bash
cd backend
./mvnw compile        # verified working — Java 25 + Spring Boot 4.1
./mvnw test            # needs a reachable Postgres matching db/migration — see below
```

`spring-boot-starter-parent` in `pom.xml` must be a real published version,
e.g. `4.1.1` — **not** `4.1.1.RELEASE`. `start.spring.io`'s metadata
endpoint returns the latter as a version `id`, but that exact string was
never published to Maven Central; it 404s. Confirm against
`https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml`
before bumping the Boot version.

Full stack, including a real (Postgres 17) database and the frontend:

```bash
docker compose up --build
```

- Dashboard: http://localhost:3000
- API: http://localhost:8080 — try `POST /repos` then `POST /repos/{id}/poll`
- Health: http://localhost:8080/actuator/health

Postgres publishes on host port 5433, not 5432 — set via `POSTGRES_HOST_PORT`
in `docker-compose.yml`; changed because this machine already had an
unrelated local Postgres bound to 5432. The backend always talks to the
`postgres` service over the internal Docker network on 5432 regardless.

Frontend alone, with hot reload:

```bash
cd frontend
npm install
npm run build   # type-checks (tsc -b) then builds — verified working
npm run dev     # http://localhost:5173, reads VITE_API_BASE_URL (see .env.example)
```

