# Contexte

Le backend MatchVolley (Java / Spring Boot) a été aligné sur le front simplifié : le mode « Match classique » est supprimé, il ne reste que la saisie temps réel (live stats). Adapte le front aux changements de contrat ci-dessous.

Avant de modifier quoi que ce soit, liste les fichiers et usages concernés (appels API, types, client OpenAPI généré, écrans) et attends ma validation.

# 1. Endpoints supprimés côté backend

- `POST /api/v1/match-stats`
- `GET /api/v1/match-stats/{matchId}`

→ Régénère le client OpenAPI depuis `/v3/api-docs` : `saveMatchStat` et `getMatchStat` doivent disparaître. Supprime tout code mort qui les référence encore (types `MatchStatRequest`, `MatchDetailResponse`, `SetStatDto`, `PlayerSetStatDto`, `PlayerStatDto`, `TimelineEntryDto` classique, `MetaDto`…).

# 2. `POST /api/v1/matches/{matchId}/live-stats` : 2 nouveaux champs

## a) `title` (racine, string)

Nom du match saisi en début de match, ex. `"VSL vs Grenoble"`. Il est stocké, puis renvoyé par `GET /teams/{id}/matches`.

## b) `player.poste` (dans chaque event)

Poste du joueur pendant le set :

```ts
type Poste = "R4" | "Central" | "Passeur" | "Pointu" | "Libero" | null;
```

- `null` pour les events adverses (`team: "opp"`).
- Valeurs exactes, sensibles à la casse : toute autre valeur fait renvoyer **400** par le backend.
- Ne pas confondre avec `player.position`, qui reste la zone terrain 1–6 au moment de l'action.
- Le backend prend, pour chaque joueur et chaque set, le premier `poste` non nul trouvé dans ses events. Il sert à :
  - `setStats[].poste` dans l'analyse du match ;
  - `statsByPosition` dans `GET /players/{id}/season-stats`.
- Il faut donc que l'app connaisse le poste de chaque joueur pour chaque set (choix au début du set ou depuis le rôle du joueur). Si ce n'est pas encore possible, envoie `null` : rien ne casse, mais l'analyse par poste reste vide.

Exemple d'event :

```json
{
  "id": "evt_1749131100000_1",
  "sequence": 1,
  "ts": 1749131100000,
  "team": "mine",
  "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 4, "poste": "R4" },
  "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
  "trajectory": { "from": 4, "to": 1 },
  "scoredFor": "mine"
}
```

Payload racine complet : `matchId, teamId, teamName, title, venue, recordedAt, setsWon, sets[]`

Contrat détaillé : `live-stats-api-contract.md` (repo backend).

# 3. `GET /api/v1/teams/{id}/matches` : réponse modifiée

Chaque élément :

```json
{
  "id": "1749131000000",
  "teamId": 42,
  "title": "VSL vs Grenoble",
  "home": true,
  "date": "2026-06-05",
  "result": "won",
  "mySets": 3,
  "oppSets": 1
}
```

- `result` est en **minuscules** : `"won"` ou `"lost"` (avant : `"WON"` / `"LOST"`). Vérifie les comparaisons côté front.
- `opponentId`, `seasonId` et `competitionId` sont **supprimés**.
- `date` vaut `YYYY-MM-DD`, calculé en **UTC** à partir de `recordedAt`. Un match envoyé juste après minuit heure de Paris peut afficher la date de la veille.
- La liste contient désormais les matchs enregistrés via `POST /live-stats`.

# 4. `GET /api/v1/matches/{matchId}/live-stats/analysis`

- `players[].setStats[]` gagne un champ `poste` (même type `Poste` que ci-dessus, ou `null`).
- Renvoie **404** s'il n'existe pas de stats live pour ce match : gère ce cas dans l'écran d'analyse.

Contrat détaillé : `live-stats-analysis-api-doc.md` (repo backend).

# 5. `GET /api/v1/players/{id}/season-stats?teamId=…`

Contrat inchangé (`playerId, matchCount, setCount, totalStats, statsByPosition`). Ces stats sont désormais calculées à partir des events live :

| Champ | Calcul |
|---|---|
| `points` | actions de catégorie `point` |
| `faults` | actions de catégorie `fault` |
| `attackPoints` | `attack_pt` |
| `blockPoints` | `block_pt` |
| `acePoints` | `ace` |
| `attackErrors` | `attack_fault` |
| `serviceErrors` | `serve_fault` |
| `receptions` | `good_recv` + `bad_recv` + `recv_fault` |

`statsByPosition` est regroupé par `poste`. Il reste vide si le front n'envoie pas `poste`. L'écran est désactivé pour l'instant : rien à faire tout de suite, juste à garder en tête pour sa réactivation.

# 6. `GET /api/v1/matches/{matchId}/live-stats`

La réponse contient aussi `title`. Il n'est utile que si le front appelle cet endpoint.

# 7. CORS / web

- En dev, l'API accepte toutes les origines `http://localhost:*` et `http://127.0.0.1:*`, dont `http://localhost:8082`. Les preflight `OPTIONS` et l'en-tête `Authorization` sont autorisés.
- En prod, donne-moi l'URL Vercel exacte : je l'ajouterai dans `CORS_ALLOWED_ORIGINS` sur Railway.

# 8. Points d'attention

- Le token d'accès expire au bout de **2 minutes** (`app.jwt.access-token-expiration=120000`). Vérifie que le front rafraîchit bien le token via `POST /auth:refresh` sur une 401, puis rejoue la requête.
- Les erreurs de l'API remontent désormais avec leur vrai statut (400, 404, 409, 500) au lieu d'un 401 générique. Les réponses suivent `{ data, message, status }`.

# Livrable attendu

1. La liste des fichiers et usages à modifier, pour validation.
2. Après validation : les modifications, le typage TypeScript à jour, le build et les tests au vert.
3. Comment le `poste` est choisi ou déduit par set dans l'UI live.
