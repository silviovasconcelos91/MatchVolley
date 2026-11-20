---
type: critic-review
round: 1
bloquants: 0
majeurs: 1
mineurs: 5
---
# Revue critique — MV-001 event `malus` (validation, overlay, analyse)

Périmètre : `git diff bd673c1` (src/main, src/test, docs racine) + `LiveScoreDto.java` (non suivi). Conventions : CLAUDE.md courant (scratchpad).

## Synthèse

Le chemin nominal est correct : j'ai rejoué CA-1 à CA-19 contre le code. L'overlay trie par `sequence`. L'analyse part de `startScore`. `afterPoint` passe sur un compteur dédié. `computeScopeStats` exclut les malus. Le validateur couvre les montants, la cohérence clé/catégorie, les champs nuls, le placement et l'unicité. Je n'ai trouvé aucun résultat faux sur une entrée produite par le front (`buildLiveStatsPayload` : `sequence = index + 1`, malus possible seulement si `history.length === 0`). Le seul constat MAJEUR est l'écart de CA-2 à CA-7 sur le POST, que le développeur a lui-même signalé. Le reste concerne des tests qui prouvent moins que ce qu'ils annoncent et des cas limites hors usage réel.

Fausse piste écartée (vérifiée) : je soupçonnais qu'un rollback casse la lecture des snapshots, puisque tous les events écrits après le déploiement portent `"malusAmount": null`. Ce n'est pas le cas pour `LiveStatsSnapshotConverter`. Dans `jackson-databind-3.1.5`, `FAIL_ON_UNKNOWN_PROPERTIES` vaut `false` par défaut (`javap -c` : `iconst_0`). Je n'en fais donc pas un constat.

## Avis sur les points signalés par le développeur

1. **POST non validé** → voir CRIT-001 (MAJEUR, arbitrage humain recommandé).
2. **Score de départ au lieu d'un décompte dans la boucle** → **justification acceptée**. Pour une donnée validée (malus de `sequence` strictement inférieure à tout event scorant), les deux approches sont équivalentes. Dans l'analyse, qui ne trie pas, le score de départ est même strictement plus robuste (test `should_deductMalus_in_timeline_when_events_are_not_in_sequence_order`). La javadoc de `malusOf` ne tient pas sur le chemin de l'analyse (« As a validated malus precedes… »), puisque ces données viennent du POST non validé. Je le rattache à CRIT-001 et ne l'ouvre pas comme constat séparé.
3. **Malus à `sequence` égale au premier point → 400** → **choix accepté**. Le front ne peut pas produire d'égalité (`i + 1`) et la doc dit « strictement inférieure ». En revanche, ce comportement n'est verrouillé par aucun test : voir CRIT-003.

---

### CRIT-001 — CA-2 à CA-7 non satisfaits sur `POST /matches/{id}/live-stats`, alors que l'analyse lit justement ces données [MAJEUR]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java:29-47` (aucun appel au validateur) ; `LiveStatsPayloadValidator.java:49-63` (`malusOf`) ; `LiveStatsAnalysisService.java:156-160`.
- **Constat** : le ticket définit CA-2 à CA-7 sur le validateur, « commun à `POST /matches/{id}/live-stats` et `PUT /live-sessions/{id}/snapshot` ». Dans le code, le validateur n'est appelé que par `LiveSessionService.java:95` (PUT). Le développeur l'a signalé honnêtement. Point qu'il n'a pas relevé : **l'analyse (CA-14 à CA-18) ne lit que des données issues du POST**. Les garanties sur lesquelles repose `malusOf`/`startScore` (montant ∈ {1,2}, un malus par équipe, malus avant le premier point) ne sont donc **jamais vérifiées** pour les données qu'elle traite.
- **Scénario de défaillance** :
  - POST avec un malus `mine` et `malusAmount: 3` (ou `-2`, ou deux malus `mine` de 2) → 201 au lieu du 400 attendu par CA-2/CA-7. Ensuite, `GET …/analysis` renvoie `startScore {myScore: -3}` (resp. `+2`, resp. `-4`) et une timeline qui ne retombe pas sur `scoreTeam`.
  - POST avec `category: "malus"` et `key: "attack_pt"` → 201 au lieu du 400 de CA-4. L'event est compté dans `actions.neutral` (`computeActions`, `default` → neutral), car `isMalus` ne regarde que la clé.
- **Preuve** : le code cité (pas de validation dans `saveLiveStats`), plus le témoignage du développeur, dont le premier IT « POST malus invalide → 400 » a reçu 201. Un test qui échouerait : `LiveStatsControllerIT#should_return400_when_post_malus_amount_is_three` (POST de `malusRequest` avec `malus(1, "mine", 3)`, attendu 400, obtenu 201).
- **Réponse à l'argument du développeur** (« brancher le validateur durcit le POST pour toutes les règles et risque un match non sauvegardé ») : l'argument est recevable mais surévalué. Le POST final et les PUT de snapshot sont construits par **la même fonction front** (`buildLiveStatsPayload`, `liveStatsPayload.ts`). Tout match diffusé en live a donc déjà vu un payload équivalent passer le validateur à chaque PUT. Le risque réel se limite aux matchs enregistrés **sans** session live, dont le payload n'a jamais été validé.
- **Correction proposée** : décision humaine entre deux options.
  - (a) Brancher `payloadValidator.validate(request)` en tête de `LiveStatsService.saveLiveStats`, avec un IT POST 400 (montant invalide) et un IT POST 201 de non-régression sur un payload réaliste sans live session.
  - (b) Réécrire CA-2 à CA-7 du ticket en « PUT snapshot uniquement », corriger la section « Problème » (le POST n'échouait pas), et retirer ou nuancer la phrase de la javadoc de `malusOf` (« As a validated malus… ») : sur le chemin de l'analyse, la donnée n'est pas validée.

  Dans les deux cas, `live-stats-api-contract.md` (§Règles dérivées « Malus ») doit dire si ces règles sont **imposées** par le serveur au POST ou seulement attendues.
- **Question au développeur** : sur le POST, existe-t-il un payload produit par le front actuel qui passe sans live session mais que le validateur rejetterait (par exemple un event adverse sans `jersey`) ? Si non, l'option (a) n'a pas de coût démontré.
- **Recommandation** : `ARBITRAGE` humain dès ce round. C'est un écart entre le ticket et l'existant, pas un défaut d'implémentation.

### CRIT-002 — Le test CA-4 « category malus avec une autre clé » passe sans le contrôle qu'il prétend tester [MINEUR]
- **Localisation** : `src/test/java/vasconcelos/volleymatch/LiveStatsPayloadValidatorTest.java` (`should_reject_when_malus_category_used_with_other_key`) ; `LiveStatsPayloadValidator.java:156-158` et `:170`.
- **Constat** : l'assertion est `hasMessageContaining("malus")`. Si on supprime le contrôle explicite (l. 156-158), l'event `{key: attack_pt, category: malus, player présent, scoredFor: null}` tombe dans le `default` du switch. Ce `default` lève `"action.category must be 'point', 'fault', 'neutral' or 'malus'"`, qui contient aussi « malus » : le test reste vert. Le choix « message clair avant les autres contrôles » de la note n'est donc pas verrouillé. Le message du `default` est par ailleurs trompeur : il annonce `malus` comme catégorie valide, alors qu'on ne l'atteint jamais avec `category == malus`.
- **Scénario de défaillance** : une régression qui supprime le contrôle explicite garde le 400 (CA-4 tient), mais le client reçoit un message qui l'invite à utiliser `malus`. Aucun test ne le détecte.
- **Preuve** : lecture du flux `validateEvent` (l. 149 → 153 → 156 → 159 → 163 → 166-170).
- **Correction proposée** : `hasMessageContaining("requires action.key 'malus'")`. Dans le `default`, revenir à `'point', 'fault' or 'neutral'` ou préciser « 'malus' (key malus only) ».
- **Question au développeur** : quel message le front affiche-t-il ou journalise-t-il sur un 400 ?

### CRIT-003 — Règle « `sequence` égale → 400 » non testée [MINEUR]
- **Localisation** : `LiveStatsPayloadValidator.java:127` (`>=`) ; note d'implémentation, point d'attention n°4.
- **Constat** : la note et la doc (`live-overlay-api-doc.md`, « strictement inférieure ») font de l'égalité un rejet assumé, mais aucun test ne couvre `malus(2, …)` avec `opponentPoint(2, …)`. Un passage de `>=` à `>` resterait vert.
- **Scénario de défaillance** : un refactor qui passe en `>`. Un malus et un point de même `sequence` sont alors acceptés et l'ordre devient indéterminé dans l'overlay (tri stable sur la position). Il n'y a pas d'effet sur le score, seulement un comportement contraire à la doc.
- **Correction proposée** : `should_reject_when_malus_sequence_equals_first_point_sequence`.
- **Question au développeur** : aucune, il suffit d'ajouter le test.

### CRIT-004 — Le volet « joueur » du test CA-16 est tautologique [MINEUR]
- **Localisation** : `LiveStatsAnalysisServiceTest#should_excludeMalus_from_team_set_and_player_actionStats`.
- **Constat** : la fixture `malus(...)` a `player == null`. `computePlayerAnalysis` filtre déjà sur `e.player() != null` (l. 215, 232). L'assertion `players().get(0).matchStats()` ne dépend donc pas du filtre `isMalus` ajouté dans `computeScopeStats`. Les volets équipe et set, eux, prouvent bien l'exclusion : sans le filtre, `malus` apparaîtrait dans `neutral`.
- **Scénario de défaillance** : un malus stocké avec un `player` (possible, car le POST n'est pas validé, voir CRIT-001) n'est couvert par aucun test au niveau joueur. Le code actuel l'exclut quand même (`computeScopeStats` est appelé au niveau joueur), donc l'impact n'est pas démontré.
- **Correction proposée** : ajouter un cas avec un malus `mine` portant `player.id = 3L`, et vérifier que la stat joueur ne contient que `attack_pt`. Sinon, renommer le test pour ne pas annoncer « player ».
- **Question au développeur** : aucune.

### CRIT-005 — Un malus stocké sans `malusAmount` est ignoré en silence : l'ordre de déploiement front/back compte [MINEUR]
- **Localisation** : `LiveStatsPayloadValidator.java:57` (`event.malusAmount() != null`) ; `LiveStatsAnalysisService.java:156-180`.
- **Constat** : avant ce déploiement, le backend ignore la propriété inconnue `malusAmount` (Jackson, `FAIL_ON_UNKNOWN_PROPERTIES=false`) et le POST n'est pas validé. Si le front `a962d34` (commit du 2026-11-20 sur `master` du front) est publié **avant** le backend, chaque match avec malus est stocké avec un event `malus` **sans montant**. Après le déploiement du backend, `malusOf` renvoie 0 pour ces matchs.
- **Scénario de défaillance** : un match enregistré dans cette fenêtre, malus -2 `mine` et 27-15 → `scoreTeam 25` (payload), mais `startScore {0,0}` et une dernière entrée de timeline à `27-15`. L'analyse se contredit sans erreur visible.
- **Preuve** : le raisonnement sur le code cité. Le front a été commité aujourd'hui, et rien ne prouve qu'il soit publié. D'où la gravité MINEUR.
- **Correction proposée** : déployer le backend avant le front (le noter dans la synthèse ou le ticket). Si une fenêtre a existé, on peut reconstituer le montant à partir de `pointsMarqués − scoreTeam` lorsque l'écart vaut 1 ou 2. Sinon, accepter le cas comme une donnée legacy.
- **Question au développeur** : le front avec l'event malus est-il déjà publié (store, OTA Expo) ?

### CRIT-006 — Sentinelle `Integer.MAX_VALUE` : un malus seul de `sequence = 2147483647` est rejeté à tort [MINEUR]
- **Localisation** : `LiveStatsPayloadValidator.java:112-128`.
- **Constat** : sans event scorant, `firstScoringSequence` vaut `Integer.MAX_VALUE`, et la comparaison `event.sequence() >= firstScoringSequence` rejette un malus de `sequence` `Integer.MAX_VALUE` avec le message « before the first point » dans un set **sans aucun point**.
- **Scénario de défaillance** : un set contenant uniquement `malus(2147483647, "mine", 2)` → 400 au lieu de 200. Le front ne peut pas produire ce cas (`i + 1`) : l'impact n'est pas démontré.
- **Correction proposée** : utiliser un `Integer firstScoringSequence = null` (ou `OptionalInt`) et ne comparer que s'il est présent.
- **Question au développeur** : aucune. Correctif d'une ligne, à faire si l'on touche de toute façon à cette méthode (CRIT-003).
