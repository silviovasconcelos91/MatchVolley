---
id: MV-001
titre: Prendre en compte l'event malus dans la validation, l'overlay et l'analyse des stats live
type: feature
statut: EN REVUE
priorite: haute
cree: 2026-11-20
maj: 2026-11-20
depend_de: []
branche: feat/MV-001-event-malus
debat: docs/reviews/2026-11-20-MV-001-event-malus/
---

# MV-001 — Prendre en compte l'event malus dans la validation, l'overlay et l'analyse des stats live

## Demande initiale
> Backend task from the front session: support a new live-stats event `malus` (the front already sends it).
> Full spec is in this file: C:\Users\Silvio\workspace\perso\MatchVolleyFront\docs\malus-backend-prompt.md
> Updated API contract: C:\Users\Silvio\workspace\perso\MatchVolleyFront\docs\live-stats-api-contract.md
> Front implementation for reference: C:\Users\Silvio\workspace\perso\MatchVolleyFront\src\data\liveStatsPayload.ts and src\data\liveStats.ts
> Summary: an event with action.key='malus', category='malus', player=null, trajectory=null, scoredFor=null, malusAmount 1|2, team = penalized team. Backend recomputes scores from events, so it must subtract malusAmount from `team` (overlay + analysis + set scores); otherwise e.g. 25-15 in the app becomes 27-15 server-side. Same pattern as the recent timeout event (commit bd673c1).
> As the spec says: present your plan (files touched, migration if any, tests) to the user and wait for their validation before coding.

## Problème et valeur
Le coach peut fixer, en début de set, un malus de 1 ou 2 points à une équipe (handicap : l'équipe démarre le set à -1/-2). Le front envoie désormais ce malus comme un event `malus` dans `sets[].events` (spec front : `MatchVolleyFront/docs/malus-backend-prompt.md`).
Aujourd'hui le backend **rejette** cet event : `LiveStatsPayloadValidator.java:97` exige `player.jersey` (player est `null` pour un malus) puis `:104` refuse la catégorie `malus` → **400 sur tout le payload**. Conséquence : dès qu'un malus est saisi, les `PUT /live-sessions/{id}/snapshot` échouent (overlay figé pour les spectateurs). *(Correction 2026-11-20, round 1 : le `POST /matches/{id}/live-stats` final n'échouait pas, car il n'appelait pas le validateur et acceptait le malus en 201 ; mais l'analyse recomptait alors 27-15. Le POST est désormais validé, cf. décision Q4.)* Et une fois accepté, sans traitement, l'overlay recompterait 27-15 au lieu de 25-15.

## User story
En tant que coach qui applique un malus en début de set, je veux que le backend accepte cet event et le déduise du score, afin que l'overlay public, l'historique et l'analyse affichent le même score que l'app.

## Critères d'acceptation

Validation (`LiveStatsPayloadValidator`, commun à `POST /matches/{id}/live-stats` et `PUT /live-sessions/{id}/snapshot` ; le branchement sur le POST est ajouté par ce ticket, cf. décision Q4) :
- [x] **CA-1** — Étant donné un set dont le premier event est `{team:"mine", player:null, action:{key:"malus",category:"malus"}, trajectory:null, scoredFor:null, malusAmount:2}`, quand le payload est envoyé, alors il est accepté (201 en POST, 200 en PUT snapshot).
- [x] **CA-2** — Étant donné un event malus avec `malusAmount` à `0`, `3` ou `null`, alors 400.
- [x] **CA-3** — Étant donné un event non-malus (point, faute, neutre, temps mort) avec `malusAmount` non null, alors 400.
- [x] **CA-4** — Étant donné `category:"malus"` avec `key != "malus"`, ou `key:"malus"` avec `category != "malus"`, alors 400.
- [x] **CA-5** — Étant donné un malus avec `player`, `trajectory` ou `scoredFor` non null, alors 400.
- [x] **CA-6** — Étant donné un malus dont la `sequence` est supérieure à celle d'un event avec `scoredFor != null` du même set, alors 400 (l'ordre est évalué par `sequence`, pas par position dans la liste). Un malus précédé uniquement d'un temps mort ou d'une action neutre est accepté.
- [x] **CA-7** — Étant donné deux malus pour la même équipe dans un même set, alors 400 ; un malus `mine` et un malus `opp` dans le même set sont acceptés ; un malus `mine` dans le set 1 et un autre dans le set 2 sont acceptés.
- [x] **CA-8** — Étant donné un payload sans aucun champ `malusAmount` (front antérieur, snapshots déjà stockés), alors il est accepté et rendu comme avant (rétrocompatibilité).

Overlay public (`GET /public/live/{code}`, `LivePublicViewService.computeState`) :
- [x] **CA-9** — Étant donné un set avec malus -2 `mine` puis 27 points `mine` et 15 points `opp`, alors `currentSet.score` et `sets[].score` valent `{mine:25, opp:15}`.
- [x] **CA-10** — Étant donné un malus -2 `mine` seul dans le set en cours, alors `currentSet.score` vaut `{mine:-2, opp:0}`, `trajectory` vaut `[{mine:-2, opp:0}]` et `serving` est `null`.
- [x] **CA-11** — Étant donné un malus -2 `mine` puis un point `opp`, alors `trajectory` vaut `[{-2,0},{-2,1}]`, `serving` vaut `"opp"`, `lastPoints` contient uniquement le point `opp` avec `scoreAfter {mine:-2, opp:1}`.
- [x] **CA-12** — Étant donné un malus `mine`, alors il n'apparaît ni dans `lastPoints` ni dans `topScorers`, et n'incrémente pas `currentSet.timeouts`.
- [x] **CA-13** — Étant donné un snapshot avec malus, puis un snapshot suivant identique sans l'event malus (undo), alors le score de l'overlay remonte (malus non déduit).

Analyse (`GET /matches/{id}/live-stats/analysis`, `LiveStatsAnalysisService`) :
- [x] **CA-14** — Étant donné un set avec malus -2 `mine` puis un point `mine`, alors la première entrée de `timeline` vaut `myScore:-1, oppScore:0` et aucune entrée de timeline ne correspond au malus.
- [x] **CA-15** — Étant donné malus -2 `mine`, un point `opp`, puis un temps mort, alors le temps mort a `myScore:-2, oppScore:1` et `afterPoint:1` (`afterPoint` reste le **nombre de points joués**, pas la somme des scores).
- [x] **CA-16** — Étant donné un malus `mine`, alors il n'apparaît dans aucune stat d'action (`actions.points/faults/neutral`) d'équipe, de set ou de joueur.
- [x] **CA-18** — Étant donné un set avec malus -2 `mine` et malus -1 `opp`, alors l'analyse du set expose `startScore: {myScore:-2, oppScore:-1}` ; étant donné un set sans malus (y compris données stockées avant ce ticket), alors `startScore` vaut `{myScore:0, oppScore:0}`.
- [x] **CA-19** — Étant donné un malus `mine` de 2 dans le set 5, alors il est accepté (pas de limite serveur au set décisif).
- [x] **CA-17** — Le score de set stocké par `POST …/live-stats` reste `scoreTeam`/`scoreOpp` du payload (déjà malus déduits côté front) : un payload `scoreTeam:25, scoreOpp:15` avec malus -2 et 27/15 points est restitué `25-15` par `GET /matches/{id}/live-stats` et par l'analyse.

Isolation : inchangée (aucun nouvel endpoint ; les endpoints existants filtrent déjà par utilisateur).

## Règles métier
- Malus = handicap fixé **en début de set** : l'équipe `team` démarre le set à `-malusAmount` (spec front, `live-stats-api-contract.md` côté front §Règles dérivées). Ce n'est pas une règle FIVB mais un usage de championnat loisir.
- `malusAmount` ∈ {1, 2} ; au plus un malus par équipe et par set ; placé avant tout event avec `scoredFor != null` (temps morts et actions neutres peuvent le précéder).
- Score d'un set = (nb d'events `scoredFor == team`) − (somme des `malusAmount` des malus de `team`), calculé dans l'ordre de `sequence`. Un score négatif est valide.
- Un malus n'est pas un point : ne change pas le service (`serving`), n'apparaît pas dans `lastPoints` ni dans la `timeline`, n'est compté ni dans les stats d'actions ni dans les meilleurs marqueurs.
- `wonBy` et `setsWon` restent fournis par le front (le backend ne détermine pas la fin de set, inchangé).
- Le front limite le malus à 1 point au set décisif (2-2) (`MatchVolleyFront/src/components/ScoreHeader.tsx:48`) ; le backend n'applique pas cette limite (décision Q3).
- Score de départ d'un set (analyse) = `{myScore: -malus mine, oppScore: -malus opp}`, `{0,0}` sans malus (décision Q1).
- Un malus ne termine pas un temps mort actif (`activeTimeout`) sur l'overlay : il n'est pas une action de jeu.
- Le score de set stocké au `POST` reste celui du payload (pas de recalcul serveur).

## Hors périmètre
- Exposer explicitement le malus aux spectateurs (compteur/badge « Malus -2 » sur l'overlay) : `trajectory[0]` et le score négatif portent déjà l'information (décision Q2). Ticket suivant si besoin.
- Limite serveur du malus à 1 point au 5e set (décision Q3).
- Adaptation du graphe front (`SetGraph.tsx`) pour utiliser `startScore` : côté front.
- Recalcul serveur de `scoreTeam`/`scoreOpp` au `POST` à partir des events : les valeurs du payload sont déjà correctes (spec front) et c'est le comportement actuel.
- Détermination serveur de la fin de set / `wonBy`.
- Malus en cours de set (sanctions arbitrales type carton rouge = point adverse) : ce n'est pas ce qu'implémente le front.

## Impacts
- **API / contrat front** : nouveau champ optionnel `malusAmount` sur `EventPayload` (additif, rétrocompatible) ; nouvelle catégorie `malus`. Docs racine à mettre à jour : `live-stats-api-contract.md` (champ, catégorie, règles dérivées), `live-overlay-api-doc.md` (validation l.173 et suivantes, copie du contrat l.456/475/494, règles de calcul score/trajectoire), `live-stats-analysis-api-doc.md` (timeline et timeouts malus déduits). **Ne pas recopier** le contrat du front tel quel : il a divergé (il perd la règle « Temps mort », mentionne un « backend Kotlin » alors que le backend est Java) — n'ajouter que les parties malus.
- **Données** : aucune migration. Les events sont stockés en `jsonb` (`MatchLiveSetEvent.java:55`, `LiveSession.java:77`) ; ajout d'un composant nullable au record `LiveEventDto` → les données existantes se désérialisent avec `malusAmount = null`.
- **Public / overlay** : aucune donnée personnelle nouvelle (malus sans joueur). Pas de coût supplémentaire notable sur le chemin chaud (même boucle sur les events, +1 test par event). ETag inchangé (dérivé de la révision).
- **Autres** : native image — `LiveEventDto` est couvert par `BindingReflectionHintsRegistrar` sur `LiveStatsRequest` (`NativeConfig.java:30`), rien à ajouter a priori. Jackson 3.

## Plan technique (esquisse, à valider par l'utilisateur)
- `dto/live/LiveEventDto.java` : ajouter `Integer malusAmount` (dernier composant). Mettre à jour tous les `new LiveEventDto(...)` (tests/fixtures, mapper si concerné).
- `service/LiveStatsPayloadValidator.java` : `MALUS_KEY`/`isMalus(event)` (comme `isTimeout`) ; `validateMalus` (category, player/trajectory/scoredFor null, amount ∈ {1,2}) ; `malusAmount` interdit hors malus ; règles par set (au plus un par équipe, sequence < première sequence scorante) dans `validateSet` ; catégorie `malus` acceptée uniquement avec `key == malus`.
- `service/LivePublicViewService.java` : dans la boucle de `computeState`, un malus décrémente `mine`/`opp` **avant** l'ajout du point de départ de la trajectoire (trajectoire initialisée au score après malus de tête) ; pas de `lastScorer`, pas de `PublicPointDto` ; ne touche pas `activeTimeout`. `topScorers` déjà filtré par `category == point`.
- `service/LiveStatsAnalysisService.java` : `buildTimeline` et `buildTimeouts` déduisent le malus ; `afterPoint` passe sur un compteur de points dédié ; `computeScopeStats` exclut les malus comme les temps morts. Nouveau composant `startScore` (`{myScore, oppScore}`, nouveau record ou réutilisation d'un record existant cohérent avec le nommage de l'analyse) dans `LiveSetAnalysisDto`. Tests : `LiveStatsAnalysisServiceTest` couvre aussi CA-18, `LiveStatsPayloadValidatorTest` CA-19.
- Tests : `LiveStatsPayloadValidatorTest` (CA-1 à CA-8), `LivePublicViewServiceTest` (CA-9 à CA-13), `LiveStatsAnalysisServiceTest` (CA-14 à CA-16), `LiveStatsControllerIT` (POST avec malus 201 + 400 + CA-17), `PublicLiveControllerIT` (PUT snapshot avec malus puis GET overlay 25-15), `LiveSnapshotFixtures` (fabrique `malus(seq, team, amount)`).
- Docs : les 3 `.md` racine.
- Migration : **non**.

## Hypothèses
- Aucune (toutes tranchées, voir Décisions).

## Décisions
| Question | Décision | Par | Date |
|---|---|---|---|
| Q1 — Score de départ dans l'analyse | Champ `startScore {myScore, oppScore}` par set dans `LiveSetAnalysisDto` (0-0 sans malus), pas d'entrée malus dans la timeline | Utilisateur | 2026-11-20 |
| Q2 — Malus affiché explicitement sur l'overlay | Non, hors périmètre | Utilisateur | 2026-11-20 |
| Q3 — Limite à 1 point au 5e set côté backend | Non, 1 ou 2 acceptés dans tous les sets | Utilisateur | 2026-11-20 |
| H1 — Score de set stocké au POST | Score du payload conservé, pas de recalcul | Utilisateur (non contesté) | 2026-11-20 |
| H3 — Malus et `activeTimeout` | Le malus ne termine pas un temps mort actif | Utilisateur (non contesté) | 2026-11-20 |
| H4 — Malus dans la timeline | Aucune entrée | Utilisateur (non contesté) | 2026-11-20 |
| Plan technique | Validé sans objection (pas de migration) | Utilisateur | 2026-11-20 |
| Q4 — Valider le payload au `POST /matches/{id}/live-stats` (CRIT-001 / ARCH-001, round 1) | Oui : même validateur que le PUT snapshot, appelé en tête de `LiveStatsService.saveLiveStats` ; 400 sur toutes les règles du contrat | Utilisateur | 2026-11-20 |
| Q5 — Limite de 5 sets du validateur, bloquante au POST depuis Q4 (CRIT-101, round 2) | Assouplie sur POST et PUT : borne de sécurité relevée à 10 sets (`setNumber` 1–10), car un set à égalité (tournoi au temps) ne compte pour personne et le front n'a pas de plafond | Utilisateur (borne choisie par le développeur) | 2026-11-20 |

## Risques
- Le front dessine le graphe de set en partant de `{0,0}` en dur (`MatchVolleyFront/src/components/SetGraph.tsx:20`) — le backend expose `startScore` ; le front devra l'utiliser (à signaler à la session front, documenté dans `live-stats-analysis-api-doc.md`).
- Régression silencieuse sur `afterPoint` des temps morts si on le calcule comme `myScore + oppScore` après déduction — CA-15 le verrouille.
- Ordre des events : le validateur parcourt la liste dans l'ordre reçu ; la règle « malus avant le premier point » doit utiliser `sequence` — CA-6.
- Divergence des docs contrat front/back — n'appliquer que le delta malus.
- Ordre de déploiement : déployer ce backend **avant** le front qui envoie l'event malus (`MatchVolleyFront` a962d34, non poussé au 2026-11-20). Un malus reçu par l'ancien backend serait stocké sans `malusAmount` (champ inconnu ignoré) et compté 0 dans l'analyse (CRIT-005).

## Historique
| Date | Statut | Événement |
|---|---|---|
| 2026-11-20 | BROUILLON | Création depuis la demande utilisateur (relayée par la session front) |
| 2026-11-20 | PRÊT | Réponses utilisateur intégrées (Q1 startScore, Q2 hors périmètre, Q3 pas de limite set 5), plan technique validé |
| 2026-11-20 | EN COURS | Développement lancé via dev-debate (branche feat/MV-001-event-malus, base bd673c1) |
| 2026-11-20 | EN REVUE | Implémentation terminée (248 tests verts, IT inclus), revue critic/perf/architect lancée |
| 2026-11-20 | EN REVUE | Débat terminé : PRÊT AVEC RÉSERVES (build natif non vérifié), 3 rounds, 268 tests verts, voir docs/reviews/2026-11-20-MV-001-event-malus/SYNTHESE.md |
