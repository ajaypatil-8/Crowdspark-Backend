-- src/main/resources/db/migration/V13__project_media_index.sql
-- Feature #33 — Database index & query optimization
--
-- Of the three indexes named in the feature spec (donation.project_id,
-- notification.user_id, project_media.project_id), the first two already
-- existed in this database (idx_donations_project_id,
-- idx_notifications_recipient_id — the notifications FK column is actually
-- named recipient_id, not user_id, but same thing). This is the one that
-- was genuinely missing. Every project detail page load and every
-- explore/feed listing queries project_media by project_id to render
-- thumbnails/gallery/video — that was a full sequential scan on every one
-- of those requests.

CREATE INDEX IF NOT EXISTS idx_project_media_project_id
    ON project_media (project_id);
