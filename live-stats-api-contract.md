# Live Stats — Contrat API

Ce document décrit la payload envoyée par l'application mobile (React Native / Expo)
au backend Kotlin lors de la fin d'une saisie de stats en temps réel.

---

## Endpoint suggéré

```
POST /api/matches/{matchId}/live-stats
Content-Type: application/json
```

---

## Structure complète

```json
{
  "matchId": "match-20260605-001",
  "teamId": 42,
  "teamName": "Les Lynx",
  "venue": "home",
  "recordedAt": "2026-06-05T14:45:00.000Z",
  "setsWon": { "mine": 2, "opp": 1 },
  "sets": [
    {
      "setNumber": 1,
      "scoreTeam": 25,
      "scoreOpp": 21,
      "wonBy": "mine",
      "events": [
        {
          "id": "ev-001",
          "sequence": 1,
          "ts": 1749131100000,
          "team": "mine",
          "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 4 },
          "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
          "trajectory": { "from": 4, "to": 1 },
          "scoredFor": "mine"
        }
      ]
    }
  ]
}
```

---

## Champs racine

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `matchId` | `string` | oui | Identifiant unique du match, généré côté app |
| `teamId` | `number` | oui | ID de l'équipe qui saisit les stats |
| `teamName` | `string` | oui | Nom de l'équipe |
| `venue` | `"home"` \| `"away"` | oui | Domicile ou extérieur |
| `recordedAt` | `string` (ISO 8601) | oui | Date/heure d'envoi, ex. `"2026-06-05T14:45:00.000Z"` |
| `setsWon` | `{ mine: number, opp: number }` | oui | Nombre de sets remportés par chaque équipe sur l'ensemble du match |
| `sets` | `Set[]` | oui | Timeline organisée par set (peut contenir un seul set si match en cours) |

---

## Objet `Set`

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `setNumber` | `number` | oui | Numéro du set (1, 2, 3, 4, 5) |
| `scoreTeam` | `number` | oui | Points marqués par `"mine"` dans ce set (dérivé des events) |
| `scoreOpp` | `number` | oui | Points marqués par `"opp"` dans ce set (dérivé des events) |
| `wonBy` | `"mine"` \| `"opp"` \| `null` | oui | Équipe qui a gagné le set. `null` si set encore en cours ou scores à égalité |
| `events` | `Event[]` | oui | Événements de ce set, ordonnés par `sequence` (commence à 1 pour chaque set) |

---

## Objet `Event`

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `id` | `string` | oui | UUID unique de l'événement |
| `sequence` | `number` | oui | Ordre dans la timeline du set (commence à 1 à chaque nouveau set) |
| `ts` | `number` | oui | Timestamp UNIX en millisecondes |
| `team` | `"mine"` \| `"opp"` | oui | Équipe qui réalise l'action (`mine` = l'équipe qui saisit, `opp` = adversaire) |
| `player` | `Player` | oui | Joueur qui réalise l'action |
| `action` | `Action` | oui | Détail de l'action |
| `trajectory` | `Trajectory` \| `null` | oui | Trajectoire de la balle — `null` si non applicable |
| `scoredFor` | `"mine"` \| `"opp"` \| `null` | oui | Qui marque le point grâce à cette action |

### Règle `scoredFor`

```
action.category = "point"   → scoredFor = team   (l'équipe qui fait l'action marque)
action.category = "fault"   → scoredFor = adversaire de team (l'autre équipe marque)
action.category = "neutral" → scoredFor = null   (aucun point)
```

---

## Objet `Player`

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `id` | `number` \| `null` | oui | ID interne du joueur. `null` si équipe adverse (pas de roster adverse saisi) |
| `jersey` | `number` | oui | Numéro de maillot (1–99) |
| `name` | `string` | oui | Nom complet si `team = "mine"`, `"Adv #N"` si `team = "opp"` |
| `position` | `number` \| `null` | oui | Zone terrain (1–6) du joueur au moment de l'action. `null` si non déterminable |

---

## Objet `Action`

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `key` | `string` | oui | Clé technique (voir catalogue ci-dessous) |
| `label` | `string` | oui | Label lisible en français |
| `category` | `"point"` \| `"fault"` \| `"neutral"` | oui | Nature de l'action |

---

## Objet `Trajectory`

Présent uniquement si la balle a une zone d'arrivée renseignée.

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `from` | `number` \| `null` | oui | Zone de départ (1–6). `null` pour ace et service réussi (zone départ non saisie) |
| `to` | `number` | oui | Zone d'arrivée (1–6) |

> `trajectory` est `null` (objet absent) pour les actions sans zone : contre, fautes, réceptions, défenses.

---

## Catalogue des actions

13 actions possibles, réparties en 3 catégories.

### Points remportés (`category: "point"`)

| `key` | Label | `trajectory` |
|---|---|---|
| `attack_pt` | Attaque | `{ from: zone_départ, to: zone_arrivée }` |
| `ace` | Ace | `{ from: null, to: zone_arrivée }` |
| `block_pt` | Contre | `null` |
| `relance_pt` | Relance | `{ from: null, to: zone_arrivée }` |

### Fautes (`category: "fault"`) — point pour l'adversaire

| `key` | Label | `trajectory` |
|---|---|---|
| `attack_fault` | Faute attaque (out ou filet) | `null` |
| `serve_fault` | Faute service (out ou filet) | `null` |
| `recv_fault` | Réception (faute) | `null` |
| `fault` | Faute | `null` |

### Sans incidence sur le score (`category: "neutral"`)

| `key` | Label | `trajectory` |
|---|---|---|
| `good_recv` | Bonne réception | `null` |
| `bad_recv` | Mauvaise récept. | `null` |
| `block_touch` | Contre touché | `null` |
| `serve_in` | Service réussi | `{ from: null, to: zone_arrivée }` |
| `attack_no_pt` | Attaque (sans pt) | `{ from: zone_départ, to: zone_arrivée }` |

---

## Zones terrain (1–6)

Numérotation standard FIVB. Vue du coach, filet au centre :

```
                  ┌─────┬─────┬─────┐
  ADVERSAIRE      │  4  │  3  │  2  │  (avant — proche du filet)
                  ├─────┼─────┼─────┤
                  │  5  │  6  │  1  │  (arrière)
  ════════════════╪═════╪═════╪═════╡  ← FILET
  MON ÉQUIPE      │  4  │  3  │  2  │  (avant — proche du filet)
                  ├─────┼─────┼─────┤
                  │  5  │  6  │  1  │  (arrière)
                  └─────┴─────┴─────┘
```

Zone de départ (`trajectory.from`) = côté de l'équipe qui frappe.
Zone d'arrivée (`trajectory.to`) = côté adverse où la balle retombe.

---

## Exemple complet (3 sets, toutes les actions)

```json
{
  "matchId": "match-20260605-001",
  "teamId": 42,
  "teamName": "Les Lynx",
  "venue": "home",
  "recordedAt": "2026-06-05T14:45:00.000Z",
  "setsWon": { "mine": 2, "opp": 1 },
  "sets": [
    {
      "setNumber": 1,
      "scoreTeam": 4,
      "scoreOpp": 2,
      "wonBy": "mine",
      "events": [
        {
          "id": "ev-001", "sequence": 1, "ts": 1749131100000,
          "team": "mine",
          "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 4 },
          "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
          "trajectory": { "from": 4, "to": 1 },
          "scoredFor": "mine"
        },
        {
          "id": "ev-002", "sequence": 2, "ts": 1749131108000,
          "team": "mine",
          "player": { "id": 5, "jersey": 12, "name": "Martin", "position": 1 },
          "action": { "key": "ace", "label": "Ace", "category": "point" },
          "trajectory": { "from": null, "to": 5 },
          "scoredFor": "mine"
        },
        {
          "id": "ev-003", "sequence": 3, "ts": 1749131115000,
          "team": "mine",
          "player": { "id": 8, "jersey": 4, "name": "Bernard", "position": 3 },
          "action": { "key": "block_pt", "label": "Contre", "category": "point" },
          "trajectory": null,
          "scoredFor": "mine"
        },
        {
          "id": "ev-004", "sequence": 4, "ts": 1749131122000,
          "team": "mine",
          "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 2 },
          "action": { "key": "relance_pt", "label": "Relance", "category": "point" },
          "trajectory": { "from": null, "to": 6 },
          "scoredFor": "mine"
        },
        {
          "id": "ev-005", "sequence": 5, "ts": 1749131130000,
          "team": "opp",
          "player": { "id": null, "jersey": 5, "name": "Adv #5", "position": 2 },
          "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
          "trajectory": { "from": 2, "to": 6 },
          "scoredFor": "opp"
        },
        {
          "id": "ev-006", "sequence": 6, "ts": 1749131138000,
          "team": "opp",
          "player": { "id": null, "jersey": 7, "name": "Adv #7", "position": 4 },
          "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
          "trajectory": { "from": 4, "to": 1 },
          "scoredFor": "opp"
        }
      ]
    },
    {
      "setNumber": 2,
      "scoreTeam": 2,
      "scoreOpp": 3,
      "wonBy": "opp",
      "events": [
        {
          "id": "ev-007", "sequence": 1, "ts": 1749131200000,
          "team": "mine",
          "player": { "id": 5, "jersey": 12, "name": "Martin", "position": 1 },
          "action": { "key": "attack_fault", "label": "Faute attaque", "category": "fault" },
          "trajectory": null,
          "scoredFor": "opp"
        },
        {
          "id": "ev-008", "sequence": 2, "ts": 1749131208000,
          "team": "mine",
          "player": { "id": 11, "jersey": 9, "name": "Leclerc", "position": 1 },
          "action": { "key": "serve_fault", "label": "Faute service", "category": "fault" },
          "trajectory": null,
          "scoredFor": "opp"
        },
        {
          "id": "ev-009", "sequence": 3, "ts": 1749131215000,
          "team": "mine",
          "player": { "id": 2, "jersey": 2, "name": "Petit", "position": 5 },
          "action": { "key": "recv_fault", "label": "Réception", "category": "fault" },
          "trajectory": null,
          "scoredFor": "opp"
        },
        {
          "id": "ev-010", "sequence": 4, "ts": 1749131222000,
          "team": "opp",
          "player": { "id": null, "jersey": 3, "name": "Adv #3", "position": 3 },
          "action": { "key": "fault", "label": "Faute", "category": "fault" },
          "trajectory": null,
          "scoredFor": "mine"
        },
        {
          "id": "ev-011", "sequence": 5, "ts": 1749131230000,
          "team": "mine",
          "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 4 },
          "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
          "trajectory": { "from": 4, "to": 5 },
          "scoredFor": "mine"
        }
      ]
    },
    {
      "setNumber": 3,
      "scoreTeam": 3,
      "scoreOpp": 1,
      "wonBy": "mine",
      "events": [
        {
          "id": "ev-012", "sequence": 1, "ts": 1749131300000,
          "team": "mine",
          "player": { "id": 2, "jersey": 2, "name": "Petit", "position": 6 },
          "action": { "key": "good_recv", "label": "Bonne réception", "category": "neutral" },
          "trajectory": null,
          "scoredFor": null
        },
        {
          "id": "ev-013", "sequence": 2, "ts": 1749131307000,
          "team": "mine",
          "player": { "id": 2, "jersey": 2, "name": "Petit", "position": 5 },
          "action": { "key": "bad_recv", "label": "Mauvaise récept.", "category": "neutral" },
          "trajectory": null,
          "scoredFor": null
        },
        {
          "id": "ev-014", "sequence": 3, "ts": 1749131314000,
          "team": "opp",
          "player": { "id": null, "jersey": 3, "name": "Adv #3", "position": 2 },
          "action": { "key": "block_touch", "label": "Contre touché", "category": "neutral" },
          "trajectory": null,
          "scoredFor": null
        },
        {
          "id": "ev-015", "sequence": 4, "ts": 1749131321000,
          "team": "mine",
          "player": { "id": 11, "jersey": 9, "name": "Leclerc", "position": 1 },
          "action": { "key": "serve_in", "label": "Service réussi", "category": "neutral" },
          "trajectory": { "from": null, "to": 2 },
          "scoredFor": null
        },
        {
          "id": "ev-016", "sequence": 5, "ts": 1749131328000,
          "team": "mine",
          "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 4 },
          "action": { "key": "attack_no_pt", "label": "Attaque (sans pt)", "category": "neutral" },
          "trajectory": { "from": 4, "to": 3 },
          "scoredFor": null
        },
        {
          "id": "ev-017", "sequence": 6, "ts": 1749131335000,
          "team": "mine",
          "player": { "id": 3, "jersey": 7, "name": "Dupont", "position": 4 },
          "action": { "key": "attack_pt", "label": "Attaque", "category": "point" },
          "trajectory": { "from": 4, "to": 1 },
          "scoredFor": "mine"
        },
        {
          "id": "ev-018", "sequence": 7, "ts": 1749131342000,
          "team": "mine",
          "player": { "id": 8, "jersey": 4, "name": "Bernard", "position": 3 },
          "action": { "key": "block_pt", "label": "Contre", "category": "point" },
          "trajectory": null,
          "scoredFor": "mine"
        },
        {
          "id": "ev-019", "sequence": 8, "ts": 1749131349000,
          "team": "mine",
          "player": { "id": 5, "jersey": 12, "name": "Martin", "position": 1 },
          "action": { "key": "ace", "label": "Ace", "category": "point" },
          "trajectory": { "from": null, "to": 5 },
          "scoredFor": "mine"
        },
        {
          "id": "ev-020", "sequence": 9, "ts": 1749131356000,
          "team": "opp",
          "player": { "id": null, "jersey": 9, "name": "Adv #9", "position": 1 },
          "action": { "key": "serve_fault", "label": "Faute service", "category": "fault" },
          "trajectory": null,
          "scoredFor": "mine"
        }
      ]
    }
  ]
}
```

---

## Notes d'implémentation backend

- `sets` est ordonné par `setNumber` (croissant). `events` dans chaque set est ordonné par `sequence` (croissant, repart à 1 pour chaque set).
- `scoreTeam` et `scoreOpp` sont dérivés des events : comptage de `scoredFor === "mine"` / `"opp"`. Le backend peut les recalculer lui-même si nécessaire.
- `setsWon` reflète les sets terminés via l'action "Set suivant" dans l'app. Le dernier set en cours peut ne pas encore y être comptabilisé si le match n'est pas terminé.
- `ts` est le timestamp de saisie côté app (horloge locale). Utiliser `recordedAt` pour dater le match, `ts` pour l'ordre relatif des événements.
- Les joueurs adverses n'ont pas de `player.id` — les identifier uniquement par `player.jersey` dans le contexte du match.
- `trajectory.from` est `null` pour les services (ace, serve_in) : la zone de départ n'est pas saisie.
- Un même joueur peut apparaître à des `position` différentes entre deux événements (rotation en cours de set).
- Les événements `team = "opp"` avec `action.category = "fault"` → `scoredFor = "mine"`.
