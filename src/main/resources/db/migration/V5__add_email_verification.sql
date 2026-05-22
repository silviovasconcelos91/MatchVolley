-- Set existing users as verified so they are not locked out
ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
UPDATE app_users SET email_verified = TRUE;
ALTER TABLE app_users ALTER COLUMN email_verified SET NOT NULL;

CREATE TABLE email_verification_tokens
(
    id           UUID      PRIMARY KEY,
    token        UUID      NOT NULL UNIQUE,
    user_id      UUID      NOT NULL UNIQUE REFERENCES app_users (id),
    expires_at   TIMESTAMP NOT NULL,
    last_sent_at TIMESTAMP NOT NULL,
    verified     BOOLEAN   NOT NULL DEFAULT FALSE
);