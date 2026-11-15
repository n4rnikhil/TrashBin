-- Extensions required by later migrations (case-insensitive email, trigram filename search).
-- Both are "trusted" extensions in PostgreSQL 13+, so the database owner can create them.
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
