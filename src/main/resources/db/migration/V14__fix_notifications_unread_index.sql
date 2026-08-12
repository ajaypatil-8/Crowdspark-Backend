-- src/main/resources/db/migration/V14__fix_notifications_unread_index.sql
-- Performance bug found during deployment audit, not a deployment blocker
-- on its own, but real and worth fixing now that we're touching this area.
--
-- notifications has two boolean columns: `read` and `is_read`. The
-- Notification entity's field is named `read` in Java but is explicitly
-- mapped with @Column(name = "is_read") -- so `is_read` is the column
-- Hibernate actually reads/writes, and the standalone `read` column in the
-- database is an unmapped leftover that nothing in the app touches.
--
-- The existing composite index (idx_notifications_read, on
-- (recipient_id, read)) was clearly built to speed up "unread notifications
-- for this user," but it indexes the orphaned `read` column instead of
-- `is_read`. NotificationRepository.countByRecipient_IdAndReadFalse(...) --
-- the exact per-user unread-count query used on every notification bell
-- load -- filters on `is_read` and can never use that index. It falls back
-- to the single-column idx_notifications_is_read (no recipient_id), which
-- means Postgres has to check every unread row across ALL users rather than
-- narrowing to one user first. This replaces the composite index with one
-- on the column the query actually filters by, and drops the one that was
-- never usable for its intended purpose.
--
-- The orphaned `read` column itself is left in place -- dropping columns is
-- outside the scope of this pass and the column being unmapped costs
-- nothing at runtime.

DROP INDEX IF EXISTS idx_notifications_read;

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_is_read
    ON notifications (recipient_id, is_read);
