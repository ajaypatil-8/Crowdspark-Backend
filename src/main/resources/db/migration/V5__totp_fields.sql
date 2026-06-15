

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS totp_secret  VARCHAR(64),
    ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.totp_secret  IS 'Base32-encoded TOTP shared secret. NULL when 2FA is disabled.';
COMMENT ON COLUMN users.totp_enabled IS 'True after the user has scanned the QR code and confirmed the first code.';
