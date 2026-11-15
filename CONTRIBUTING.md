# Contributing to Trash Bin

This document is the single reference for how work flows through the repository. Technical standards
live in [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md); security rules in
[docs/SECURITY_GUIDELINES.md](docs/SECURITY_GUIDELINES.md).

## Workflow overview

1. Pick work from the active milestone in [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md).
2. Create a branch from an up-to-date `main`.
3. Make small commits using Conventional Commits.
4. Open a pull request (also for solo work; the PR is the review checkpoint).
5. All checks pass, the self-review checklist is complete, then **squash-merge**.

`main` is always releasable and never receives direct pushes.

## Local development requirements

See [README.md](README.md#prerequisites-windows-11) for tools and versions, and the
[configuration reference](docs/SYSTEM_ARCHITECTURE.md#9-configuration-reference) for environment
variables. Summary:

- JDK 21, Node.js LTS (22+), PostgreSQL 16+ (native), Git. Docker is optional.
- Secrets live in `backend/.env` and `frontend/.env.local`, which are git-ignored. Only
  `*.env.example` files with placeholder values are committed.

### Line endings

The repository normalizes to **LF** (see `.editorconfig`). On Windows set once:

```powershell
git config --global core.autocrlf input
```

Exception: `*.cmd` and `*.bat` files use CRLF.

## Branch naming

```
<type>/<short-kebab-description>
```

| Type | Use for |
|---|---|
| `feat` | New user-visible capability |
| `fix` | Bug fix |
| `refactor` | Behavior-preserving restructuring |
| `perf` | Performance work |
| `test` | Tests only |
| `docs` | Documentation only |
| `build` | Build tooling, dependencies |
| `chore` | Maintenance that fits nowhere else |
| `spike` | Throwaway investigation. Never merged; findings go into an ADR or doc. |

Rules: lowercase, hyphen-separated, at most 50 characters, optionally prefixed with the milestone
(`feat/m1-refresh-token-rotation`). One concern per branch. Delete branches after merging.

## Commit messages

[Conventional Commits 1.0](https://www.conventionalcommits.org/):

```
<type>(<scope>): <imperative summary, max 72 chars, no trailing period>

<body: why this change, wrapped at 72 chars. Optional.>

<footer: BREAKING CHANGE: ..., Closes #123. Optional.>
```

- **Types:** `feat fix refactor perf test docs build ci chore revert`.
- **Scopes** (pick one): `auth user archive upload lifecycle search tag collection rediscover activity
  storage content stats db api web config security deps docs`.
- Subject in the imperative: "add refresh token rotation", not "added" or "adds".
- Explain *why* in the body; the diff already shows *what*.
- One logical change per commit. Do not mix formatting-only changes with behavior changes.
- Database migrations get their own commit (`feat(db): add archived_files table`).
- Never commit secrets, real credentials, or personal data, not even in history. If it happens, rotate
  the secret first and then rewrite history.

Examples:

```
feat(auth): rotate refresh tokens and detect reuse
fix(upload): reject files whose signature disagrees with extension
docs(api): document access ticket TTLs
```

## Pull requests

- Title follows the commit format (it becomes the squash commit).
- Description: what and why, how it was tested, screenshots for UI changes, and links to the docs
  sections touched.
- Keep PRs under roughly 400 changed lines where practical; split otherwise.

### Self-review checklist (copy into the PR description)

```
- [ ] Scope matches the active milestone; no unrelated changes
- [ ] Tests added/updated and green; cross-tenant test for any new owned endpoint
- [ ] Error paths and validation covered
- [ ] No secrets, tokens, filenames or PII in code, logs, fixtures or screenshots
- [ ] docs/ updated (API_CONTRACT, DATABASE_DESIGN, SYSTEM_ARCHITECTURE as applicable)
- [ ] OpenAPI snapshot regenerated if the API changed
- [ ] New dependency? Justification written below, license and maintenance checked
- [ ] Migration is new (never edits an applied one) and reversible by a follow-up migration
```

## Testing expectations

Full rules are in [docs/CODING_STANDARDS.md](docs/CODING_STANDARDS.md#testing-expectations). The
minimum bar for every PR:

- New logic has unit tests. New endpoints have integration tests covering: anonymous, wrong owner
  (expects 404), correct owner, and invalid input.
- Bug fixes include a test that fails without the fix.
- Security-sensitive code (auth, ownership, upload validation, tickets, path handling) is tested at
  branch level, with negative cases written first.
- Tests are deterministic: no sleeps, no real clocks (inject `Clock`), no dependence on test order,
  and no network.

## Definition of done (every change)

A change is done when **all** of these hold:

1. Builds with no compiler warnings (`-Xlint:all -Werror`) and no TypeScript or ESLint errors.
2. Formatters pass (Spotless for Java, Prettier for frontend).
3. Tests are added and passing locally (`mvnw verify`, `npm test`).
4. Ownership, validation and error paths are covered.
5. Documentation and OpenAPI snapshot updated where behavior or contract changed.
6. No secrets or sensitive data introduced; logs reviewed for leakage.
7. No new `TODO` without an issue reference, and no commented-out code.
8. PR self-review checklist completed.

Each milestone adds its own checks; see [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md).

## Dependencies

The default answer to a new dependency is **no**. A PR adding one must state: what it does, why the
platform or an existing dependency can't, its license, maintenance status, and the transitive
footprint. Prefer dev-only dependencies where possible.

## Database changes

- New Flyway migration `V{n}__{snake_case_description}.sql`. Never edit a merged migration.
- Update [docs/DATABASE_DESIGN.md](docs/DATABASE_DESIGN.md) in the same PR.
- Test the migration on an empty database and on the previous schema version.

## Reporting security issues

Do not open a public issue for vulnerabilities. Contact the maintainer privately. Include
reproduction steps; never include real user data.

## Documentation conventions

- Markdown, one H1 per file, sentence-case headings, tables for reference data.
- Diagrams are Mermaid, embedded in the document.
- Each fact has one home (see the source-of-truth map in [CLAUDE.md](CLAUDE.md)); link instead of
  duplicating.
- Never include real credentials, even as examples. Use `<placeholder>` values.
