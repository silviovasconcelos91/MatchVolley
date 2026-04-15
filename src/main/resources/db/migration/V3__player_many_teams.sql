CREATE TABLE player_teams (
    player_id BIGINT NOT NULL REFERENCES players(id),
    team_id   BIGINT NOT NULL REFERENCES teams(id),
    PRIMARY KEY (player_id, team_id)
);

INSERT INTO player_teams (player_id, team_id)
SELECT id, team_id FROM players WHERE team_id IS NOT NULL;

ALTER TABLE players DROP COLUMN team_id;