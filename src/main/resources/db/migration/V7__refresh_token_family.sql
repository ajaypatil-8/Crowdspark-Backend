-- src/main/resources/db/migration/V7__refresh_token_family.sql
-- Feature #28 — Refresh Token Rotation Security
--
-- Adds three columns to refresh_tokens:
--
--   family_id        — UUID shared by every token generated from the same
--                      login session.  When a revoked token from a known
--                      family is replayed (theft detection), ALL tokens in
--                      that family are immediately invalidated.
--
--   parent_token_hash — SHA-256 of the previous token in the rotation chain.
--                       Allows reconstructing the full chain for audit/forensics.
--
--   created_at       — timestamp of when this token was issued.
--
-- Existing rows get a generated family_id so they remain functional.
-- parent_token_hash is nullable (the first token in a family has no parent).

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS family_id         VARCHAR(36),
    ADD COLUMN IF NOT EXISTS parent_token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS created_at        TIMESTAMP DEFAULT now();

-- Back-fill: assign each existing row its own unique family so old sessions
-- behave safely (they just won't have cross-token theft detection until the
-- next login that uses the new code path).
UPDATE refresh_tokens
SET family_id = gen_random_uuid()::VARCHAR
WHERE family_id IS NULL;

-- Index for fast family-wide revocation (the hot path on theft detection).
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id
    ON refresh_tokens (family_id);

-- Index on userId for fast per-user revocation (logout / account delete).
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

-- Index on expiry for efficient cleanup jobs.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry
    ON refresh_tokens (expiry_date)
    WHERE revoked = false;
