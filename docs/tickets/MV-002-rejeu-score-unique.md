---
id: MV-002
titre: Unifier le rejeu du score d'un set entre l'overlay et l'analyse
type: dette
statut: PRÊT
priorite: moyenne
cree: 2026-11-20
maj: 2026-11-20
depend_de: [MV-001]
branche:
debat:
---

# MV-002 — Unifier le rejeu du score d'un set entre l'overlay et l'analyse

## Demande initiale
> Créé les tickets de dettes

Dette ARCH-D01, relevée pendant la revue de MV-001 (`docs/reviews/2026-11-20-MV-001-event-malus/r1-architect.md`, section « Dette existante » ; `r2-architect.md`, remarque sur `LiveStatsAnalysisService.java:158-159` ; `SYNTHESE.md`, « Reportés »). La dette existait avant MV-001.

## Problème et valeur
Le score d'un set est recalculé à partir des events à trois endroits, avec deux politiques d'ordre différentes :
- overlay public : `LivePublicViewService.computeState` (`LivePublicViewService.java:115-163`) trie les events par `sequence` (`orderedEvents`, l.176-183) ;
- analyse : `LiveStatsAnalysisService.buildTimeline` (l.165-180) et `buildTimeouts` (l.187-206) suivent l'ordre stocké, sans tri.

Si un payload est stocké dans le désordre, la timeline et les scores aux temps morts de l'analyse sont faux, alors que l'overlay est juste. Le staff qui relit le match après coup voit alors une courbe de score différente de celle que les spectateurs ont vue. Chaque nouveau type d'event (après `timeout` et `malus`) doit aussi être traité trois fois, d'où un risque de divergence.
L'analyse utilise en plus les littéraux `"mine"`/`"opp"` (l.75, 158-159, 172-174, 197, 200, 215, 231) alors que `LiveEventRules.MINE`/`OPP` existent depuis MV-001.

## User story
En tant que staff qui analyse un match après coup, je veux que la timeline et les scores aux temps morts de l'analyse soient calculés comme le score de l'overlay, afin de voir le même déroulé de set partout, quel que soit l'ordre de stockage des events.

## Critères d'acceptation
- [ ] **CA-1** — Étant donné un set dont les events sont stockés dans un ordre différent de leur `sequence` (ex. stockés 3, 1, 2), quand l'analyse est calculée, alors `timeline` contient une entrée par event marquant, dans l'ordre des `sequence`, avec le score cumulé correct après chaque point (test unitaire `LiveStatsAnalysisServiceTest`).
- [ ] **CA-2** — Étant donné le même set dans le désordre, avec un temps mort entre deux points, quand l'analyse est calculée, alors `timeouts[].afterPoint`, `myScore` et `oppScore` correspondent à la position du temps mort selon `sequence` (test unitaire).
- [ ] **CA-3** — Étant donné un même `LiveStatsRequest` (avec malus, temps morts, actions neutres, events dans le désordre), quand on calcule l'overlay (`computeState`) et l'analyse, alors la trajectoire de l'overlay (`currentSet.trajectory`, hors point de départ) et les scores de la `timeline` de l'analyse sont identiques, et le score de départ de l'overlay est égal au `startScore` de l'analyse (test unitaire croisé).
- [ ] **CA-4** — Étant donné des events dont la `sequence` est `null` (données stockées avant la validation du POST), quand l'overlay ou l'analyse est calculé, alors ces events sont rejoués en dernier, dans leur ordre stocké, sans exception (même comportement que `orderedEvents` aujourd'hui, `nullsLast`).
- [ ] **CA-5** — Étant donné deux events de même `sequence`, quand le set est rejoué, alors leur ordre stocké relatif est conservé (tri stable).
- [ ] **CA-6** — Tous les tests existants de l'overlay et de l'analyse (`LivePublicViewServiceTest`, `LiveStatsAnalysisServiceTest`, `PublicLiveControllerIT`, `LiveStatsControllerIT`) passent sans modification de leurs assertions. Le contrat JSON de `GET /public/live/{code}` et de `GET /matches/{id}/live-stats/analysis` est inchangé.
- [ ] **CA-7** — Il n'existe sous `src/main` qu'une seule implémentation du cumul de score d'un set (incrément sur `scoredFor`) : `grep -rn "scoredFor()" src/main/java/vasconcelos/volleymatch/service` ne montre l'incrément que dans le composant de rejeu (plus le validateur, qui ne cumule pas).
- [ ] **CA-8** — `LiveStatsAnalysisService` n'utilise plus de littéral `"mine"` ni `"opp"` : `grep -n '"mine"\|"opp"' src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java` est vide (usage de `LiveEventRules.MINE`/`OPP`).
- [ ] **CA-9** — Le composant de rejeu a ses propres tests unitaires (`XxxTest`, sans contexte Spring) : set vide, `events` null, malus seul, malus puis points, temps mort final actif, temps mort suivi d'un point, events dans le désordre.

## Règles métier
- Le malus est un handicap de début de set : le rejeu part du score `{-malusMine, -malusOpp}` (`LiveEventRules.malusOf`), le malus n'apparaît ni dans la timeline ni dans la trajectoire comme point (existant, MV-001 CA-9/CA-14).
- Un temps mort ne marque pas ; `afterPoint` = nombre d'events marquants qui le précèdent, pas la somme des scores (existant, MV-001 CA-15, javadoc `LiveStatsAnalysisService.java:183-186`).
- Seul un temps mort en fin de set reste « actif » sur l'overlay ; un malus ne l'annule pas (`LivePublicViewService.java:127-139`).
- L'ordre d'un set est défini par `sequence` (contrat `live-stats-api-contract.md`, « Events du set, ordonnés par `sequence` »), pas par la position dans la liste.
- Le score final d'un set dans l'analyse reste celui envoyé par le front (`scoreTeam`/`scoreOpp`, MV-001 CA-17) : ce ticket ne le recalcule pas.

## Hors périmètre
- Recalculer `scoreTeam`/`scoreOpp` stockés à partir des events : décision MV-001 (CA-17), le payload fait foi.
- Toute évolution du contrat JSON (nouveau champ, renommage) : refactorisation pure, à l'exception du tri de l'analyse.
- Les statistiques d'actions (`computeScopeStats`, `computePlayerAnalysis`, `topScorers`) : ce sont des comptages, indépendants de l'ordre.
- La validation (`LiveStatsPayloadValidator`) : inchangée.
- Une éventuelle migration des snapshots stockés : aucune donnée n'est réécrite, le tri se fait à la lecture.

## Impacts
- **API / contrat front** : aucun changement de forme. Changement de comportement uniquement pour un payload stocké dans le désordre : l'analyse devient juste (alignée sur l'overlay). Pas de mise à jour de `live-*.md` requise ; si la doc analyse (`live-stats-analysis-api-doc.md`) précise un ordre, vérifier qu'elle dit « ordre de `sequence` ».
- **Données** : aucune migration. Compatibilité avec les snapshots et matchs déjà stockés, y compris `sequence` null (CA-4).
- **Public / overlay** : chemin chaud (overlay interrogé en boucle). Le rejeu ne doit pas ajouter de requête SQL ni de tri supplémentaire par rapport à aujourd'hui (un tri par set et par appel, comme `orderedEvents`). Pas d'exposition de données nouvelle.
- **Autres** : native image. Si le composant de rejeu est une classe utilitaire statique ou un `record` interne sans réflexion, aucun hint n'est requis. À vérifier par l'architecte, et build natif local avant `master`.

## Hypothèses
- Aucune : refactorisation interne, décisions prises par le PO ci-dessous (modifiables par l'utilisateur).

## Décisions
| Question | Décision | Par | Date |
|---|---|---|---|
| L'analyse doit-elle trier par `sequence` (changement de comportement) ? | Oui : c'est l'ordre défini par le contrat, et l'overlay le fait déjà | PO | 2026-11-20 |
| Où placer le rejeu ? | Libre pour le développeur ; piste de l'architecte : `LiveSetReplay` à côté de `LiveEventRules` (`r2-architect.md`, « Dette restante ») | PO | 2026-11-20 |
| Faut-il un ticket séparé pour les littéraux `"mine"`/`"opp"` ? | Non, intégré ici (CA-8), comme suggéré en revue | PO | 2026-11-20 |

## Risques
- Régression sur l'overlay (chemin public) lors de l'extraction — mitigation : CA-6 (tests existants inchangés) et CA-3 (test croisé overlay/analyse).
- Dégradation de perf sur le poll overlay — mitigation : relecture `perf`, pas plus d'un tri par set et par appel.
- Branche dépendante de MV-001 (`LiveEventRules` n'existe que sur `feat/MV-001-event-malus`) — mitigation : démarrer après la fusion de MV-001.

## Historique
| Date | Statut | Événement |
|---|---|---|
| 2026-11-20 | PRÊT | Création depuis la dette ARCH-D01 (revue MV-001), sans question ouverte |
