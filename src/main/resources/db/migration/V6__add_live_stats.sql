CREATE TABLE match_live_sessions
(
    id            BIGSERIAL    PRIMARY KEY,
    match_id      VARCHAR(255) NOT NULL UNIQUE REFERENCES matches (id) ON DELETE CASCADE,
    team_name     VARCHAR(255) NOT NULL,
    recorded_at   TIMESTAMP    NOT NULL,
    sets_won_mine INTEGER      NOT NULL,
    sets_won_opp  INTEGER      NOT NULL
);

CREATE TABLE match_live_set_events
(
    id          BIGSERIAL    PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES match_live_sessions (id) ON DELETE CASCADE,
    set_number  INTEGER      NOT NULL,
    score_team  INTEGER      NOT NULL,
    score_opp   INTEGER      NOT NULL,
    won_by      VARCHAR(4),
    events      JSONB        NOT NULL,
    CONSTRAINT chk_won_by CHECK (won_by IN ('mine', 'opp'))
);
