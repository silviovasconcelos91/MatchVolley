-- ============================================================
-- V1__init.sql  –  Initial schema for VolleyMatch
-- ============================================================

CREATE TABLE players
(
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(255) NOT NULL,
    role   VARCHAR(30)  NOT NULL,
    numero INTEGER      NOT NULL,
    age    INTEGER      NOT NULL,
    taille VARCHAR(10)  NOT NULL
);

CREATE TABLE matches
(
    id             VARCHAR(255) PRIMARY KEY,
    team_id        BIGINT      NOT NULL,
    opponent_id    BIGINT,
    season_id      VARCHAR(255),
    competition_id BIGINT,
    date           DATE        NOT NULL,
    result         VARCHAR(10) NOT NULL,
    my_sets        INTEGER     NOT NULL,
    opp_sets       INTEGER     NOT NULL,
    -- embedded VolleyStats (teamMatchStats)
    points         INTEGER,
    attack_points  INTEGER,
    block_points   INTEGER,
    ace_points     INTEGER,
    attack_errors  INTEGER,
    service_errors INTEGER,
    receptions     INTEGER
);

CREATE TABLE set_stats
(
    id             BIGSERIAL PRIMARY KEY,
    match_id       VARCHAR(255) NOT NULL REFERENCES matches (id),
    set_num        INTEGER      NOT NULL,
    my_score       INTEGER      NOT NULL,
    opp_score      INTEGER      NOT NULL,
    timeline       JSONB,
    -- embedded VolleyStats (teamStats)
    points         INTEGER,
    attack_points  INTEGER,
    block_points   INTEGER,
    ace_points     INTEGER,
    attack_errors  INTEGER,
    service_errors INTEGER,
    receptions     INTEGER
);

CREATE TABLE player_match_stats
(
    id             BIGSERIAL PRIMARY KEY,
    match_id       VARCHAR(255) NOT NULL REFERENCES matches (id),
    player_id      BIGINT       NOT NULL,
    number         INTEGER      NOT NULL,
    role           VARCHAR(30)  NOT NULL,
    -- embedded VolleyStats (matchStats)
    points         INTEGER,
    attack_points  INTEGER,
    block_points   INTEGER,
    ace_points     INTEGER,
    attack_errors  INTEGER,
    service_errors INTEGER,
    receptions     INTEGER
);

CREATE TABLE player_set_stats
(
    id                   BIGSERIAL PRIMARY KEY,
    player_match_stat_id BIGINT  NOT NULL REFERENCES player_match_stats (id),
    set_num              INTEGER NOT NULL,
    -- embedded VolleyStats (stats)
    points               INTEGER,
    attack_points        INTEGER,
    block_points         INTEGER,
    ace_points           INTEGER,
    attack_errors        INTEGER,
    service_errors       INTEGER,
    receptions           INTEGER
);
