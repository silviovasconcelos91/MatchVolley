-- Public live broadcast sessions (overlay OBS / Twitch).
CREATE TABLE live_sessions
(
    id                UUID         PRIMARY KEY,
    code              VARCHAR(6)   NOT NULL UNIQUE,
    owner_user_id     UUID         NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    team_id           BIGINT       NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    title             VARCHAR(255) NOT NULL,
    venue             VARCHAR(4)   NOT NULL,
    show_player_names BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(5)   NOT NULL,
    revision          BIGINT       NOT NULL DEFAULT 0,
    snapshot          JSONB,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    ended_at          TIMESTAMP,
    expires_at        TIMESTAMP    NOT NULL,
    match_id          VARCHAR(255),
    CONSTRAINT chk_live_sessions_venue CHECK (venue IN ('home', 'away')),
    CONSTRAINT chk_live_sessions_status CHECK (status IN ('LIVE', 'ENDED'))
);

CREATE INDEX idx_live_sessions_expires_at ON live_sessions (expires_at);
CREATE INDEX idx_live_sessions_owner ON live_sessions (owner_user_id);
