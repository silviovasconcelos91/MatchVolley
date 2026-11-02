-- Classic match mode removed: only live stats remain.
DROP TABLE IF EXISTS player_set_stats;
DROP TABLE IF EXISTS player_match_stats;
DROP TABLE IF EXISTS set_stats;

ALTER TABLE matches
    DROP COLUMN IF EXISTS opponent_id,
    DROP COLUMN IF EXISTS season_id,
    DROP COLUMN IF EXISTS competition_id,
    DROP COLUMN IF EXISTS points,
    DROP COLUMN IF EXISTS attack_points,
    DROP COLUMN IF EXISTS block_points,
    DROP COLUMN IF EXISTS ace_points,
    DROP COLUMN IF EXISTS attack_errors,
    DROP COLUMN IF EXISTS service_errors,
    DROP COLUMN IF EXISTS receptions,
    DROP COLUMN IF EXISTS faults;
