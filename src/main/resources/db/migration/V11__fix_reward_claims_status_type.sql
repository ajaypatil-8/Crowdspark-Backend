-- src/main/resources/db/migration/V11__fix_reward_claims_status_type.sql
-- Feature #24 (Reward Tier Claiming & Fulfillment) — DB-level bug fix.
--
-- V6__reward_claims.sql created `status` as a native PostgreSQL ENUM type
-- (CREATE TYPE reward_claim_status AS ENUM (...)). The entity maps this field
-- with plain `@Enumerated(EnumType.STRING)`, which sends the value over JDBC
-- as a text/varchar parameter — and Postgres refuses to implicitly cast a
-- text parameter into a custom enum column:
--   ERROR: column "status" is of type reward_claim_status but expression is
--   of type character varying
-- Verified directly: reproducing V6's schema and inserting a claim the same
-- way Hibernate would (a plain string bind parameter) throws exactly that
-- error. That means every single claim creation or status update would fail
-- outright against a database that was actually migrated using the current
-- V6 file — the entire feature would be dead on arrival.
--
-- Every other enum-backed column in this schema (donations.payment_status,
-- etc.) uses plain VARCHAR + a CHECK constraint instead, which is exactly
-- what Hibernate's EnumType.STRING expects, and is confirmed to work. This
-- migration converts reward_claims.status to match that same, already-proven
-- pattern.
--
-- This is a new, forward-only migration rather than an edit to V6 — once a
-- versioned migration has run anywhere, editing it risks a Flyway checksum
-- mismatch on the next boot in every environment that already applied it.
-- The block below is fully defensive: it only touches the column if it's
-- still the native enum type, so it's a safe no-op anywhere the column is
-- already VARCHAR (including via some other path that predates this fix).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'reward_claims'
          AND column_name = 'status'
          AND udt_name = 'reward_claim_status'
    ) THEN
        ALTER TABLE reward_claims ALTER COLUMN status DROP DEFAULT;
        ALTER TABLE reward_claims
            ALTER COLUMN status TYPE VARCHAR(30) USING status::text;
        ALTER TABLE reward_claims ALTER COLUMN status SET DEFAULT 'PENDING';
        ALTER TABLE reward_claims ALTER COLUMN status SET NOT NULL;

        -- The enum type is only dropped once nothing references it anymore.
        DROP TYPE IF EXISTS reward_claim_status;
    END IF;
END $$;

-- Match the CHECK constraint that already protects every other enum-backed
-- column in this schema, in case it isn't there yet.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'reward_claims_status_check'
    ) THEN
        ALTER TABLE reward_claims
            ADD CONSTRAINT reward_claims_status_check
            CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'FULFILLED', 'CANCELLED'));
    END IF;
END $$;
