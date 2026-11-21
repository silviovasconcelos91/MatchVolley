---
type: architecture-review
date: 2026-11-21
perimetre: diff courant depuis 73e5c66 (git diff 73e5c66 + non suivis), hors CLAUDE.md, .gitignore, .claude/, docs/architecture/
base: feat/MV-002-rejeu-score-unique@73e5c66
verdict: CONFORME
bloquants: 0
majeurs: 0
mineurs: 1
---

# Revue d'architecture — MV-002 rejeu unique du score d'un set

## Synthèse
Le changement extrait le rejeu du score d'un set dans `service/LiveSetReplay` : une classe finale immuable, avec une fabrique statique, placée à côté de `LiveEventRules`. L'overlay (`LivePublicViewService.computeState`) et l'analyse (`LiveStatsAnalysisService.getMatchAnalysis`, `buildTimeline`, `buildTimeouts`) l'utilisent tous les deux. Le tri par `sequence` est stable, avec les nulls en dernier. L'implémentation s'inscrit bien dans l'architecture : calcul pur sur des DTO, pas de bean, pas de réflexion, aucun DTO de sortie couplé, et un seul tri par set et par appel sur les deux chemins.

Les 9 CA sont couverts par des tests ou par un grep vérifié :
- CA-7 : l'incrément sur `scoredFor` n'existe plus que dans `LiveSetReplay.java:82-85`.
- CA-8 : grep vide.
- CA-6 : aucune ligne `-` dans `git diff -U0 73e5c66 -- src/test`.

Il reste une seule scorie de conception : la méthode publique `startScore(List)`, devenue morte, est un second calcul du score de départ. L'interprétation de CA-4 côté overlay (point sans `sequence` absent de `lastPoints`) relève d'un arbitrage produit, voir les Questions ouvertes.

## Constats

### ARCH-001 — `startScore(List)` est devenue une méthode publique morte et un second calcul du score de départ [MINEUR]
- **Catégorie** : conception
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java:158-163`. Références javadoc à `{@link #startScore}` aux lignes 166 et 176.
- **Constat** : `getMatchAnalysis` prend maintenant le score de départ dans le rejeu (`new LiveScoreDto(replay.startMine(), replay.startOpp())`, l.65). La méthode publique `startScore(List<LiveEventDto>)` reste pourtant en place et recalcule `-malusOf(..., MINE/OPP)` de son côté. `grep -rn "startScore(" src` ne trouve que sa déclaration : aucun appel en main ni en test (les tests lisent le champ DTO `.startScore()`). Les javadocs de `buildTimeline` et `buildTimeouts` décrivent encore leur score comme partant de `{@link #startScore}`, alors qu'il part de `LiveSetReplay`.
- **Règle** : l'objectif du ticket (règles métier, CA-7) est une seule source du rejeu, score de départ compris (« le rejeu part du score `{-malusMine, -malusOpp}` »). Le bon modèle est dans le même diff : `buildTimeline` et `buildTimeouts` ont été gardées mais délèguent au rejeu (`LiveStatsAnalysisService.java:169-171`, `178-180`).
- **Impact** : faible aujourd'hui, puisque les deux calculs reposent sur `LiveEventRules.malusOf`. Mais c'est une seconde porte d'entrée publique pour le score de départ. Si la règle du départ évolue dans `LiveSetReplay`, cette méthode divergera sans qu'aucun test ne le signale. Et la javadoc oriente le lecteur vers la mauvaise source.
- **Correction attendue** :
  1. Dans `LiveStatsAnalysisService.java`, supprimer la méthode `startScore(List<LiveEventDto>)` et sa javadoc (l.158-163). Aucun appelant n'existe.
  2. Dans la javadoc de `buildTimeline` (l.166) et de `buildTimeouts` (l.176), remplacer `{@link #startScore}` par `{@link LiveSetReplay#startMine()}`/`{@link LiveSetReplay#startOpp()}`, ou par « le score de départ du rejeu (malus déduits) ».
  3. Si la suppression est refusée, la faire déléguer : `LiveSetReplay replay = LiveSetReplay.of(events); return new LiveScoreDto(replay.startMine(), replay.startOpp());`.
- **Critère de validation** :
  - `grep -n "startScore(List\|#startScore" src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java` est vide (ou ne montre que la version qui délègue) ;
  - `./mvnw test -Dtest=LiveStatsAnalysisServiceTest,LiveScoreReplayConsistencyTest` est vert.

## Dette existante remise en question

### ARCH-D01 — Le poste d'un joueur par set dépend encore de l'ordre de stockage des events [MINEUR]
- **Catégorie** : domaine
- **Localisation** :
  - `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java:227-236` : filtre `playerSetEvents` sur `se.getEvents()` brut ;
  - `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java:250-257` : `posteOf`, `findFirst` du premier poste non nul.
- **Constat** : `computePlayerAnalysis` parcourt les events dans l'ordre stocké. `posteOf` retient le premier poste non nul rencontré. Le poste affiché pour un joueur dans un set dépend donc de l'ordre de stockage, et non de `sequence`. C'est exactement le défaut que MV-002 corrige pour la timeline. Le développeur l'a lui-même signalé (`00-implementation.md`, Points d'attention).
- **Règle** : l'ordre d'un set est défini par `sequence` (`live-stats-api-contract.md:45`). `LiveSetReplay.events()` fournit désormais cet ordre de référence.
- **Impact** : pour un payload stocké dans le désordre avec un joueur qui change de poste dans le set, l'analyse peut afficher un poste différent de celui du premier event selon `sequence`. Les comptages (`computeScopeStats`) ne sont pas affectés.
- **Correction attendue** : dans un ticket dédié (le ticket MV-002 exclut explicitement `computePlayerAnalysis`), faire itérer `computePlayerAnalysis` sur les events triés. Deux possibilités :
  - `LiveSetReplay.of(se.getEvents()).events()`, au prix d'un rejeu par set et par appel, ce qui reste raisonnable sur un chemin non chaud ;
  - extraire un `LiveSetReplay.ordered(List)` statique si l'on ne veut que le tri.

  Dans les deux cas, le tri reste défini à un seul endroit.
- **Critère de validation** : un test `LiveStatsAnalysisServiceTest` avec un joueur dont les events (postes différents) sont stockés dans le désordre : le poste retenu est celui de la plus petite `sequence`.
- **Aggravé par le diff : non**

## Questions ouvertes

### Q1 — Restitution, dans `lastPoints` de l'overlay, d'un point dont la `sequence` est null (CA-4)
- **Contexte** : `PublicPointDto.sequence` est un `int` (`dto/livesession/PublicPointDto.java:9`). Avant le diff, un point sans `sequence` faisait lever une NPE à `computeState` (unboxing). Le diff fait désormais compter ce point dans le score, la trajectoire et `serving`, mais l'exclut de `lastPoints` (`LivePublicViewService.java:125-128`). C'est verrouillé par `LivePublicViewServiceTest#should_replay_null_sequence_events_last_in_stored_order_when_legacy_payload`. CA-4 est respecté sur l'essentiel (rejeu en dernier, ordre stocké, sans exception). Il n'avait pas anticipé ce cas, et le développeur demande un arbitrage.
- **Options** :
  1. Garder l'exclusion (état actuel). Pas de changement de contrat. Le cas est inatteignable en pratique : le PUT de snapshot est validé et les sessions expirent.
  2. Passer `PublicPointDto.sequence` en `Integer` nullable. C'est un changement de contrat public (`live-overlay-api-doc.md:325-340`), hors périmètre du ticket.
  3. Substituer une valeur, comme 0 ou la position. Cela fabrique une donnée fausse.
- **Recommandation** : option 1. Elle supprime une NPE sur le chemin public sans toucher au contrat. Une phrase dans le tableau `lastPoints` de `live-overlay-api-doc.md:372` (« un point sans `sequence`, donnée antérieure à la validation, n'y figure pas ») est facultative, vu que le cas est inatteignable.

## Ordre de traitement recommandé
1. ARCH-001, indépendant et local à `LiveStatsAnalysisService`.
2. ARCH-D01 : hors de ce ticket, à proposer en ticket de dette séparé.
3. Q1 : décision PO/utilisateur. Aucune action de code si l'option 1 est retenue.
