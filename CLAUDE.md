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

- **Done — universal GitHub search (the primary feature)**: all seven
  search types (repositories, issues, users, code, commits, topics,
  labels) behind two endpoints — `GET /search/catalog` and
  `GET /search/{type}`. Stateless: no table, no cache.
  - The `search/` package is **spec-driven**. `SearchCatalog` declares each
    type (endpoint, sorts, groups, ~116 qualifiers total) and
    `SearchQueryBuilder` renders arbitrary request params into a GitHub
    query against that spec. **Adding a qualifier is one catalog entry** —
    do not add a `@RequestParam` per filter, that is what this replaced.
  - The frontend generates its whole filter sidebar from `/search/catalog`,
    so a new qualifier appears in the UI with no frontend change at all.
  - Request params are **camelCase** (`goodFirstIssues`, `isFeatured`) and
    map to GitHub's hyphenated qualifiers (`good-first-issues:`,
    `is:featured`) via `QualifierSpec.githubQualifier`.
- The older single-purpose `GET /issues/search` still exists and works, but
  nothing in the UI calls it any more — the Search tab goes through
  `/search/issues`. Retiring it is a separate decision.
- **Done — repo watch (pre-existing, still works)**: tables, `PollerService`,
  `/repos` + `/issues/recent`, CORS via `WebConfig`, 502 upstream mapping and
  409 on duplicate owner/repo. Now lives behind the "Watch" tab. Note
  `/issues/recent` reads the `seen_issue` table and does **not** call GitHub —
  don't confuse it with `/issues/search`.
- Adding a repo (`POST /repos`) auto-triggers a poll immediately (see
  `App.tsx` `handleAddRepo` → `handlePoll`) — a repo you just added is
  never silently empty until someone remembers to hit "Poll now". Added
  after a real user hit exactly that: added a repo, applied a date filter,
  saw "no issues match" and assumed the GitHub search results were wrong —
  they weren't; the repo just had zero `seen_issue` rows because it had
  never been polled. If you add another way to create a `WatchedRepo`
  (e.g. a future bulk-import), poll it too, immediately, don't rely on
  Quartz's first tick.
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
- GitHub's Search API does **not** resolve renamed repos inside a `repo:`
  qualifier (e.g. `facebook/react` → `react/react`) — a stale owner/repo
  name will 502 with a GitHub-forwarded message rather than silently
  returning nothing. The REST repo endpoint *does* redirect (301), and the
  client follows redirects (`Redirect.NORMAL`) so repo-id resolution for
  label search survives a rename.
- **Search rate limits are per MINUTE and a separate bucket: 10/min
  unauthenticated, 30/min with a token.** This is not the 60/hr vs 5000/hr
  figure in `.env.example`, which covers non-search endpoints. Every UI
  search spends one request. GitHub 403/429 maps to HTTP 429 with
  `Retry-After`, not the generic 502.
- **Only 1,000 results are reachable** per search (`per_page` 100 × 10
  pages; beyond that GitHub 422s), even when `total_count` is far larger.
  The backend rejects `page * perPage > 1000` with a 400, and the UI says
  so rather than faking a page count.
- Advanced search became GitHub's default on 2025-09-04 and the
  `advanced_search` parameter is now marked **deprecated** in GitHub's REST
  reference — we no longer send it. Under advanced search a space between
  multiple `repo:`/`org:`/`user:` qualifiers means **AND**, not OR, so a
  future multi-repo single-query optimisation would silently return zero
  rows without an explicit `OR`.
- **A `/search/issues` query must name `is:issue` or `is:pull-request` or
  GitHub 422s.** `SearchQueryBuilder` adds `is:issue` when the caller's
  filters imply neither — without it, an Issues search with no type
  selected fails. Do not remove that default; several builder tests assert
  the resulting `is:issue` prefix.
- Sort enums differ per search type and a wrong one is a 422: repositories
  `stars|forks|help-wanted-issues|updated`, issues
  `created|updated|comments|reactions*|interactions`, users
  `followers|repositories|joined`, code `indexed` only, commits
  `author-date|committer-date`, labels `created|updated`, and **topics
  accepts no sort at all**. The catalog encodes this; trust it over memory.
- Code search is a **separate 10/min bucket** (`code_search`) and requires a
  token; the other six share the 30/min `search` bucket. Responses carry a
  `rateLimit` object read off GitHub's headers, and the UI displays it.
- Label search needs a numeric `repository_id` and accepts **no qualifiers**
  — `/search/labels` takes `owner`+`repo` and resolves the id for you.
- Every PR is also an issue. `is:issue` is sent *and* items with a non-null
  `pull_request` are dropped in `IssueSearchService` — belt and braces.
- `order` is ignored by GitHub unless `sort` is also sent; `sort=bestmatch`
  means omit both.
- There is **no official GitHub SDK for Java** (Octokit is JS/Ruby/.NET/
  Terraform only; `org.kohsuke:github-api` is community). Calling REST
  directly from `GitHubService` is the officially supported path — don't
  "upgrade" it to an SDK expecting something more official.
- Frontend never calls `window.alert`/`confirm`/`prompt` — those block the
  page (and break browser automation tooling). Poll results and errors are
  shown inline (`pollResult` / `error` state in `WatchTab.tsx`, and
  `ErrorNotice` for search) instead.

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

