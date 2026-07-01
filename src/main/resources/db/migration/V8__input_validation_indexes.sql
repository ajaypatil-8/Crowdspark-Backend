

CREATE INDEX IF NOT EXISTS idx_donations_user_id
    ON donations (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_otp_verification_user_type
    ON otp_verification (user_id, otp_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_user_id
    ON kyc_documents (user_id);

CREATE INDEX IF NOT EXISTS idx_kyc_documents_status
    ON kyc_documents (status);

CREATE INDEX IF NOT EXISTS idx_reward_tiers_project_id
    ON reward_tiers (project_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
    ON audit_logs (entity_type, entity_id, created_at DESC);
