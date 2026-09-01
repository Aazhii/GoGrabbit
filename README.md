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

Backend (tables, GitHub API integration, repos/issues REST endpoints) and a
simple frontend dashboard are working end-to-end. Not yet built: automatic
scheduling (Quartz — polling is manual via a button for now), notification
dispatch (Email/Telegram/Discord), auth. See `CLAUDE.md` for the detailed
status and `docs/ROADMAP.md` for what's next.

## Quick start

```bash
cp .env.example .env      # optionally fill in GITHUB_TOKEN
docker compose up --build
```

- Frontend dashboard: http://localhost:3000
- Backend API: http://localhost:8080
- Health check: http://localhost:8080/actuator/health

Postgres is published on host port 5433 (not the default 5432) to avoid
clashing with any Postgres already running on your machine.

For frontend development with hot reload instead of the built Docker image:

```bash
cd frontend
cp .env.example .env      # VITE_API_BASE_URL, defaults to http://localhost:8080
npm install
npm run dev               # http://localhost:5173
```
