CREATE TABLE teams
(
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    city       VARCHAR(100),
    logo_color VARCHAR(20)
);

CREATE TABLE players
(
    id     BIGSERIAL    PRIMARY KEY,
    name   VARCHAR(255) NOT NULL,
    numero INTEGER      NOT NULL,
    age    INTEGER      NOT NULL,
    taille VARCHAR(10)  NOT NULL
);

CREATE TABLE player_roles
(
    player_id BIGINT      NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL,
    CONSTRAINT chk_player_role CHECK (role IN ('R4', 'Central', 'Passeur', 'Pointu', 'Libero'))
);

CREATE INDEX idx_player_roles_player_id ON player_roles (player_id);

CREATE TABLE player_teams
(
    player_id BIGINT NOT NULL REFERENCES players (id),
    team_id   BIGINT NOT NULL REFERENCES teams (id),
    PRIMARY KEY (player_id, team_id)
);

CREATE TABLE matches
(
    id             VARCHAR(255) PRIMARY KEY,
    team_id        BIGINT       NOT NULL,
    opponent_id    BIGINT,
    season_id      VARCHAR(255),
    competition_id BIGINT,
    title          VARCHAR(255),
    home           BOOLEAN,
    date           DATE         NOT NULL,
    result         VARCHAR(10)  NOT NULL,
    my_sets        INTEGER      NOT NULL,
    opp_sets       INTEGER      NOT NULL,
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
    id             BIGSERIAL    PRIMARY KEY,
    match_id       VARCHAR(255) NOT NULL REFERENCES matches (id),
    set_num        INTEGER      NOT NULL,
    my_score       INTEGER      NOT NULL,
    opp_score      INTEGER      NOT NULL,
    timeline       JSONB,
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
    id             BIGSERIAL    PRIMARY KEY,
    match_id       VARCHAR(255) NOT NULL REFERENCES matches (id),
    player_id      BIGINT       NOT NULL,
    number         INTEGER      NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_player_match_stat_role CHECK (role IN ('R4', 'Central', 'Passeur', 'Pointu', 'Libero')),
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
    player_match_stat_id BIGINT     NOT NULL REFERENCES player_match_stats (id),
    set_num              INTEGER    NOT NULL,
    position             VARCHAR(20),
    points               INTEGER,
    attack_points        INTEGER,
    block_points         INTEGER,
    ace_points           INTEGER,
    attack_errors        INTEGER,
    service_errors       INTEGER,
    receptions           INTEGER
);
