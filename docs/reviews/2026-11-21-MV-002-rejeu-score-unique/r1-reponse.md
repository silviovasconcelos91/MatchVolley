# Réponse développeur — round 1
| ID | Position | Résumé |
|----|----------|--------|
| CRIT-001 | CORRIGÉ | Comportement conservé (décision Q1). La note parle désormais d'un « état impossible avec des données validées » et non plus d'un « bug existant ». |
| CRIT-002 | CORRIGÉ | Justification corrigée : le POST d'analyse n'est validé que depuis 8712564. Risque ajouté au ticket et test `getMatchAnalysis` avec un temps mort porteur de `scoredFor`. |
| CRIT-003 | CORRIGÉ | `startScore(List)` supprimée (aucun appelant). Javadocs redirigées vers `LiveSetReplay`, et `buildTimeline`/`buildTimeouts` présentées comme points d'entrée de test. |
| CRIT-004 | CORRIGÉ | Test refait avec un scénario valide (malus avant le premier point) et cas sans malus ajouté. |
| CRIT-005 | CORRIGÉ | La doc d'analyse précise que `timeouts` suit l'ordre de `sequence`. |
| ARCH-001 | CORRIGÉ | Même correctif que CRIT-003. |
| PERF-001 | CONTESTÉ | Pas de correction : comportement antérieur, coût chiffré négligeable par le relecteur, hors périmètre. |
| PERF-002 | CONTESTÉ | Pas de correction : environ 15 Ko de durée de vie de quelques µs, et le relecteur ne demande rien. |

## CRIT-001 — CORRIGÉ
- **Correctif** :
  - Code inchangé, conformément à la décision Q1 de l'orchestrateur : `LivePublicViewService.java:126` compte le point sans `sequence` dans le score, mais ne le met pas dans `lastPoints`.
  - Note `00-implementation.md` (Choix de conception, puis « Note sur CA-4 ») : « bug existant » est remplacé par « NPE de l'ancien overlay sur un état impossible avec des données validées (snapshot toujours validé, `sequence >= 1`, depuis la création des sessions live) ». La décision Q1 y est mentionnée.
- **Test** : `LivePublicViewServiceTest#should_replay_null_sequence_events_last_in_stored_order_when_legacy_payload`, inchangé.
- Je ne retiens pas l'option d'ajouter une phrase dans `live-overlay-api-doc.md` (facultative selon l'architecte) : le cas ne peut pas se produire, et le documenter ferait croire au front qu'il peut arriver.

## CRIT-002 — CORRIGÉ
- **Correctif** :
  - `00-implementation.md` (Choix de conception, politique `scoredFor`, et Points d'attention) : la phrase « rejetés sur les deux chemins d'écriture » était fausse. Elle devient : « le snapshot est toujours validé, le POST d'analyse seulement depuis 8712564 ». Le changement de timeline pour les matchs antérieurs est maintenant explicite et assumé.
  - `docs/tickets/MV-002-rejeu-score-unique.md:77` (section Risques) : ajout du risque. Les analyses enregistrées avant 8712564 peuvent voir leur timeline perdre des entrées (temps mort ou malus porteur d'un `scoredFor`, ou `scoredFor` inconnu). Changement assumé, timeline et `timeouts` désormais alignés, impact attendu nul mais non prouvé.
- **Test** : `LiveStatsAnalysisServiceTest#should_align_timeline_and_timeouts_when_unvalidated_timeout_carries_scoredFor` (l.487), via `getMatchAnalysis`. Événements : point `mine` (1), temps mort `mine` avec `scoredFor: "mine"` (2), point `opp` (3).
  - Timeline obtenue : `(1,0), (1,1)`. Temps mort : `afterPoint` 1, score `(1,0)`.
  - Sur l'ancien code (déroulé à la main, pas exécuté), la timeline aurait été `(1,0), (2,0), (2,1)`, contre un `afterPoint` de 1 pour le temps mort. C'est exactement le désalignement que le relecteur signale.
- **Question du relecteur** (preuve qu'aucun match antérieur n'est concerné) : je n'en ai pas, faute d'accès aux données de prod. La note et le ticket le disent explicitement. Le seul argument est indirect : le front ne produit pas ces valeurs.

## CRIT-003 — CORRIGÉ
- **Correctif** : `LiveStatsAnalysisService.java`
  - méthode `startScore(List)` supprimée. Avant suppression, `grep -rn "startScore(" src` ne montrait que sa déclaration, en dehors des accesseurs du DTO ;
  - javadoc de `buildTimeline` (l.158-161) et de `buildTimeouts` (l.166-171) : `{@link #startScore}` est remplacé par le score de départ du rejeu (`{@link LiveSetReplay#startMine()}` / `{@link LiveSetReplay}`), avec la mention « Single-set entry point kept for unit tests: production goes through {@link #getMatchAnalysis} ».
- **Test** : pas de nouveau test. Le score de départ n'a plus qu'une source, `LiveSetReplay`, déjà couverte par `LiveSetReplayTest#should_start_and_end_at_negative_score_without_point_when_malus_alone` et par le test croisé `LiveScoreReplayConsistencyTest` (départ de l'overlay = `startScore` de l'analyse). Vérification : `grep -rn "startScore(List\|#startScore" src/main` ne renvoie rien.
- **Réponse à la question** (« pourquoi garder `startScore(List)` ? ») : il n'y avait aucune bonne raison. Je l'avais gardée par réflexe CA-6, alors qu'aucun test ne l'appelait.

## CRIT-004 — CORRIGÉ
- **Correctif** : `LiveSetReplayTest.java:66-89`. Le test `should_keep_trailing_timeout_active_even_when_malus_follows`, qui reposait sur un malus après un point (rejeté par le validateur), est remplacé par deux tests :
  - `should_keep_trailing_timeout_active_when_last_event_is_timeout` (l.67) : cas nominal sans malus, `attackPoint(1)` puis `timeout(2,"mine")`. Résultat : `activeTimeout = "mine"`, temps mort `afterPoint` 1, score `(1,0)` ;
  - `should_keep_timeout_active_when_malus_follows_it_before_first_point` (l.79) : séquence valide `timeout(1,"opp")` puis `malus(2,"mine",1)`. Résultat : `activeTimeout = "opp"`, temps mort `afterPoint` 0, score `(-1,0)`.
- **Test** : ces deux tests. La note (tableau CA-9) est mise à jour. Le test remplacé avait été créé dans ce ticket : aucun test antérieur n'est touché, et CA-6 reste respecté (`git diff -U0 73e5c66 -- src/test` ne contient aucune ligne `-`).

## CRIT-005 — CORRIGÉ
- **Correctif** : `live-stats-analysis-api-doc.md:200`, ajout de « Dans l'ordre de `sequence` des events du set (quel que soit leur ordre de stockage). » dans la section `timeouts`.
- **Test** : sans objet (documentation). Le comportement est couvert par `LiveStatsAnalysisServiceTest#should_placeTimeout_by_sequence_when_events_stored_unordered`.

## ARCH-001 — CORRIGÉ
- **Correctif** : identique à CRIT-003. Les critères de validation de l'architecte sont remplis :
  - `grep -n "startScore(List\|#startScore" .../LiveStatsAnalysisService.java` est vide ;
  - `LiveStatsAnalysisServiceTest` (36) et `LiveScoreReplayConsistencyTest` (1) sont verts.
- **Test** : voir CRIT-003.

## PERF-001 — CONTESTÉ
- **Argument** : la boucle de mapping existait avant MV-002 sous la même forme, et ce diff ne l'aggrave pas. Le relecteur chiffre lui-même le coût : environ 900 allocations par seconde à 2 polls/s, soit quelques dizaines de Ko/s, qu'il qualifie de « négligeable ». Optimiser en ne mappant que les 5 derniers points obligerait à conserver un état entre sets, pour un gain non mesurable. Ce serait aussi une modification du chemin public hors du périmètre d'une refactorisation pure (ticket, Hors périmètre).
- **Preuve** : `r1-perf.md`, PERF-001, sections « Chiffrage » et « Correction proposée : optionnelle […] Je ne l'exige pas ». Ancien code (`git show 73e5c66:…/LivePublicViewService.java`, l.153) : `allPoints.add(toPoint(...))` pour chaque point.

## PERF-002 — CONTESTÉ
- **Argument** : ce coût a été choisi en connaissance de cause. Garder les rejeux jusqu'à `topScorers` est précisément ce qui supprime le deuxième tri par set, l'objectif perf du ticket. Le relecteur chiffre ce maintien à environ 10-15 Ko pendant quelques microsecondes et ne demande pas de correction.
- **Preuve** : `r1-perf.md`, PERF-002 : « Correction proposée : aucune. » Bilan du chemin chaud du même rapport : un tri et deux copies en moins par set.

## Hors identifiants
- **ARCH-D01** (poste par set dépendant de l'ordre de stockage) : c'est une dette que j'avais signalée moi-même. Elle est explicitement hors périmètre (le ticket exclut `computePlayerAnalysis`). Je propose un ticket de dette séparé, comme le recommande l'architecte. Je ne l'ai pas corrigée ici.
- **Q1** : arbitrée par l'orchestrateur (option 1, comportement conservé), voir CRIT-001.

## Conflits entre relecteurs
Aucun. CRIT-003 et ARCH-001 demandent la même chose. Les constats perf sont informatifs et ne s'opposent à aucune correction : supprimer `startScore(List)` retire même un calcul.

## Résultat `./mvnw clean test`
`Tests run: 325, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`. Les IT sont inclus : `PublicLiveControllerIT` 18, `LiveStatsControllerIT` 27. Côté unitaires : `LiveSetReplayTest` 11, `LiveStatsAnalysisServiceTest` 36, `LivePublicViewServiceTest` 29, `LiveScoreReplayConsistencyTest` 1.
