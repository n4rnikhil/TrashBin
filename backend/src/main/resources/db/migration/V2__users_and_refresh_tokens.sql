-- Accounts and refresh-token sessions (docs/DATABASE_DESIGN.md 3.1, 3.2).
-- Emails are stored lower-cased by the application; the CHECK makes that an invariant.

CREATE TABLE users (
    id                 uuid         PRIMARY KEY,
    email              varchar(254) NOT NULL,
    display_name       varchar(100) NOT NULL,
    password_hash      varchar(255) NOT NULL,
    role               varchar(20)  NOT NULL,
    status             varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count integer      NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE refresh_tokens (
    id                uuid         PRIMARY KEY,
    user_id           uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id         uuid         NOT NULL,
    token_hash        varchar(64)  NOT NULL,
    issued_at         timestamptz  NOT NULL,
    expires_at        timestamptz  NOT NULL,
    family_expires_at timestamptz  NOT NULL,
    used_at           timestamptz,
    revoked_at        timestamptz,
    replaced_by_id    uuid,
    user_agent        varchar(255),
    ip_address        varchar(45),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
