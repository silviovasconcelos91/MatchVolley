-- ============================================================
-- V7__player_roles_enum.sql
-- 1. players.role (VARCHAR) → player_roles join table (VolleyPosition enum)
-- 2. player_match_stats.role VARCHAR(30) → VARCHAR(20) constrained to enum values
-- ============================================================

-- -------------------------------------------------------
-- 1. Create player_roles table (replaces players.role)
-- -------------------------------------------------------
CREATE TABLE player_roles
(
    player_id BIGINT      NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    role      VARCHAR(20) NOT NULL,
    CONSTRAINT chk_player_role CHECK (role IN ('R4', 'Central', 'Passeur', 'Pointu', 'Libero'))
);

CREATE INDEX idx_player_roles_player_id ON player_roles (player_id);

-- Migrate existing role values with case-insensitive mapping.
-- Rows whose role cannot be mapped are silently dropped (no valid enum equivalent).
-- Known aliases: 'setter' → Passeur, 'opposite' → Pointu.
INSERT INTO player_roles (player_id, role)
SELECT id,
       CASE lower(trim(role))
           WHEN 'r4'       THEN 'R4'
           WHEN 'central'  THEN 'Central'
           WHEN 'passeur'  THEN 'Passeur'
           WHEN 'setter'   THEN 'Passeur'
           WHEN 'pointu'   THEN 'Pointu'
           WHEN 'opposite' THEN 'Pointu'
           WHEN 'libero'   THEN 'Libero'
       END
FROM players
WHERE lower(trim(role)) IN ('r4', 'central', 'passeur', 'setter', 'pointu', 'opposite', 'libero');

ALTER TABLE players
    DROP COLUMN role;

-- -------------------------------------------------------
-- 2. Normalize player_match_stats.role to enum values
-- -------------------------------------------------------

-- Same alias mapping as above.
UPDATE player_match_stats
SET role = CASE lower(trim(role))
               WHEN 'r4'       THEN 'R4'
               WHEN 'central'  THEN 'Central'
               WHEN 'passeur'  THEN 'Passeur'
               WHEN 'setter'   THEN 'Passeur'
               WHEN 'pointu'   THEN 'Pointu'
               WHEN 'opposite' THEN 'Pointu'
               WHEN 'libero'   THEN 'Libero'
               ELSE role
           END;

ALTER TABLE player_match_stats
    ALTER COLUMN role TYPE VARCHAR(20),
    ADD CONSTRAINT chk_player_match_stat_role CHECK (role IN ('R4', 'Central', 'Passeur', 'Pointu', 'Libero'));
