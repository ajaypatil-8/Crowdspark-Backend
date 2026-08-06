-- BUG FIX: this file originally referenced otp_verification.user_id and
-- otp_verification.otp_type — columns that don't exist on that table (it
-- only has id/email/expiry_time/otp) — and kyc_documents.status, when the
-- real column is kyc_documents.kyc_status. CREATE INDEX on a nonexistent
-- column is a hard Postgres error, and since DDL is transactional here, that
-- error rolled back this entire file's transaction on every attempt — every
-- statement below it, INCLUDING THE VALID ONES, silently never took effect
-- either. On a fresh database (a brand-new `docker compose up`, for
-- instance) Flyway runs migrations strictly in order, so this failure
-- blocks the application from starting at all, not just from getting these
-- indexes. Corrected to index what the code actually queries by
-- (OtpRepository.findByEmail/deleteByEmail, KycDocumentRepository.findByKycStatus).

CREATE INDEX IF NOT EXISTS idx_donations_user_id
    ON donations (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_otp_verification_email
    ON otp_verification (email);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_user_id
    ON kyc_documents (user_id);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_status
    ON kyc_documents (kyc_status);

CREATE INDEX IF NOT EXISTS idx_reward_tiers_project_id
    ON reward_tiers (project_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id, created_at DESC);