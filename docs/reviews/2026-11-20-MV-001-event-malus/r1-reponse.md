# Réponse développeur — round 1
| ID | Position | Résumé |
|----|----------|--------|
| CRIT-001 | CORRIGÉ | Validateur branché en tête de `LiveStatsService.saveLiveStats` (arbitrage utilisateur Q4). 6 IT POST → 400 couvrent CA-2 à CA-7, plus un test unitaire « rien n'est persisté ». Doc et ticket corrigés |
| CRIT-002 | CORRIGÉ | Le test CA-4 vérifie le message exact du contrôle explicite. Le message du `default` ne propose plus `malus` |
| CRIT-003 | CORRIGÉ | Test `sequence` égale → 400 |
| CRIT-004 | CORRIGÉ | Test renommé en ce qu'il prouve (équipe et set). Nouveau test joueur avec un malus portant un `player` |
| CRIT-005 | CONTESTÉ | Aucune fenêtre de données : le front malus n'est pas publié (commit non poussé). L'ordre de déploiement est inscrit dans les risques du ticket |
| CRIT-006 | CORRIGÉ | Sentinelle `Integer.MAX_VALUE` remplacée par un `Integer` nullable, avec test |
| ARCH-001 | CORRIGÉ | Même correctif que CRIT-001, selon l'option A recommandée |
| ARCH-002 | CORRIGÉ | Extraction de `LiveEventRules` (`MINE`, `OPP`, `TIMEOUT_KEY`, `MALUS_KEY`, `isTimeout`, `isMalus`, `malusOf`), avec `LiveEventRulesTest` |
| PERF-001 | CONTESTÉ | Le relecteur ne demande aucune correction. Son chiffrage (quelques µs par poll, O(n)) est accepté tel quel |

## CRIT-001 — CORRIGÉ
- **Correctif** :
  - `src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java:28` : injection de `LiveStatsPayloadValidator`.
  - `LiveStatsService.java:35` : `payloadValidator.validate(request)` en **première instruction** de `saveLiveStats`, avant `existsById` et toute persistance. Même modèle que `LiveSessionService.java:95`. La logique reste dans le service et le controller n'est pas touché.
  - L'erreur passe par `BadRequestException`, déjà mappée en 400 dans l'enveloppe `ApiResponse` (`GlobalExceptionHandler.java:72-75`).
- **Tests** :
  - `LiveStatsServiceTest#should_reject_and_persist_nothing_when_payload_is_invalid` : `BadRequestException`, `matchRepository.save`, `liveSessionRepository.save` et `attachMatch` jamais appelés. Le validateur est un vrai `@Spy`, pas un mock. Les 6 tests existants passent avec lui, donc leurs payloads sont valides.
  - `LiveStatsControllerIT` (POST de bout en bout). Chaque cas vérifie le 400, l'enveloppe `status: 400`, le message et l'**absence du match** en base :
    - CA-2 : `#should_return400_when_posted_malus_amount_is_invalid` (montants 3, 0 et null) ;
    - CA-3 : `#should_return400_when_posted_non_malus_event_has_malus_amount` ;
    - CA-4 : `#should_return400_when_posted_malus_key_and_category_mismatch` (les deux sens) ;
    - CA-5 : `#should_return400_when_posted_malus_has_player` ;
    - CA-6 : `#should_return400_when_posted_malus_comes_after_first_point` ;
    - CA-7 : `#should_return400_when_posted_set_has_two_malus_for_same_team` (rejet) et `#should_return201_when_posted_set_has_one_malus_per_team` (acceptation).
  - Mutation : avec la ligne `LiveStatsService.java:35` neutralisée, **7 tests échouent** (les 6 IT 400 et le test unitaire). Le fichier a été restauré.
- **Fixtures des autres IT** : seuls `LiveStatsControllerIT` et `LiveSessionControllerIT` POSTent des live-stats (`grep -l "live-stats" src/test/**/*.java`). `DataIsolationIT` et `PlayerControllerIT` n'en postent pas. Toutes les fixtures POST existantes restent vertes : `buildValidRequest`, `malusRequest`, le JSON brut legacy de `#should_return201_and_null_malus_amount_when_payload_has_no_malus_field`, et `simpleSnapshot` dans `LiveSessionControllerIT` (l. 303-335).
- **Réponse à la question du critic** (« un payload du front actuel qui passerait sans session live mais serait rejeté ? ») : non, d'après le code front. `buildLiveStatsPayload` (`MatchVolleyFront/src/data/liveStatsPayload.ts`) sert aux deux endpoints, et :
  - `jersey` est typé `number` non nul pour tout event non-équipe ;
  - `player` vaut null seulement si `isTeamEvent` (temps mort, malus) ;
  - `scoredFor` est dérivé de la définition de l'action ;
  - `sequence` vaut `i + 1` ;
  - `wonBy` vaut `mine`, `opp` ou `null`.
  
  Il reste deux risques non vérifiables d'ici : une valeur `undefined` au runtime malgré le typage TS, et un `venue` hors liste. Le second est déjà rejeté au PUT.
- **Docs** :
  - `live-stats-api-contract.md:15-20` : tableau des codes du POST, dont `400` (« mêmes règles que le PUT snapshot, rien n'est enregistré »).
  - `live-stats-api-contract.md:262` : les règles du malus sont « imposées par le serveur ».
  - `live-overlay-api-doc.md:274` (§5 Soumission finale) : le POST applique les mêmes règles.
- **Ticket** (`docs/tickets/MV-001-event-malus-live-stats.md`) :
  - l. 26 : la phrase « le POST final échoue » est corrigée et annotée (elle était fausse : le POST acceptait en 201 et l'analyse comptait 27-15) ;
  - l. 33 : précise que le branchement sur le POST est ajouté par ce ticket ;
  - l. 107 : nouvelle décision Q4.
- **Javadoc de `malusOf`** : la remarque du critic (« As a validated malus… » faux sur le chemin de l'analyse) est levée, puisque les deux chemins d'écriture sont désormais validés. La javadoc le dit explicitement (`LiveEventRules.java`, `malusOf`).

## CRIT-002 — CORRIGÉ
- **Correctif** : `LiveStatsPayloadValidator.java:140`. Le `default` du switch revient à `'point', 'fault' or 'neutral'`. Une catégorie `malus` n'y arrive jamais, car elle est traitée l. 127. Annoncer `malus` comme catégorie valide était donc trompeur.
- **Test** : `LiveStatsPayloadValidatorTest#should_reject_when_malus_category_used_with_other_key` (l. 208) vérifie désormais `"action.category 'malus' requires action.key 'malus'"`. Supprimer le contrôle explicite ferait tomber l'event dans le `default`, dont le message ne contient plus cette chaîne : le test échouerait. La version POST le vérifie aussi (`LiveStatsControllerIT#should_return400_when_posted_malus_key_and_category_mismatch`).
- **Question du critic** (message affiché par le front sur un 400) : hors du code backend. Le message est en anglais technique, comme tous les messages du validateur existant (cf. timeout, bd673c1).

## CRIT-003 — CORRIGÉ
- **Correctif** : aucun changement de comportement (`>=`, `LiveStatsPayloadValidator.java:97`). Le choix est maintenant verrouillé par un test.
- **Test** : `LiveStatsPayloadValidatorTest#should_reject_when_malus_sequence_equals_first_point_sequence` (l. 265) rejette un malus de `sequence` 2 face à un point de `sequence` 2. Un passage à `>` le ferait échouer.

## CRIT-004 — CORRIGÉ
- **Correctif** : `LiveStatsAnalysisServiceTest`. L'ancien test est renommé `should_excludeMalus_from_team_and_set_actionStats` (l. 334) : son volet joueur était effectivement tautologique, puisque `player == null` est déjà filtré par `computePlayerAnalysis`.
- **Test** : `LiveStatsAnalysisServiceTest#should_excludeMalus_from_player_actionStats_when_malus_carries_a_player` (l. 351). Un malus `mine` porte `player.id = 3`, le même joueur marque un `attack_pt`. Le joueur apparaît bien, mais ses `matchStats` et ses `setStats[0].stats` ne contiennent que `attack_pt`. Ce cas est désormais impossible en écriture (CA-5 sur PUT et POST). Le test documente la robustesse de `computeScopeStats` au niveau joueur.

## CRIT-005 — CONTESTÉ
- **Argument** : le scénario suppose que le front malus ait été publié **avant** ce backend, ce qui aurait créé une fenêtre où des malus sont stockés sans `malusAmount`. Cette fenêtre n'existe pas et ne peut pas exister si l'ordre de déploiement est respecté. Aucune donnée à reconstituer, donc aucun code de reprise (`pointsMarqués − scoreTeam`) à écrire.
- **Preuve** :
  - Le commit front `a962d34` (« feat(live): send malus as a live stats event ») n'est pas poussé : `git status -sb` dans `MatchVolleyFront` donne `## master...origin/master [ahead 2]` (`a962d34` et `6e0f6ee`). Le coordinateur confirme qu'il attend explicitement le déploiement du backend.
  - L'ancien backend acceptait le POST non validé, mais aucun client n'émettait d'event malus avant ce commit. `MALUS_ACTION` n'existe que dans `a962d34`.
- **Ce que je retiens du constat** : l'ordre de déploiement est une vraie contrainte. Je l'ai inscrite dans les Risques du ticket (`docs/tickets/MV-001-event-malus-live-stats.md:114`) : « déployer ce backend avant le front ».

## CRIT-006 — CORRIGÉ
- **Correctif** : `LiveStatsPayloadValidator.java:81-97`. `Integer firstScoringSequence = null`, et la comparaison n'a lieu que s'il existe un event scorant.
- **Test** : `LiveStatsPayloadValidatorTest#should_accept_when_malus_has_max_sequence_in_set_without_point` (l. 274) accepte un temps mort suivi de `malus(Integer.MAX_VALUE, …)` dans un set sans point. Avec l'ancienne sentinelle, ce cas était rejeté.

## ARCH-001 — CORRIGÉ
- **Correctif et tests** : voir CRIT-001. Les cinq points de la « Correction attendue » sont traités :
  1. le service est branché, le controller n'est pas touché ;
  2. les IT POST 400 couvrent le montant invalide (avec vérification de l'absence du match) et le double malus, plus les CA-3 à CA-6 ;
  3. les fixtures des autres IT sont vérifiées ;
  4. le contrat est documenté ;
  5. le ticket est corrigé.
- **Critère de validation** : `grep -n "payloadValidator.validate" src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java` renvoie `35:        payloadValidator.validate(request);`. La suite `-Dtest='*Test,*IT'` est verte (voir plus bas).

## ARCH-002 — CORRIGÉ
- **Choix** : je refactorise plutôt que de contester. Le coût est faible : trois appelants dans le même package, aucun changement de comportement. Et la correction de ARCH-001 renforce l'argument : `malusOf` repose sur une garantie de validation qui couvre désormais les deux chemins d'écriture. C'est de la sémantique d'event partagée, pas de la validation.
- **Correctif** :
  - nouveau `src/main/java/vasconcelos/volleymatch/service/LiveEventRules.java` (`public final`, constructeur privé), qui contient `MINE`, `OPP`, `TIMEOUT_KEY`, `MALUS_KEY`, `isTimeout`, `isMalus` et `malusOf` ;
  - `LiveStatsPayloadValidator.java:15-18` les importe en statique et ne garde que des constantes privées de validation (`TEAMS`, `VENUES`, `MAX_SETS`, `NEUTRAL`, `MALUS_CATEGORY`, `MALUS_AMOUNTS`) ;
  - `LivePublicViewService.java:30-31,117-133` et `LiveStatsAnalysisService.java:76-77,158-159,194` utilisent `LiveEventRules`.
- **Visibilité** : la classe est `public`, et non package-private comme l'étaient les helpers. Cela permet un test unitaire direct depuis le package de tests (`vasconcelos.volleymatch`), conformément à la consigne « déplacer les tests vers `LiveEventRulesTest` ». Il s'agit de fonctions pures sans état, sans risque à les exposer.
- **Test** : `LiveEventRulesTest` (3 tests) couvre l'identification par clé (y compris `null`), la somme de `malusOf` par équipe, et le cas sans events ou sans malus.
- **Critère de validation** : `grep -rn "LiveStatsPayloadValidator\.\(isMalus\|isTimeout\|malusOf\|MINE\|OPP\)" src` ne renvoie rien (exit 1).
- **Non traité** (conformément à la consigne) : ARCH-D01 (rejeu unique du score) et ARCH-D02 (IT et CI), qui feront l'objet de tickets séparés. `pom.xml` n'est pas touché.

## PERF-001 — CONTESTÉ
- **Argument** : aucune correction n'est demandée. Le relecteur chiffre lui-même le coût (de l'ordre de la µs à la dizaine de µs par poll, O(n), pas d'allocation) et qualifie l'optimisation de prématurée. Calculer `startScore` une seule fois changerait la signature des méthodes publiques `buildTimeline`/`buildTimeouts`, sans gain mesurable.
- **Preuve** :
  - `LiveEventRules.malusOf` est une boucle `for` sans allocation ;
  - le chemin chaud (overlay) fait 2 passes par set, soit environ 1000 comparaisons de `String` par poll pour 5 sets de 100 events ;
  - l'analyse et le POST ne sont pas des chemins chauds (un appel par consultation ou par match).
  
  Le rejeu unique prévu par ARCH-D01 supprimera naturellement ces passes en double.

## Conflits entre relecteurs
- **ARCH-001/CRIT-001 (une validation de plus sur le POST) face à PERF** : la validation ajoute 3 à 4 passes O(n) sur environ 300 events, **une seule fois par match** (soumission finale), sur un endpoint non chaud. C'est négligeable selon le propre barème de PERF-001. Pas de conflit réel.
- **ARCH-002 (extraction) face à ARCH-D01 (rejeu unique)** : ils ne se contredisent pas. `LiveEventRules` est la future maison naturelle de `LiveSetReplay` (D01).

## Résultat `./mvnw test`
- Commande : `./mvnw test '-Dtest=*Test,*IT'`. Le `./mvnw test` nu n'exécute pas les `*IT` (dette ARCH-D02, ticket séparé).
- `[INFO] Tests run: 261, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` (247 au round 0, +14 : 7 IT POST, 1 unitaire service, 2 validateur, 1 analyse, 3 `LiveEventRulesTest`).
