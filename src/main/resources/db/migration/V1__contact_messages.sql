CREATE TABLE IF NOT EXISTS contact_messages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL,
    topic VARCHAR(80) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL,
    replied_at TIMESTAMP NULL,
    reply_subject VARCHAR(180) NULL,
    reply_message TEXT NULL,
    replied_by_id BIGINT NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_contact_messages_replied_by
        FOREIGN KEY (replied_by_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_contact_messages_status ON contact_messages(status);
CREATE INDEX IF NOT EXISTS idx_contact_messages_created_at ON contact_messages(created_at);
CREATE INDEX IF NOT EXISTS idx_contact_messages_email ON contact_messages(email);