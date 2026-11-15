# Coding standards

| | |
|---|---|
| **Status** | Approved baseline v1.0 (2026-11-15) |
| **Scope** | Part A: all code · Part B: backend · Part C: frontend · Part D: SQL and migrations · Part E: tests |
| **Enforcement** | Compiler/linter/formatter rules and ArchUnit tests where possible; review otherwise. |
| **Related** | [CONTRIBUTING](../CONTRIBUTING.md) · [SECURITY_GUIDELINES](SECURITY_GUIDELINES.md) |

---

## Part A — Shared standards

1. **Clarity over cleverness.** Prefer boring, explicit code. Names carry intent; comments explain *why*.
2. **Small units.** Classes under ~300 lines, methods under ~40, React components under ~200. Split when a
   unit does more than one thing, not to hit a number.
3. **One home per business rule.** No duplicated validation or policy logic; the backend is the source of truth.
4. **No dead code, no commented-out code, no `TODO` without an issue reference.**
5. **Never swallow exceptions.** Handle meaningfully, or wrap with the cause and rethrow. No empty `catch`.
6. **No secrets, no real personal data** in code, tests, fixtures, logs or docs. Use obviously fake values.
7. **Dependencies:** default no; see [CONTRIBUTING](../CONTRIBUTING.md#dependencies).
8. **Formatting is automated** (`.editorconfig`, Spotless, Prettier). Do not hand-format or argue about style in review.
9. **Document complex decisions** in the code (why), in docs, or in an ADR when it is architectural.
10. **English** identifiers and messages; American spelling.

---

## Part B — Backend standards

*Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL.*

### B.1 Language and style

- Java 21. Use **records** for DTOs and value objects, **sealed interfaces** and **pattern matching for
  `switch`** where they model closed sets (e.g., upload outcomes), text blocks for multi-line SQL/JSON in tests.
- `var` only when the type is obvious from the right-hand side.
- No wildcard imports. No Lombok. Explicit constructors; **constructor injection only** (no field injection,
  no `@Autowired` on fields); single-constructor beans need no annotation.
- Formatting: google-java-format (AOSP style, 4-space indent, 100 columns) enforced by **Spotless**; the build
  fails on violations.
- Compiler: `-Xlint:all -Werror`. Warnings are errors.
- `Optional` only as a return type; never as a parameter or field. Collections are never `null`; return empty.
- Prefer immutability: `final` fields, unmodifiable collections (`List.copyOf`).

### B.2 Naming

| Element | Convention | Example |
|---|---|---|
| Package | lowercase, by feature | `app.trashbin.archive.upload` |
| Controller | `XxxController` | `FileController` |
| Service | `XxxService` (interface only when a second implementation or a module boundary needs it) | `TagService` |
| Repository | `XxxRepository` | `ArchivedFileRepository` |
| Entity | domain noun, no suffix | `ArchivedFile` |
| DTO | `XxxRequest` / `XxxResponse` / `XxxSummary` (records) | `UpdateFileRequest` |
| Mapper | `XxxMapper` (explicit class, no framework) | `FileMapper` |
| Exception | `XxxException`, extends `TrashBinException` | `DuplicateFileException` |
| Config properties | `XxxProperties` record, `@ConfigurationProperties` + `@Validated` | `StorageProperties` |
| Tests | `XxxTest` (unit), `XxxIT` (integration) | `UploadServiceTest` |

### B.3 Layering and modules

- Dependencies flow `web → service → repository → domain`. Rules in
  [SYSTEM_ARCHITECTURE §2.2](SYSTEM_ARCHITECTURE.md#22-layer-rules), enforced by ArchUnit.
- Controllers: map HTTP ⇄ DTO and delegate. No business logic, no `@Transactional`, no entity types.
- Services own transactions, ownership checks, state transitions and orchestration.
- A module never uses another module's repository or entities. It calls that module's **service**.
- Entities never leave their module's service layer; mappers convert to DTOs inside the service.

### B.4 Persistence

- `spring.jpa.open-in-view=false`. `ddl-auto=validate`. Schema changes only via Flyway.
- Associations are `LAZY`; avoid bidirectional associations unless needed. Fetch with `@EntityGraph`/
  fetch joins deliberately; watch for N+1 (integration tests assert query counts on list endpoints).
- Entities: protected no-arg constructor, behavior-bearing methods instead of public setters for
  invariants, `equals`/`hashCode` on the identifier only. No `@Data`.
- **Owned entities are accessed only via owner-scoped repository methods** (`findByIdAndOwnerId`).
  Plain `findById` on owned entities is forbidden (ArchUnit).
- `@Transactional` on service methods; `readOnly = true` for queries. Do not hold a transaction across
  slow IO. Follow the storage consistency protocol in
  [SYSTEM_ARCHITECTURE §5](SYSTEM_ARCHITECTURE.md#5-file-storage).
- Native SQL only for full-text search and bulk operations; always with bound parameters.
- Optimistic locking via `@Version`; map `ObjectOptimisticLockingFailureException` to `VERSION_CONFLICT`.

### B.5 Validation and errors

- Bean Validation annotations on request records, plus service-level invariant checks. Never rely on the
  client.
- One abstract `TrashBinException` (carries `ErrorCode`, HTTP status, safe message) with specific subclasses.
  One `@RestControllerAdvice` maps **specific** types to `ProblemDetail`. The single permitted broad
  handler is the last-resort `Throwable` mapper: it logs the stack with the `traceId` and returns a generic
  `INTERNAL_ERROR`.
- Never expose stack traces, SQL, paths or entity internals. See [API_CONTRACT §4](API_CONTRACT.md#4-error-model).
- Do not use exceptions for expected control flow in hot paths; do use them for contract violations.

### B.6 Logging

- SLF4J with parameterized messages: `log.info("Discarded file {}", fileId)`. No string concatenation.
- MDC provides `traceId` and `userId`. Log ids, not names. Never log secrets, tokens, filenames or content.
  See [SECURITY_GUIDELINES §10](SECURITY_GUIDELINES.md#10-logging-and-monitoring).
- Levels: `ERROR` unexpected failures needing attention; `WARN` rejected/suspicious but handled
  (reuse detection, reconciler findings); `INFO` lifecycle and business events; `DEBUG` diagnostics (off by default).

### B.7 Configuration and time

- All configuration through typed `@ConfigurationProperties` records, validated at startup. No scattered
  `@Value`, no hardcoded paths, limits or secrets. New settings are added to the
  [configuration reference](SYSTEM_ARCHITECTURE.md#9-configuration-reference) in the same PR.
- Inject `java.time.Clock`; never call `Instant.now()` directly in logic. All times are UTC.

### B.8 Security-specific rules

- Authentication principal is obtained via an injected `AuthenticatedUser` abstraction, never parsed from
  request data.
- Any file path comes from `BlobStore` and a UUID `storage_key`. Never concatenate user input into a `Path`.
- Never log or return `storage_key`, `password_hash`, token hashes, or ticket material.
- Use `SecureRandom` for tokens; compare secrets with constant-time comparison.

### B.9 Documentation

- Javadoc on public service methods and any non-obvious class. Document invariants, thread-safety and
  failure modes. Architectural rationale goes to docs/ADRs.

---

## Part C — Frontend standards

*Next.js App Router, React, strict TypeScript, Tailwind, shadcn/ui.*

### C.1 TypeScript

- `tsconfig`: `"strict": true`, plus `noUncheckedIndexedAccess`, `noImplicitOverride`,
  `noFallthroughCasesInSwitch`, `verbatimModuleSyntax`, `forceConsistentCasingInFileNames`.
- **No `any`** (ESLint `no-explicit-any` as error). Use `unknown` and narrow or parse with **zod** at
  boundaries (API responses are typed from the generated OpenAPI types; forms use zod schemas).
- No non-null assertions (`!`) and no `@ts-ignore`; `@ts-expect-error` only with a reason comment.
- Prefer `type` aliases for unions and props; `interface` only when extension is needed.
- Import alias `@/` → `src/`. No deep imports into another feature (see C.3).

### C.2 Naming and files

| Element | Convention | Example |
|---|---|---|
| Files and folders | `kebab-case` | `file-card.tsx`, `use-upload-queue.ts` |
| Components | `PascalCase`, one per file, named export | `FileCard` |
| Hooks | `useXxx` | `useFileSearch` |
| Constants | `SCREAMING_SNAKE_CASE` only for true constants | `MAX_TAGS_PER_FILE` |
| Types | `PascalCase`, no `I` prefix | `FileSummary` |
| Tests | colocated `*.test.ts(x)` | `file-card.test.tsx` |

Default exports only where Next.js requires them (`page.tsx`, `layout.tsx`, `error.tsx`, `loading.tsx`).

### C.3 Architecture

- **Feature-based**: `src/features/<feature>/{api,hooks,components,schemas}` with a public `index.ts`.
  Features import each other **only** through `index.ts`. `app/` contains routes only: compose feature
  components, no business logic.
- Shared UI primitives in `components/ui` (shadcn, owned code). Cross-feature utilities in `lib/`.
- **Server state only in TanStack Query.** Query keys come from per-feature key factories. UI-only state
  stays local (`useState`/`useReducer`); do not add a global state library without an ADR.
- Filters and pagination live in the **URL** (`useSearchParams`) so views are shareable and back/forward works.
- **No business rules in components.** Components render and dispatch; rules (limits, state transitions,
  duplicate policy) are backend concerns. Client checks (file size, tag count) are UX hints that mirror,
  never replace, the server.
- Server components for public pages; `"use client"` as low in the tree as practical (the authenticated shell
  is client-rendered by design, see [SYSTEM_ARCHITECTURE §3.1](SYSTEM_ARCHITECTURE.md#31-rendering-model)).

### C.4 API access and errors

- All HTTP goes through `lib/api` (a thin `fetch` wrapper) which: attaches the bearer token, performs the
  single-flight refresh-and-retry on `401`, parses problem+json into a typed `ApiError`
  (`code`, `status`, `traceId`, `fieldErrors`), and sends `X-Requested-With` on cookie endpoints.
- Never call `fetch` directly from components. Never store tokens outside the in-memory auth store.
- Map `ApiError.code` to user-facing messages in one place. Show the `traceId` in unexpected-error toasts.
- Each route segment has `error.tsx`; unexpected errors never render raw messages from the server.
- Downloads and previews use access tickets (see [API_CONTRACT §3.3](API_CONTRACT.md#33-content--get-contentticket)).

### C.5 UI, styling and accessibility

- Tailwind utility classes with the `cn()` helper; design tokens via CSS variables (shadcn theme). No
  inline styles except dynamic values. No arbitrary values when a token exists.
- shadcn components are copied into `components/ui` and kept close to upstream; wrap rather than fork.
- Icons from **Lucide React** only, imported individually.
- **WCAG 2.1 AA:** semantic HTML, labeled controls, visible focus, keyboard operability, sufficient contrast,
  `aria-live` for async status (uploads), reduced-motion respected. `eslint-plugin-jsx-a11y` rules (bundled
  with `eslint-config-next`) are errors.
- Responsive from 360 px. Handle loading, empty, error and success states for every data view.
- **No `dangerouslySetInnerHTML`.** User-supplied text is rendered as text.

### C.6 Forms

`react-hook-form` + `zod` resolvers. Server field errors map back to form fields from `fieldErrors`.
Disable submit while pending; never allow double submission of uploads or destructive actions.

### C.7 Tooling

- ESLint (`next/core-web-vitals`, `typescript-eslint` strict) with `--max-warnings 0`; Prettier (with the
  Tailwind class-sorting plugin); both run in `npm run lint` and `npm run format:check`.
- Scripts required in `package.json`: `dev`, `build`, `start`, `lint`, `typecheck`, `format:check`,
  `test`, `e2e`, `api:types`.
- Environment: only `BACKEND_URL` (server-side). Never put secrets in `NEXT_PUBLIC_*`.

---

## Part D — SQL and migrations

- Flyway only; rules in [DATABASE_DESIGN §8](DATABASE_DESIGN.md#8-migration-rules).
- Lowercase `snake_case`; explicit constraint and index names (`uq_tags_owner_name`, `ix_files_owner_status_archived`).
- Every table has a primary key; every owned table has `owner_id`; add `CHECK` constraints for enumerations
  and limits so the database defends itself.
- No `SELECT *` in application SQL. Parameterize everything.

---

## Testing expectations

### E.1 Principles

- Tests are **deterministic**: no `sleep`, no real clock (`Clock` is injected), no ordering dependence, no
  network, no shared mutable state between tests.
- Test **behavior**, not implementation. Name tests for the scenario and expectation
  (`discard_rejectsFileOwnedByAnotherUser`).
- Every bug fix starts with a failing test.
- **Negative paths first** for security-sensitive code.
- Fixtures contain obviously fake data; no real files or personal data.

### E.2 Backend

| Layer | Tools | What must be tested |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Service rules, state transitions, validation pipeline, token rotation, ticket signing/expiry, extraction caps, mappers |
| Persistence | Spring Boot Test + real PostgreSQL (`trashbin_test_db`) | Migrations on empty DB, constraints, composite FKs, FTS queries, owner scoping, query counts |
| Web/security | `@SpringBootTest` + MockMvc | Per endpoint: anonymous → 401; wrong owner → 404; right owner; invalid input → 400 problem+json; role-protected → 403 |
| Upload/storage | temp directories | Traversal-style filenames, signature mismatch, oversize stream, duplicate, quota, DB-failure compensation, reconciler |
| Architecture | ArchUnit | Layering, module boundaries, no plain `findById` on owned entities, no entities in controllers |
| Contract | OpenAPI snapshot test | Generated spec equals the committed `openapi.json` |

- **Database for tests:** a dedicated local `trashbin_test_db` database migrated by Flyway. A Testcontainers
  profile (`TRASHBIN_TEST_USE_TESTCONTAINERS=true`) is optional for machines with Docker. Never H2.
- **Coverage gates (JaCoCo, enforced in `verify`):** ≥ 80% line coverage for `service` packages; ≥ 90%
  branch coverage for `auth` and ownership/upload-validation classes; no gate on DTOs/config.
- Integration tests are named `*IT` and run in `verify`; unit tests run in `test`.

### E.3 Frontend

| Layer | Tools | What must be tested |
|---|---|---|
| Unit/component | Vitest, React Testing Library, MSW | Forms and validation, auth single-flight refresh, filter ⇄ URL sync, `ApiError` mapping, upload queue behavior, accessibility roles/labels |
| E2E | Playwright | Journey: register → upload → search → preview → download → discard → restore → purge; plus login failure and session expiry/refresh; runs against a real backend and isolated test DB/storage |

- MSW handlers are generated from/validated against the OpenAPI types so mocks cannot drift.
- Coverage gate: ≥ 80% lines for `features/*/hooks` and `lib/api`; components are covered by behavior tests,
  not a numeric target.
- Playwright runs headless in `npm run e2e`; traces are kept on failure only.

### E.4 Gates

A change cannot merge unless: Java and TS build cleanly, formatters pass, all relevant tests pass, and
coverage gates hold. Milestone-level gates are in [DEVELOPMENT_ROADMAP](DEVELOPMENT_ROADMAP.md).
