# Operations

How to run, back up, restore and look after a Trash Bin installation on one Windows machine. All
commands run from the repository root in PowerShell. Scripts read `backend\.env`; real environment
variables take precedence, exactly as for the backend. Pass `-PgBin '<PostgreSQL bin folder>'` (or
set `TRASHBIN_PG_BIN`) when `psql`/`pg_dump` are not on `PATH`.

## 1. Running the hardened build

```powershell
cd backend;  .\mvnw.cmd -DskipTests package
$env:SPRING_PROFILES_ACTIVE = 'prod'
java -jar target\trashbin-backend-0.1.0-SNAPSHOT.jar      # from backend\, so .env is found

cd frontend; npm ci; npm run build; npm run start          # second terminal
```

`prod` turns off Swagger/OpenAPI, writes JSON (ECS) logs, exposes only `/actuator/health`, and marks the
refresh cookie `Secure`. Browsers drop `Secure` cookies on plain `http://localhost`, so set
`TRASHBIN_COOKIE_SECURE=false` only when you run `prod` without HTTPS on your own machine. HSTS is sent
automatically on HTTPS requests. Verified by `ProdProfileIT`.

## 2. Backup

```powershell
# Stop the backend first; the script refuses to run while /api/v1/health answers.
.\scripts\backup.ps1 -Destination D:\TrashBinBackups
```

Creates `trashbin-<timestamp>\` with `database.dump` (pg_dump custom format), `blobs\` (a copy of
`<storage root>\blobs`) and `manifest.json` (checksum of the dump, file count, bytes). Staging files are
not copied: they belong to unfinished uploads. Keep backups on a different disk; copy the folder
elsewhere for an off-machine copy. Back up `backend\.env` separately and privately; it is not in the backup.

## 3. Restore

```powershell
# Stop the backend first.
.\scripts\restore.ps1 -BackupPath D:\TrashBinBackups\trashbin-20261116-101500 -Force
```

Checks the dump against its manifest, then **replaces** the database contents (`DROP OWNED BY` the
app role, `pg_restore`) and the stored files. The previous `blobs` folder is moved aside to
`blobs.pre-restore-<timestamp>`; delete it once you are satisfied. Ends by running the storage check
below and fails loudly if any file is missing or altered. Without `-Force` it only says what it would do.

**Drill (M6, 2026-11-16):** on the disposable test database and a scratch storage root: 6 files
(5 archived, 1 discarded) → backup → database and storage wiped (0 tables, folder deleted) → restore →
6/6 blobs verified by script and 6/6 by the in-app integrity check; counts and statuses unchanged.

## 4. Checking stored files

```powershell
.\scripts\verify-storage.ps1        # read-only; exit code 1 if anything is missing or altered
```

Recomputes the SHA-256 of every blob, for every user, and compares it with the value recorded at
upload. Users can check a single file from its detail page ("Verify integrity").

## 5. Forgotten password (no email in v1)

```powershell
cd backend; .\mvnw.cmd -DskipTests package                 # once, if the jar is missing
.\scripts\reset-password.ps1 -Email sam@example.com
```

Prompts twice for the new password without echoing it and runs the jar once with the `recovery`
profile (no web server): the password is replaced, any lockout is lifted, and every session of that
account ends. Unknown accounts and passwords that break the policy exit with an error and change
nothing. The backend may keep running meanwhile. See [ADR-003](DECISIONS/ADR-003-account-recovery.md).

## 6. Background jobs

| Job | When | What to watch |
|---|---|---|
| Retention purge | daily 03:00 | Logs how many discarded files were purged. `TRASHBIN_RETENTION_DAYS=0` disables it. |
| Storage reconciler | hourly at :15 | Removes stale staging files and orphan blobs; **logs ERROR for rows whose blob is missing** — restore that file from a backup. |
| Text extraction sweep | every minute (first 30 s after start) | Picks up files still `PENDING` (e.g. after a restart). |
| Refresh-token cleanup | daily 03:30 | Deletes expired sessions. |

## 7. Rotating secrets

| Secret | Effect of rotation | How |
|---|---|---|
| `TRASHBIN_JWT_SECRET` | Every access token stops working; users are silently re-issued one from their refresh cookie. | Generate (command in `backend\.env.example`), replace in `.env`, restart. |
| `TRASHBIN_TICKET_SECRET` | Open download/preview links stop working (they live ≤ 5 min). | Same. Must differ from the JWT secret; startup refuses otherwise. |
| `TRASHBIN_DB_PASSWORD` | — | `ALTER ROLE trashbin PASSWORD '…'` as an admin, update `.env`, restart. |

If a secret leaked: rotate first, then clean history. To end every session of one account immediately,
use "Sign out everywhere" (Settings) or the reset in §5.

## 8. Release checks

```powershell
cd backend;  .\mvnw.cmd verify
cd frontend; npm run lint; npm run typecheck; npm run format:check; npm test; npm run build
cd frontend; npm run e2e        # starts both apps if they are not running (see playwright.config.ts)
cd frontend; npm audit --omit=dev
cd backend;  .\mvnw.cmd org.owasp:dependency-check-maven:12.1.0:check   # slow on first run (NVD download)
```

For E2E against fresh data, start the backend yourself with `TRASHBIN_DB_URL` and
`TRASHBIN_STORAGE_ROOT` pointing at an empty database and folder, and
`TRASHBIN_AUTH_RATE_LIMIT_PER_MINUTE=1000` (each full page load refreshes the session, which the default
10/min limit would throttle). Results of the v1 run are in [VERIFICATION.md](VERIFICATION.md).
