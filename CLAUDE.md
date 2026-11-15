# CLAUDE.md — Trash Bin

Guidance for AI coding agents working in this repository. Humans: read [CONTRIBUTING.md](CONTRIBUTING.md).

## What this is

Trash Bin: a digital archive + memory web app. Spring Boot 3.x (Java 21) modular monolith, Next.js
(App Router, TypeScript strict) frontend, PostgreSQL + Flyway, local-filesystem blob storage.

**Current status: M3a (tags, favorites, metadata editing) complete; M3b (collections, search,
filters) is next.** Accounts, sessions, uploads, archive list, file details, tags and editing exist. Check `docs/DEVELOPMENT_ROADMAP.md` for the active
milestone and **do not build features outside it**.

## Source-of-truth map

Read the relevant document *before* changing code. If code and docs disagree, stop and ask; do not
silently pick one. Update the doc in the same change when a decision changes.

| Topic | Document |
|---|---|
| Scope, requirements, limits | `docs/PROJECT_REQUIREMENTS.md` |
| Components, flows, env vars | `docs/SYSTEM_ARCHITECTURE.md` |
| Tables, constraints, migrations | `docs/DATABASE_DESIGN.md` |
| Endpoints, DTOs, errors | `docs/API_CONTRACT.md` |
| Auth, authz, uploads, threats | `docs/SECURITY_GUIDELINES.md` |
| Style and testing rules | `docs/CODING_STANDARDS.md` |
| Milestones and definition of done | `docs/DEVELOPMENT_ROADMAP.md` |
| Why things are the way they are | `docs/DECISIONS/` |

## Non-negotiable invariants

1. **Ownership.** Every owned entity is fetched with its owner (`findByIdAndOwnerId`, or owner-scoped
   queries). Never use plain `findById` on owned entities. The owner comes from the security context,
   never from a request body or path. A resource owned by someone else returns **404**, not 403.
2. **UUIDs are identifiers, not authorization.**
3. **Entities never cross the service boundary.** Controllers accept and return DTO records only.
4. **Disk paths come only from a server-generated UUID `storage_key`.** User filenames never touch the
   filesystem. All blob access goes through `BlobStore`.
5. **Never trust the client** for media type, size or hash. Compute them server-side.
6. **No secrets in the repo**, in logs, or in error responses. Config comes from env vars via typed
   `@ConfigurationProperties`.
7. **No swallowed exceptions** and no catch-all handlers, except the single documented last-resort
   handler in the error advice.
8. **Access token lives in memory only** (frontend). Refresh token is an HttpOnly cookie. Never use
   `localStorage`/`sessionStorage` for tokens.
9. **Never serve user content inline** unless the type is in the preview allowlist. SVG and HTML are
   always `attachment`.
10. **No fake features.** If it is not implemented, it is not in the UI or the API.

## Commands

Available after M0 (see README for setup). Run from the repository root in PowerShell.

| Task | Command |
|---|---|
| Backend run | `cd backend; .\mvnw.cmd spring-boot:run` |
| Backend tests (unit + integration + ArchUnit) | `cd backend; .\mvnw.cmd verify` |
| Backend format check / fix | `.\mvnw.cmd spotless:check` / `spotless:apply` |
| Frontend dev | `cd frontend; npm run dev` |
| Frontend checks | `npm run lint; npm run typecheck; npm test` |
| E2E | `cd frontend; npm run e2e` |
| Regenerate API types | `cd frontend; npm run api:types` (reads `backend/openapi/openapi.json`) |

## Working agreements

- **Plan first** for anything touching security, the database schema, or the API contract. Show the
  plan, then implement.
- **Test first for security-sensitive paths** (auth, ownership, upload validation, tickets).
  Cross-tenant tests are mandatory for every new owned endpoint.
- **Small, reviewable changes.** One concern per branch. Branch and commit conventions are in
  CONTRIBUTING.md (Conventional Commits).
- **Migrations are immutable** once merged. Add a new `V{n}__*.sql`; never edit an applied one.
- **API changes** update `docs/API_CONTRACT.md` and the committed OpenAPI snapshot in the same PR.
- **Dependencies:** adding one needs a written justification in the PR. The default answer is no.
- **Do not** add Lombok, MapStruct, Redux, a second HTTP client, or any state library beyond TanStack
  Query without an ADR.
- **Windows environment:** the shell is PowerShell. Use `\` or `/` consistently, quote paths with
  spaces, and never assume Unix tools.
- When you create git commits, keep the repository's commit conventions and any attribution trailer
  your harness requires.

## Where code goes

- Backend: `backend/src/main/java/app/trashbin/<module>/{web,service,domain,repository,dto,mapper}`.
  Modules talk through each other's **service interfaces only**.
- Frontend: `frontend/src/features/<feature>/…`; routes only in `src/app`. Features import each other
  only through their `index.ts`.
- Business rules live in the backend. The frontend may mirror them for UX hints only.

## Definition of done (short form)

Compiles with warnings as errors, formatters clean, tests added and green, ownership and error paths
covered, docs and OpenAPI updated, no secrets, no TODOs without an issue reference. The full list is in
CONTRIBUTING.md and per-milestone lists in `docs/DEVELOPMENT_ROADMAP.md`.
