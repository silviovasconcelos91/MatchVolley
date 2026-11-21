---
type: critic-review
round: 1
bloquants: 0
majeurs: 0
mineurs: 5
---
# Revue critique — MV-002 Unifier le rejeu du score d'un set (LiveSetReplay)

## Ce qui a été vérifié (et tient)

- **Équivalence overlay avant/après sur données validées**, en rejouant l'ancien `computeState` (`git show 73e5c66`) branche par branche contre `LiveSetReplay.of` :
  - malus : il est ignoré dans la boucle et ne remet pas à zéro `activeTimeout`. `malusOf` est une somme, donc le fait qu'elle soit maintenant appelée sur la liste triée ne change rien ;
  - temps mort : il est testé avant `scoredFor`, il ne marque jamais, et le comptage par équipe est identique (`timeoutCounts`, l.139) ;
  - `scoredFor == null` : pas un point, mais l'event termine le temps mort, comme avant ;
  - serving : il vient de `lastScorer` = `scoredFor` du dernier point, comme avant ;
  - trajectoire : elle part de `startMine/startOpp` puis reçoit un `ScoreDto` par point ;
  - `lastPoints` : même ordre, même `scoreAfter` ;
  - `topScorers` : il parcourt `replay.events()` dans l'ordre `orderedSets`. C'est le même ordre qu'avant, verrouillé par `should_name_top_scorer_from_lowest_sequence_when_payload_is_unordered`.
- **Tri** : le comparateur est identique (`nullsLast(naturalOrder())`). `ArrayList.sort` et `Stream.sorted` sur un flux ordonné sont tous les deux stables. CA-4 et CA-5 sont donc tenus.
- **Caractérisation analyse** (`should_exposeFullTimelineAndTimeouts_when_orderedSetWithMalusTimeoutAndNeutral`) : je l'ai déroulée à la main sur l'ancien `buildTimeline`/`buildTimeouts`. On obtient bien (1,-1,3L) (1,0,null) (2,0,null) (2,1,8L) et le temps mort `opp` avec afterPoint 1 et (1,-1). Le test aurait donc été vert avant, et il verrouille `playerId`, `action` et `occurredAt`, pas seulement les scores.
- **CA-6** : `git diff -U0 73e5c66 -- src/test` ne contient aucune ligne supprimée.
- **CA-7** : grep conforme. Les incréments ne sont que dans `LiveSetReplay.java:83,85` ; les autres occurrences sont des lectures.
- **CA-8** : grep conforme.
- **CA-3** : le test croisé compare la trajectoire (sans son premier point) à la timeline, le départ à `startScore`, et vérifie les `afterPoint` (1, 4) sur 9 events mélangés.
- **CA-9** : tous les cas demandés sont présents.
- J'ai relancé `LiveSetReplayTest`, `LiveScoreReplayConsistencyTest`, `LivePublicViewServiceTest` et `LiveStatsAnalysisServiceTest` : 10 + 1 + 29 + 35 tests, tous verts.

Je ne trouve ni BLOQUANT ni MAJEUR. Les constats ci-dessous sont des mineurs. CRIT-002 corrige une justification factuellement fausse de la note.

---

### CRIT-001 — Point sans `sequence` : `lastPoints` incohérent avec `score`/`serving` [MINEUR]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LivePublicViewService.java:126`
- **Constat** : un point dont la `sequence` est null est compté dans `score`, `trajectory` et `serving`, mais il est exclu de `lastPoints`.
- **Scénario de défaillance** : on prend le snapshot du test `should_replay_null_sequence_events_last_in_stored_order_when_legacy_payload`. On obtient `score = (3,1)` et `serving = "mine"`, mais `lastPoints[0]` est le point de séquence 2 avec `scoreAfter = (2,0)`. L'overlay afficherait un « dernier point » dont le score ne correspond pas au score courant.
- **Preuve** : le cas est **inatteignable** sur l'overlay. Le snapshot n'est écrit que par `LiveSessionService.java:98`, après `payloadValidator.validate`, et le validateur exige `sequence >= 1` depuis la création des sessions (`git show 76f5285:…/LiveStatsPayloadValidator.java:73`). L'ancien code levait bien une NPE (unboxing vers `PublicPointDto.sequence` de type `int`), mais sur un état qui ne pouvait pas exister. Le qualifier de « bug existant » est exagéré.
- **Position sur l'arbitrage n°1** : j'accepte le choix du développeur. Il n'y a plus d'exception, le score reste juste, le contrat est inchangé, et les alternatives sont pires (`Integer` change le contrat, 0 invente une valeur). Il n'y a pas lieu de bloquer.
- **Correction proposée** : aucune dans le code. Dans la note, remplacer « bug existant » par « NPE sur un état inatteignable (snapshot toujours validé) ».
- **Question au développeur** : aucune, sauf si le PO préfère une autre restitution.

### CRIT-002 — « Données invalides rejetées sur les deux chemins d'écriture » : faux pour l'analyse déjà stockée [MINEUR]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveSetReplay.java:79-86`. La note d'implémentation le dit dans « Choix de conception » (politique `scoredFor`) et dans « Points d'attention ».
- **Constat** : la note affirme que les cas invalides (`scoredFor` inconnu, temps mort ou malus porteur d'un `scoredFor`) sont « rejetés par le validateur sur les deux chemins d'écriture », et que « aucune donnée validée n'est concernée ». Pour l'**analyse**, c'est inexact. `LiveStatsService` n'appelle `payloadValidator.validate` que depuis `8712564` (MV-001, `git log -S"payloadValidator.validate"`). Toutes les `MatchLiveSession` enregistrées par POST avant MV-001 n'ont jamais été validées. Le ticket les mentionne d'ailleurs explicitement en CA-4 (« données stockées avant la validation du POST »).
- **Scénario de défaillance** : un match POSTé avant MV-001 contient un event temps mort avec `scoredFor: "mine"`, ou un event avec `scoredFor: "both"`.
  - Avant : l'ancien `buildTimeline` ajoutait une entrée (+1 pour le temps mort, score inchangé pour `"both"`), alors que l'ancien `buildTimeouts` ne le comptait pas.
  - Après : aucune entrée dans la timeline.

  La longueur de la timeline change donc sur des données stockées, sans changement d'ordre. Cela contredit l'« Impacts » du ticket : « Changement de comportement uniquement pour un payload stocké dans le désordre ».
- **Position sur l'arbitrage n°2** : sur le fond, la règle unique retenue est **meilleure**. L'ancien code était incohérent avec lui-même, puisque `timeline` et `timeouts` ne comptaient pas les mêmes points, ce qui décalait les repères `afterPoint`. La nouvelle règle rétablit l'alignement. Je n'ai pas de preuve qu'un front ait produit de telles données : le même front écrit des snapshots validés depuis le 9/11. Le constat reste donc MINEUR.
- **Correction proposée** :
  1. corriger la note et l'historique du ticket : « changement assumé sur des matchs stockés avant MV-001 et non validés, impact attendu nul » ;
  2. ajouter dans `LiveStatsAnalysisServiceTest` un test qui passe par `getMatchAnalysis`, avec un temps mort porteur de `scoredFor`, pour verrouiller l'alignement timeline/timeouts. Aujourd'hui, seul `LiveSetReplayTest#should_not_count_unknown_scoredFor_as_a_point` couvre ce choix, et uniquement pour `scoredFor` inconnu, pas pour un temps mort ou un malus porteur de `scoredFor`.
- **Question au développeur** : as-tu un élément (historique du front, données de prod) qui montre qu'aucun match antérieur à MV-001 n'est concerné ? Si oui, cite-le dans la note.

### CRIT-003 — `startScore(List)` public, devenu code mort et seconde implémentation du score de départ [MINEUR]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java:161`
- **Constat** : `getMatchAnalysis` utilise maintenant `replay.startMine()/startOpp()`. `startScore(List)` n'est plus appelé ni sous `src/main` ni sous `src/test` (grep `startScore(` : seules restent les lectures de l'accesseur du DTO). Pourtant, il recalcule toujours le départ de son côté avec `malusOf`.
- **Scénario de défaillance** : si on modifie demain la règle de départ dans `LiveSetReplay` (par exemple un malus plafonné), `startScore(List)` diverge en silence. Aucun test ne le détecte, et la javadoc de `buildTimeline` (`{@link #startScore}`) renvoie vers cette méthode comme si elle faisait référence. C'est précisément le risque de divergence que le ticket veut supprimer.
- **Correction proposée** : supprimer `startScore(List)`, ou le faire déléguer à `LiveSetReplay.of(events)`. Faire pointer les javadocs vers `LiveSetReplay`. `buildTimeline` et `buildTimeouts` ne sont plus appelés que par les tests. Les garder pour CA-6 se défend, mais il faut le dire dans leur javadoc (« point d'entrée de test, la production passe par `getMatchAnalysis` »).
- **Question au développeur** : pourquoi garder `startScore(List)`, puisqu'aucun test ne l'appelle ?

### CRIT-004 — Le test CA-9 « temps mort final actif » repose sur une donnée que le validateur rejette [MINEUR]
- **Localisation** : `src/test/java/vasconcelos/volleymatch/LiveSetReplayTest.java:67-77`
- **Constat** : `should_keep_trailing_timeout_active_even_when_malus_follows` place un malus (seq 3) **après** un point (seq 1). Le validateur l'interdit : la séquence d'un malus doit être inférieure à celle du premier event avec `scoredFor != null`. Le test vérifie donc la règle sur un état impossible. Aucun test de `LiveSetReplay` ne couvre le cas nominal « temps mort final actif » sans malus.
- **Scénario de défaillance** : si quelqu'un « durcit » demain le rejeu pour ignorer, voire lever une exception sur un malus placé après un point, ce test casse sans qu'il y ait de régression réelle. À l'inverse, le cas valide n'est pas verrouillé au niveau du composant. Il ne l'est qu'indirectement, par `LivePublicViewServiceTest#should_set_active_timeout_when_last_event_of_current_set_is_timeout`.
- **Correction proposée** : utiliser une séquence valide, `timeout(1,"opp"), malus(2,"mine",1)`. Le contrat l'autorise : « un temps mort ou une action neutre peut le précéder ». Ajouter aussi `attackPoint(1), timeout(2,"mine")` → `activeTimeout = "mine"`.
- **Question au développeur** : aucune.

### CRIT-005 — Doc analyse : l'ordre de `timeouts` n'est pas précisé [MINEUR]
- **Localisation** : `live-stats-analysis-api-doc.md:183-200`
- **Constat** : la phrase ajoutée (l.166) précise que `timeline` suit l'ordre de `sequence`. La section `timeouts`, elle, ne dit rien de son ordre, alors qu'il est désormais lui aussi celui de `sequence` (avant, c'était l'ordre de stockage).
- **Scénario de défaillance** : un front qui suppose `timeouts` trié par `afterPoint` est déjà servi, puisque les deux ordres coïncident. Le contrat écrit reste toutefois incomplet par rapport à ce que le ticket demande (« vérifier qu'elle dit ordre de `sequence` »).
- **Correction proposée** : ajouter l.200 « Dans l'ordre de `sequence` des events du set. »
- **Question au développeur** : aucune.
