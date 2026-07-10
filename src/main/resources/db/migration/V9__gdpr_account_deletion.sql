-- src/main/resources/db/migration/V9__gdpr_account_deletion.sql
-- Covers the schema change introduced in Feature #11 (Account deletion & GDPR).
--
-- AccountStatus.java added a DELETED constant for this feature, but the
-- 'users_account_status_check' CHECK constraint that restricts the column to
-- known values was only ever updated by hand on the dev/dump database — no
-- migration ever added 'DELETED' to it. On any fresh deployment (Docker,
-- CI/CD, staging, new dev machine) that only runs the tracked migrations,
-- GdprServiceImpl.deleteAccount() would fail on its very first call: setting
-- accountStatus = DELETED and saving would violate the CHECK constraint and
-- raise a 500 error instead of actually deleting the account.
--
-- Same root cause V3 already fixed once for features #15-22 — schema that
-- only ever existed in the SQL dump, never captured in Flyway.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_account_status_check;
ALTER TABLE users ADD CONSTRAINT users_account_status_check
    CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'DELETED'));

-- Lets admin/cleanup queries find deleted accounts without a full table scan.
CREATE INDEX IF NOT EXISTS idx_users_deleted
    ON users (account_status) WHERE account_status = 'DELETED';
