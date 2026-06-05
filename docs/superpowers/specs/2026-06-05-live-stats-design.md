# Live Stats — Design Spec
**Date:** 2026-06-05  
**Status:** Approved

---

## Contexte

L'application mobile (React Native / Expo) envoie une payload d'événements bruts à la fin d'une saisie de stats en temps réel. Ce flux **remplace** l'ancien `POST /api/v1/match-stats` pour la création de match — la payload live devient la source de vérité. Les stats agrégées sont calculées côté client à partir des événements bruts récupérés via le GET.

---

## Endpoints

```
POST /api/v1/matches/{matchId}/live-stats   → 201 Created
GET  /api/v1/matches/{matchId}/live-stats   → 200 OK
```

Les deux endpoints requièrent une authentification JWT (comportement identique aux endpoints existants).

---

## Architecture

### Flow POST

1. Reçoit `LiveStatsRequest` (payload complète)
2. Vérifie que `matchId` n'existe pas déjà → `409 Conflict` si doublon
3. Crée entité `Match` (champs dérivés depuis payload)
4. Crée `MatchLiveSession` lié au `Match`
5. Crée N `MatchLiveSetEvent` (un par set, events stockés en JSONB)
6. Retourne `ApiResponse<LiveStatsSavedResponse>` — `201 Created`

### Flow GET

1. Cherche `MatchLiveSession` par `matchId` + utilisateur authentifié → `404` si absent
2. Charge les `MatchLiveSetEvent` liés
3. Reconstruit la payload originale
4. Retourne `ApiResponse<LiveStatsResponse>` — `200 OK`

### Nouveaux composants

| Couche | Fichiers |
|---|---|
| Controller | `MatchStatController` — 2 nouveaux endpoints |
| Service | `LiveStatsService` |
| Mapper | `LiveStatsMapper` (MapStruct, `componentModel = "spring"`) |
| Model | `MatchLiveSession`, `MatchLiveSetEvent` |
| Repository | `MatchLiveSessionRepository` |
| DTOs | `dto/live/` — voir section DTOs |

---

## Schéma DB

### Nouvelle table `match_live_sessions`

```sql
id            BIGSERIAL    PRIMARY KEY
match_id      VARCHAR      NOT NULL UNIQUE  -- FK → matches.id
team_name     VARCHAR      NOT NULL
recorded_at   TIMESTAMP    NOT NULL
sets_won_mine INTEGER      NOT NULL
sets_won_opp  INTEGER      NOT NULL
```

### Nouvelle table `match_live_set_events`

```sql
id          BIGSERIAL   PRIMARY KEY
session_id  BIGINT      NOT NULL            -- FK → match_live_sessions.id
set_number  INTEGER     NOT NULL
score_team  INTEGER     NOT NULL
score_opp   INTEGER     NOT NULL
won_by      VARCHAR(4)                      -- 'mine' | 'opp' | NULL
events      JSONB       NOT NULL
```

### Table `matches` — champs dérivés depuis payload (pas de nouvelle colonne)

| Colonne existante | Source dans payload |
|---|---|
| `id` | `matchId` |
| `team_id` | `teamId` |
| `home` | `venue == "home"` |
| `date` | `recordedAt.toLocalDate()` |
| `my_sets` | `setsWon.mine` |
| `opp_sets` | `setsWon.opp` |
| `result` | `setsWon.mine > setsWon.opp` → `WIN`, sinon `LOSS` |
| `opponent_id` | Valeur par défaut (`1L`) |
| `season_id` | Valeur par défaut (`"2025/2026"`) |
| `competition_id` | Valeur par défaut (`1L`) |

`teamName` n'est pas stocké sur `Match` — uniquement dans `match_live_sessions.team_name`.

---

## DTOs

Tous dans le package `dto/live/` :

```
LiveStatsRequest
  ├─ matchId: String
  ├─ teamId: Long
  ├─ teamName: String
  ├─ venue: String              -- "home" | "away"
  ├─ recordedAt: Instant
  ├─ setsWon: SetsWonDto
  └─ sets: List<LiveSetDto>

SetsWonDto
  ├─ mine: Integer
  └─ opp: Integer

LiveSetDto
  ├─ setNumber: Integer
  ├─ scoreTeam: Integer
  ├─ scoreOpp: Integer
  ├─ wonBy: String              -- "mine" | "opp" | null
  └─ events: List<LiveEventDto>

LiveEventDto
  ├─ id: String
  ├─ sequence: Integer
  ├─ ts: Long
  ├─ team: String               -- "mine" | "opp"
  ├─ player: LivePlayerDto
  ├─ action: LiveActionDto
  ├─ trajectory: LiveTrajectoryDto (nullable)
  └─ scoredFor: String          -- "mine" | "opp" | null

LivePlayerDto
  ├─ id: Long (nullable)
  ├─ jersey: Integer
  ├─ name: String
  └─ position: Integer (nullable)

LiveActionDto
  ├─ key: String
  ├─ label: String
  └─ category: String           -- "point" | "fault" | "neutral"

LiveTrajectoryDto
  ├─ from: Integer (nullable)
  └─ to: Integer

LiveStatsSavedResponse
  └─ matchId: String

LiveStatsResponse           -- même shape que LiveStatsRequest (payload reconstruite)
  ├─ matchId: String
  ├─ teamId: Long
  ├─ teamName: String
  ├─ venue: String
  ├─ recordedAt: Instant
  ├─ setsWon: SetsWonDto
  └─ sets: List<LiveSetDto>
```

---

## Gestion des erreurs

| Cas | Code HTTP |
|---|---|
| `matchId` déjà existant (POST) | `409 Conflict` |
| Match non trouvé (GET) | `404 Not Found` |
| Non authentifié | `401 Unauthorized` |

---

## Tests

### `LiveStatsServiceTest` (unitaire — JUnit 5 + AssertJ)

- `should_create_match_and_session_when_payload_is_valid`
- `should_throw_409_when_match_id_already_exists`
- `should_derive_result_WIN_when_sets_won_mine_greater`
- `should_derive_result_LOSS_when_sets_won_opp_greater`
- `should_return_reconstructed_payload_when_match_exists`
- `should_throw_404_when_match_not_found`

### `LiveStatsControllerTest` (`@SpringBootTest`)

- POST payload valide → `201` + `matchId` en réponse
- POST doublon `matchId` → `409`
- GET match existant → `200` + payload complète reconstituée
- GET match inexistant → `404`
- GET/POST sans auth → `401`

---

## Contraintes & notes

- `events` est ordonné par `sequence` (croissant, repart à 1 par set). Le backend s'y fie pour reconstruction.
- `ts` = horloge locale app. Utiliser `recordedAt` pour dater le match.
- Joueurs adverses sans `player.id` — identifiés uniquement par `player.jersey` dans le contexte du match.
- `trajectory.from` est `null` pour ace et service réussi.
- Un joueur peut apparaître à des `position` différentes entre events (rotation).
- Mapping via MapStruct uniquement — pas de mapping manuel.
