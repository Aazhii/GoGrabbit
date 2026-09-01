# GoGrabbit

A backend service that watches GitHub repositories for newly labeled issues
(`good first issue`, `help wanted`, etc.) and pushes real-time notifications
via **Email**, **Telegram**, and **Discord** — so you can grab a
beginner-friendly issue before someone else claims it.

Auth is out of scope for v1. See [docs/ROADMAP.md](docs/ROADMAP.md) for the
planned multi-user phase.

## Stack

| Layer      | Choice                                    |
|------------|--------------------------------------------|
| Backend    | Java 25 + Spring Boot 4.1                  |
| Frontend   | TypeScript + React (Vite)                  |
| Database   | PostgreSQL + Flyway + Spring Data JPA      |
| Scheduling | Quartz (JDBC job store, per-repo triggers) |
| Packaging  | Docker + Docker Compose                    |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full design and
[docs/DECISIONS.md](docs/DECISIONS.md) for why this stack was chosen over the
original Node.js/TypeScript sketch this project started from.

## Status

Planning phase — no code has been scaffolded yet. This repo currently holds
the architecture, schema, and setup plan; implementation starts once the plan
in `docs/ARCHITECTURE.md` is approved.

## Quick start (once scaffolded)

```bash
cp .env.example .env      # fill in GITHUB_TOKEN, notification credentials
docker compose up --build
```

- Backend API: http://localhost:8080
- Frontend dashboard: http://localhost:5173 (dev) / http://localhost:3000 (docker)
- Health check: http://localhost:8080/actuator/health
