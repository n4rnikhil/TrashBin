# v1 verification record

Evidence for the M6 definition of done ([DEVELOPMENT_ROADMAP](DEVELOPMENT_ROADMAP.md#m6--hardening-and-release)).
Recorded 2026-11-16 on Windows 11, Chromium (Playwright 1.65), PostgreSQL 17, JDK 21.

## Must-have requirements

| Req | Evidence |
|---|---|
| FR-A1 register, login, logout, logout-all | `AuthFlowIT`; E2E `journey.spec.ts` (sign-up, sign-out) |
| FR-A2 refresh rotation + reuse detection | `AuthFlowIT` (rotation, grace replay, reuse revokes family); E2E reload keeps session |
| FR-A3 profile, password change revokes sessions | `AuthFlowIT` |
| FR-A5 registration can be disabled | `RegistrationDisabledIT` |
| FR-F1 upload, original bytes unmodified | `FileUploadIT`; `LifecycleIT` integrity verify; E2E download |
| FR-F2 metadata at upload | `FileUploadIT` |
| FR-F3 duplicates (archived + discarded) | `FileUploadIT` |
| FR-F4 server-side type detection, mismatch rejection | `FileUploadIT` |
| FR-F7 size limit and quota | `FileUploadIT`, `UploadCompensationIT` |
| FR-O1 tags incl. merge, AND filter | `TagFlowIT`, `FileSearchIT` |
| FR-O3 ranked full-text incl. extracted text | `FileSearchIT`, `TextExtractionIT`, `SearchPerformanceIT` (p95 within budget) |
| FR-O4 filters, sort, paging | `FileSearchIT` |
| FR-O6 optimistic concurrency | `FileEditIT` (412 / 428) |
| FR-V1 detail + timeline | `LifecycleIT` timeline; E2E journey |
| FR-V2 download via short-lived ticket | `ContentIT`; E2E journey |
| FR-V3 inline preview allowlist only | `ContentIT`; E2E text preview |
| FR-L1 – L3 discard, restore, purge (confirmed) | `LifecycleIT`; E2E journey; `lifecycle-actions.test.tsx` |
| FR-L4 auto-purge + remaining time | `LifecycleIT` (clock-driven, `0` disables); `discarded-list` UI |

Ownership: every owned endpoint has cross-user 404 tests in its `*IT` class.

## M6 checks

| Check | Result |
|---|---|
| Backend `mvnw verify` | unit + 105 integration tests green, ArchUnit + Spotless clean |
| Frontend lint / typecheck / format / unit / build | clean; 71 unit tests |
| Playwright E2E on a **fresh database and storage folder** | 8/8 green: journey, login failure, reload/refresh, sign-out, CSP, keyboard, axe @1280 and @360 |
| axe (WCAG 2.1 A/AA, serious + critical) on login, register, overview, archive, file detail (archived and discarded), collections, tags, rediscover, discarded, settings | 0 violations after fixes: contrast of meta text, success/warning/destructive tokens and sidebar tagline; `dl` structure on the stats card; accessible names containing visible text (logo, account menu); underlined in-text link |
| 360 px layout | no horizontal scrolling on any screen above |
| Keyboard-only | sign in, reach Archive from the sidebar, upload, open and dismiss a dialog with focus returned |
| Page CSP | per-request nonce, `strict-dynamic`, no `unsafe-eval`/`unsafe-inline` scripts; no violations while the app runs |
| `prod` profile | `ProdProfileIT` + jar smoke run: API docs/Swagger 404, actuator `env` 401 (404 even for admins), JSON logs, `Secure` cookie, HSTS on HTTPS only, no secrets in logs |
| Backup → wipe → restore | executed; see [OPERATIONS §3](OPERATIONS.md#3-restore) |
| `npm audit --omit=dev` | 0 vulnerabilities (12 low/moderate in dev-only tooling via the `shadcn` CLI) |
| OWASP dependency-check (backend) | Tomcat, PostgreSQL driver, log4j-api, Jackson, commons-lang3, Hibernate Validator patched by version overrides. **Open:** Spring Framework 6.2.19 / Spring Security 6.5.11 findings — fixes exist only in Spring Boot 4 (see ADR-001 §6 item 1). |
| Recovery command | `AccountRecoveryIT` + jar run (exit 0 on success, 1 on unknown account / weak password, password never logged) |

## Not verified

- PDF preview in Edge and Firefox (only Chromium is automated).
