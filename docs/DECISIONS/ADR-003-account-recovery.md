# ADR-003: Admin bootstrap and forgotten-password recovery

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-16 |
| **Resolves** | ADR-001 §6 open item 2 |

## Context

v1 has no email (ADR-001 D3), so there is no self-service password reset. A single-machine install
still needs (a) a first administrator and (b) a way back in for a user who forgot their password,
without adding an HTTP endpoint that could be abused remotely.

## Decision

1. **Bootstrap:** the account that registers with `TRASHBIN_BOOTSTRAP_ADMIN_EMAIL` becomes `ADMIN`
   (implemented in M1).
2. **Recovery is an operator command, not an endpoint.** The backend jar started with the `recovery`
   profile runs `PasswordRecoveryCommand` without a web server: it sets a new password for
   `TRASHBIN_RECOVERY_EMAIL` (policy-checked), clears any lockout, revokes every refresh token of the
   account, logs the user **id** at WARN, and exits. `scripts/reset-password.ps1` prompts for the
   password without echo and passes it through a process-scoped environment variable, so it never
   appears on a command line, in shell history or in logs.

## Consequences

- Recovery requires shell access to the machine and the `.env` (i.e. it is as privileged as the
  operator), which matches the deployment model. No new remote attack surface.
- The operator learns the new password; the user should change it after signing in (Settings).
- Access tokens already issued stay valid for at most 10 minutes (accepted residual risk,
  SECURITY_GUIDELINES §11).
- Covered by `AccountRecoveryIT` (password, lockout, session revocation, unknown account, weak
  password) and a manual run of the jar (exit 0 / 1, no secret in the log).
