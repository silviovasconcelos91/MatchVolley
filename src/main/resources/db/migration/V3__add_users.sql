CREATE TABLE app_users
(
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    pseudo     VARCHAR(100) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL    DEFAULT NOW()
);

CREATE TABLE refresh_tokens
(
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token      VARCHAR(255) NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL    DEFAULT false
);
