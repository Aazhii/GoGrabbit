# Roadmap

## Phase 1 — v1 (this plan)

Single-user, no auth. Repos, labels, and notification channels are managed
through the REST API / dashboard and stored in Postgres. See
[ARCHITECTURE.md](ARCHITECTURE.md).

## Phase 2 — Auth & multi-user

- Add a `User` entity; scope `WatchedRepo` and `NotificationChannel` to
  `userId`.
- Auth via Spring Security + OAuth2 login (GitHub OAuth is a natural fit
  given the domain) or a simple JWT + password flow.
- Per-user GitHub API token so rate limits scale with users instead of
  sharing one bot token; fall back to a shared pool token for users who
  haven't linked their own.
- Scheduler/dispatcher logic is already per-`WatchedRepo`, so this phase is
  additive (foreign key + auth middleware), not a rearchitecture.

## Nice-to-haves (unordered, pull forward as wanted)

- Slack notification channel (same shape as Discord: webhook POST).
- Multi-label AND/OR matching per watched repo, not just "any of".
- "Claimed" tracking — mark a notified issue as claimed, stop re-surfacing
  it in the dashboard (it's already deduped for notifications via
  `seen_issue`; this is a UI-only addition on top).
- Digest mode — batch notifications hourly instead of instant, per channel.
- Migrate polling to a GitHub App + `issues` webhook (`labeled` action) for
  repos the user controls or an org that allows app installs — removes
  polling delay entirely for those repos. Third-party repos the user
  doesn't control still need polling, so this is additive, not a
  replacement for the poller.
