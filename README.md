# Trash Bin — Stores Your Trash

> *Nothing is truly trash until you decide it is.*

Trash Bin is a hybrid **digital archive + digital memory** web application. You upload files you no
longer actively need but may want again. Trash Bin preserves the original bytes, plus your notes,
tags and context, so you can search, preview, download, restore and rediscover them later.

**Project status:** `M4 — retrieve` complete. Accounts, sessions, uploads (signature, duplicate and quota
checks), the archive, file details, tags, collections, favorites, metadata editing, full-text search
(including text inside PDF, Office and text files), download and inline preview work end to end.
Discard, restore and rediscovery arrive in M5. Progress is tracked against the milestones in [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md).

---

## Planned capabilities (v1)

- Accounts with JWT access tokens and rotating refresh tokens
- Upload with server-side signature verification and SHA-256 duplicate detection
- Notes, "why I kept this" context, origin date, tags and collections
- Full-text search across names, notes, tags and extracted document text
- Secure download and inline preview (images, PDF, text)
- Lifecycle: **Archived → Discarded → Purged**, with restore during the retention window
- Rediscovery without AI: "on this day", "forgotten", "surprise me", activity timeline

Out of scope for v1: sharing/teams, AI/OCR, email flows, file versioning, mobile apps. See
[docs/PROJECT_REQUIREMENTS.md](docs/PROJECT_REQUIREMENTS.md).

## Technology

| Layer | Choice |
|---|---|
| Frontend | Next.js (App Router), React, TypeScript (strict), Tailwind CSS, shadcn/ui, Lucide |
| Backend | Java 21, Spring Boot 3.5.16, Spring Web / Security / Data JPA, Hibernate, Bean Validation, Actuator |
| Database | PostgreSQL 16+, Flyway, UUID identifiers |
| Binary storage | Local filesystem behind a `BlobStore` port |
| Content | Apache Tika (type detection, text extraction), SHA-256 |
| API docs | OpenAPI + Swagger UI (dev profile only) |
| Tests | JUnit 5, Mockito, Spring Boot Test, ArchUnit, Vitest, Testing Library, MSW, Playwright |

Architecture in one line: a **modular monolith** (Spring Boot) behind a **Next.js** app that proxies
`/api/*`, with metadata in PostgreSQL and bytes on disk. Details in
[docs/SYSTEM_ARCHITECTURE.md](docs/SYSTEM_ARCHITECTURE.md).

## Repository layout

```
TrashBin/
├─ backend/               Spring Boot application (Maven, wrapper included)
├─ frontend/              Next.js application (npm)
├─ database/              Database documentation and local setup script (schema lives in backend/ Flyway migrations)
├─ docs/                  Engineering documentation (see below)
├─ CLAUDE.md              Guidance for AI coding agents
├─ CONTRIBUTING.md        Workflow, branches, commits, PRs, definition of done
├─ .editorconfig  .gitignore
└─ README.md
```

## Prerequisites (Windows 11)

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 (LTS) | Any OpenJDK build, e.g. Eclipse Temurin. `JAVA_HOME` must point at it. |
| Maven | via wrapper | The Maven Wrapper (`mvnw.cmd`) is committed in M0, so no global install is required. |
| Node.js | current LTS (22 or newer) | npm is the package manager. |
| PostgreSQL | 16 or newer, installed natively | No Docker required. |
| Git | 2.40+ | Set `core.autocrlf` per [CONTRIBUTING.md](CONTRIBUTING.md#line-endings). |
| Playwright browsers | latest | `npx playwright install` (M6; used for E2E only). |

Docker is **optional**: it is only needed if you choose the Testcontainers profile for integration
tests ([docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md#testing-expectations)).

## Quick start

### 1. Create the databases

Needs a running PostgreSQL 16+ server and its `bin` folder (for `psql`). Passwords come from environment
variables and are never stored in the repo. Creates role `trashbin` and databases `trashbin_db` (dev) and
`trashbin_test_db` (tests); safe to re-run.

```powershell
$env:PGPASSWORD           = '<postgres admin password>'
$env:TRASHBIN_DB_PASSWORD = '<choose a password for the trashbin role>'
.\database\scripts\create-local-databases.ps1 -PgBin '<path to PostgreSQL bin>'
```

Details and migration rules: [database/README.md](database/README.md).

### 2. Create a storage directory outside the repository

```powershell
New-Item -ItemType Directory -Path C:\Users\<you>\TrashBinData -Force
```

The backend refuses to start if the storage root is relative or inside any git working tree.

### 3. Configure

```powershell
Copy-Item backend\.env.example  backend\.env        # then fill in the database password and storage root
Copy-Item frontend\.env.example frontend\.env.local # optional: BACKEND_URL defaults to http://localhost:8080
```

`.env` files are git-ignored. The full variable reference is in
[docs/SYSTEM_ARCHITECTURE.md](docs/SYSTEM_ARCHITECTURE.md#9-configuration-reference). `TRASHBIN_JWT_SECRET`
is required (the generation command is in `backend/.env.example`); the backend refuses to start without
a valid one.

### 4. Run

```powershell
# Terminal 1 — backend (http://localhost:8080); JAVA_HOME must point at JDK 21
cd backend
.\mvnw.cmd spring-boot:run

# Terminal 2 — frontend (http://localhost:3000)
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000/status>. In the `dev` profile Swagger UI is at
<http://localhost:8080/swagger-ui.html>; health is at `/api/v1/health`.

### 5. Test

```powershell
cd backend;  .\mvnw.cmd verify                        # unit + integration tests, ArchUnit, Spotless check
cd frontend; npm run lint; npm run typecheck; npm run format:check; npm test; npm run build
```

After an intentional API change, regenerate the contract snapshot and frontend types:

```powershell
cd backend;  $env:UPDATE_OPENAPI_SNAPSHOT='true'; .\mvnw.cmd verify; Remove-Item Env:UPDATE_OPENAPI_SNAPSHOT
cd frontend; npm run api:types
```

## Documentation map

| Document | Purpose |
|---|---|
| [docs/PROJECT_REQUIREMENTS.md](docs/PROJECT_REQUIREMENTS.md) | Vision, scope, functional and non-functional requirements |
| [docs/SYSTEM_ARCHITECTURE.md](docs/SYSTEM_ARCHITECTURE.md) | Components, flows, module map, configuration reference |
| [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) | ER diagram, table definitions, indexes, migration rules |
| [docs/API_CONTRACT.md](docs/API_CONTRACT.md) | REST endpoints, DTOs, error model |
| [docs/SECURITY_GUIDELINES.md](docs/SECURITY_GUIDELINES.md) | Auth design, authorization rules, threat model |
| [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | Backend and frontend standards, testing expectations |
| [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md) | Milestones M0–M6 with definition of done |
| [docs/DECISIONS/ADR-001-architecture.md](docs/DECISIONS/ADR-001-architecture.md) | Architecture decision record |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branches, commits, PRs, reviews |
| [CLAUDE.md](CLAUDE.md) | Instructions for AI coding agents |

## License

Not yet chosen. Until a license is added, all rights are reserved.
