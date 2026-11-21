# Implémentation — MV-002 Unifier le rejeu du score d'un set entre l'overlay et l'analyse

## Intention
Le score d'un set était rejoué à trois endroits (overlay `computeState`, analyse `buildTimeline` et `buildTimeouts`) avec deux politiques d'ordre. Il faut une seule implémentation, triée par `sequence` (nulls en dernier, tri stable), utilisée par l'overlay et l'analyse. Le contrat JSON ne change pas. Le seul changement de comportement voulu est que l'analyse suit désormais l'ordre de `sequence`. Les littéraux `"mine"`/`"opp"` de l'analyse sont remplacés par `LiveEventRules.MINE`/`OPP`.

## Choix de conception
- **`LiveSetReplay` dans `service/`, à côté de `LiveEventRules`, sous forme de classe finale immuable avec une fabrique statique `of(events)`** — **pourquoi** : c'est la piste de l'architecte (`r2-architect.md`, « Dette restante »). Le rejeu est un calcul pur sur des DTO : il n'a ni dépendance ni état, et un bean Spring n'apporterait rien. Il ne passe pas par la réflexion, donc aucun hint natif n'est nécessaire (comme `LiveEventRules`). J'ai écarté un `record` public, parce que son constructeur canonique laisserait n'importe quel appelant fabriquer un rejeu incohérent (points sans rapport avec les events). J'ai aussi écarté un visiteur ou des callbacks : l'overlay et l'analyse consomment les mêmes trois choses (points avec score après, temps morts positionnés, score de départ), et un résultat matérialisé est plus simple à tester (CA-9).
- **Le rejeu produit `points()` (event + score après) et `timeouts()` (event, `afterPoint`, score à l'appel)**, plus `startMine/startOpp`, `mine/opp` final, `lastScorer`, `activeTimeout` et `events()` triés — **pourquoi** : c'est exactement ce dont les deux consommateurs ont besoin. Les DTO de sortie (`ScoreDto`, `LiveScoreDto`, `TimelineEntryDto`, `LiveTimeoutDto`, `PublicPointDto`) restent construits par chaque service. Le rejeu ne dépend d'aucun DTO de sortie, ce qui évite de coupler l'overlay public et l'analyse privée.
- **Un seul tri par set et par appel, sur les deux chemins** — **pourquoi** : c'est l'exigence perf du ticket.
  - Overlay : avant, il y avait **deux** tris par set et par appel (`orderedEvents` dans la boucle de `computeState`, puis à nouveau dans `topScorers`). Il n'en reste plus qu'un : `topScorers` parcourt maintenant `replay.events()` (`LivePublicViewService.java:187-190`). Aucune requête SQL n'est ajoutée.
  - Analyse : `getMatchAnalysis` fait un seul `LiveSetReplay.of` par set, qui alimente timeline, temps morts et `startScore` (`LiveStatsAnalysisService.java:59`). Ça remplace trois parcours qui recalculaient chacun le malus.
  - Le tri est un `ArrayList.sort` (TimSort, stable, O(n) si la liste est déjà triée, ce qui est le cas nominal).
- **Les méthodes publiques `buildTimeline(List)` et `buildTimeouts(List)` sont gardées ; `startScore(List)` est supprimée** — **pourquoi** :
  - les tests existants appellent `buildTimeline`/`buildTimeouts`, et CA-6 interdit de modifier leurs assertions. Elles délèguent au rejeu, et leur javadoc les présente comme des points d'entrée de test (la production passe par `getMatchAnalysis`) ;
  - `startScore(List)` n'avait plus aucun appelant après la refacto. La garder laissait un second calcul public du score de départ (r1, CRIT-003/ARCH-001).
- **Un « point » est un event dont `scoredFor` vaut `mine` ou `opp`. Toute autre valeur non nulle n'est pas un point** — **pourquoi** : il fallait choisir une seule politique là où les anciennes boucles divergeaient sur des données invalides. L'overlay comptait toute valeur ≠ `mine` comme un point `opp`. La timeline de l'analyse ajoutait une entrée sans incrémenter le score. `buildTimeouts` l'ignorait. J'ai retenu la politique de `buildTimeouts`, la seule qui n'invente pas de point. Le validateur rejette ces valeurs (`scoredFor` doit valoir `team` ou null, et `team` ∈ {mine, opp}). Mais le POST d'analyse n'est validé **que depuis 8712564 (MV-001)**. Pour les matchs enregistrés avant, la timeline peut donc changer dans ces cas : un temps mort ou un malus porteur d'un `scoredFor`, ou un `scoredFor` inconnu, ne crée plus d'entrée. Avant, ces cas créaient une entrée, mais n'étaient pas comptés dans `afterPoint`. Le changement est assumé et le risque est noté dans le ticket (r1, CRIT-002). Je n'ai aucune preuve qu'aucun match ne soit concerné, faute d'accès aux données de prod ; l'impact attendu est nul, puisque le front n'émet pas de telles valeurs. Tests : `LiveSetReplayTest#should_not_count_unknown_scoredFor_as_a_point` et `LiveStatsAnalysisServiceTest#should_align_timeline_and_timeouts_when_unvalidated_timeout_carries_scoredFor`.
- **Un event marquant dont la `sequence` est null compte dans le score et la trajectoire, mais n'apparaît pas dans `lastPoints`** (`LivePublicViewService.java:126`) — **pourquoi** : le test écrit pour CA-4 montre que l'ancien overlay levait une `NullPointerException` dans ce cas (`PublicPointDto.sequence` est un `int`), alors que CA-4 exige « sans exception ». Ce n'est pas un bug atteignable : c'est un **état impossible avec des données validées**, puisque le snapshot n'est écrit qu'après une validation qui exige `sequence >= 1`, et ce depuis la création des sessions live (r1, CRIT-001). L'orchestrateur a confirmé ce comportement (r1, Q1). J'ai écarté deux alternatives :
  - passer `sequence` en `Integer` : c'est un changement de contrat (le champ deviendrait nullable), hors périmètre ;
  - remplacer null par 0 : ça fabrique une valeur fausse.

  Le score reste juste, et seule la liste des derniers points omet un point qu'elle ne sait pas identifier. En pratique, le cas est inatteignable : le PUT de snapshot est validé depuis la création des sessions live (`76f5285`, `LiveSessionService.java:95`), et les sessions expirent après 12 h ou 24 h.
- **La doc `live-stats-analysis-api-doc.md` gagne une phrase** : « une entrée par point, dans l'ordre de `sequence` ». La doc ne précisait aucun ordre, et le ticket demande de le vérifier. La forme du contrat ne change pas.

## Fichiers
- `src/main/java/vasconcelos/volleymatch/service/LiveSetReplay.java` — **nouveau**. Seule implémentation du rejeu : tri par `sequence` (stable, nulls en dernier), score de départ malus déduit, cumul des points, position des temps morts, temps mort actif.
- `src/main/java/vasconcelos/volleymatch/service/LivePublicViewService.java` — `computeState` consomme le rejeu. `orderedEvents` est supprimé. `topScorers` réutilise les events triés. Ajout de `timeoutCounts`. Les points sans `sequence` sont exclus de `lastPoints`.
- `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java` — timeline, temps morts et `startScore` viennent du rejeu (un par set dans `getMatchAnalysis`) ; méthode `startScore(List)` supprimée. Tous les littéraux `"mine"`/`"opp"` sont remplacés par `MINE`/`OPP`.
- `src/main/java/vasconcelos/volleymatch/service/LiveEventRules.java` — javadoc uniquement (renvoi vers `LiveSetReplay`).
- `live-stats-analysis-api-doc.md` — précision sur l'ordre de `timeline`.
- `src/test/java/vasconcelos/volleymatch/LiveSetReplayTest.java` — **nouveau**, 11 tests (CA-9).
- `src/test/java/vasconcelos/volleymatch/LiveScoreReplayConsistencyTest.java` — **nouveau**, test croisé overlay/analysis (CA-3).
- `src/test/java/vasconcelos/volleymatch/LivePublicViewServiceTest.java` — 4 tests ajoutés (caractérisation et CA-4/CA-5). Aucune assertion existante n'est modifiée.
- `src/test/java/vasconcelos/volleymatch/LiveStatsAnalysisServiceTest.java` — 6 tests ajoutés (caractérisation et CA-1/2/4/5). Aucune assertion existante n'est modifiée.

## Critères d'acceptation
| CA | Test(s) qui le prouvent |
|----|-------------------------|
| CA-1 | `LiveStatsAnalysisServiceTest#should_buildTimeline_in_sequence_order_when_events_stored_unordered` (stockés 3, 1, 2, via `getMatchAnalysis`) |
| CA-2 | `LiveStatsAnalysisServiceTest#should_placeTimeout_by_sequence_when_events_stored_unordered` |
| CA-3 | `LiveScoreReplayConsistencyTest#should_give_same_score_progression_in_overlay_and_analysis_when_set_is_stored_unordered` (malus des deux équipes, 2 temps morts dont un final, neutre, points et fautes des deux camps, 9 events mélangés) |
| CA-4 | `LiveSetReplayTest#should_replay_null_sequences_last_in_stored_order_and_keep_ties_stable`, `LiveStatsAnalysisServiceTest#should_replay_null_sequence_events_last_when_legacy_events`, `LivePublicViewServiceTest#should_replay_null_sequence_events_last_in_stored_order_when_legacy_payload` |
| CA-5 | `LiveSetReplayTest#should_replay_null_sequences_last_in_stored_order_and_keep_ties_stable`, `LiveStatsAnalysisServiceTest#should_keep_stored_relative_order_when_events_share_a_sequence`, `LivePublicViewServiceTest#should_keep_stored_relative_order_when_two_events_share_a_sequence` |
| CA-6 | Aucune ligne existante supprimée ou modifiée sous `src/test` (`git diff -U0 src/test` sans ligne `-`). `LivePublicViewServiceTest` (29), `LiveStatsAnalysisServiceTest` (36), `PublicLiveControllerIT` (18) et `LiveStatsControllerIT` (27) sont verts. Aucun DTO n'est modifié. |
| CA-7 | Vérifié par grep, pas par un test : `grep -rn "scoredFor()" src/main/java/vasconcelos/volleymatch/service` → incréments uniquement dans `LiveSetReplay.java:82,84`. Les autres occurrences sont `LiveSetReplay.java:138` (lecture de `lastScorer`), `LivePublicViewService.java:171` (copie du champ dans `PublicPointDto`, pas un cumul) et le validateur. |
| CA-8 | Vérifié par grep, pas par un test : `grep -n '"mine"\|"opp"' .../LiveStatsAnalysisService.java` → vide. |
| CA-9 | `LiveSetReplayTest` : set vide (`should_return_zero_score_and_nothing_when_set_is_empty`), `events` null (`..._when_events_are_null`), malus seul (`should_start_and_end_at_negative_score_without_point_when_malus_alone`), malus puis points (`should_count_points_from_malus_score_when_malus_then_points`), temps mort final actif (`should_keep_trailing_timeout_active_when_last_event_is_timeout`, et suivi d'un malus valide, `should_keep_timeout_active_when_malus_follows_it_before_first_point`), temps mort suivi d'un point (`should_end_timeout_and_position_it_when_point_follows`), désordre (`should_replay_by_sequence_when_events_are_stored_unordered`) |

Note sur CA-4 : le CA dit « même comportement que `orderedEvents` aujourd'hui ». C'est vrai pour l'ordre (nullsLast). En revanche, l'ancien overlay levait une NPE pour un *point* sans `sequence`, sur un état impossible avec des données validées. J'ai lu « sans exception » comme l'exigence principale, et ce point est donc exclu de `lastPoints` (voir Choix de conception). Arbitré en r1 (Q1) : on garde ce comportement.

## Tests
- **Caractérisation, verte avant et après la refacto :**
  - `LiveStatsAnalysisServiceTest#should_exposeFullTimelineAndTimeouts_when_orderedSetWithMalusTimeoutAndNeutral` : contenu complet de la timeline (playerId, action, occurredAt) et des temps morts, avec malus, temps mort, neutre et fautes.
  - `#should_returnEmptyTimelineAndTimeouts_when_eventsNullOrEmpty`.
  - `LivePublicViewServiceTest#should_report_last_points_and_active_timeout_in_sequence_order_when_payload_is_unordered`.
  - `LivePublicViewServiceTest#should_name_top_scorer_from_lowest_sequence_when_payload_is_unordered` : verrouille la réutilisation des events triés dans `topScorers`.
- **Rouge vérifiée sur l'ancien code** (services remis temporairement à `HEAD`) :
  - 3 échecs analyse : CA-1, CA-2, CA-4 ;
  - 1 NPE overlay : CA-4.

  Le test d'égalité de `sequence` côté analyse (CA-5) était déjà vert, puisque l'ancien code ne triait pas. Il garantit maintenant que le nouveau tri est stable.
- `LiveSetReplayTest` (11 tests) : CA-9, plus nulls/ex-aequo et `scoredFor` inconnu.
- `LiveScoreReplayConsistencyTest` : CA-3.
- Résultat `./mvnw clean test` : `Tests run: 325, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.

## Points d'attention
- **Build natif non vérifié localement.** `LiveSetReplay` est une classe finale avec des records internes, sans réflexion ni sérialisation : aucun hint ne devrait être nécessaire. Il faut quand même builder en natif avant `master`.
- **Changements de comportement sur des données invalides uniquement**, détaillés dans Choix de conception : `scoredFor` inconnu, malus ou temps mort porteur d'un `scoredFor`, point sans `sequence` sur l'overlay. Le snapshot de l'overlay est toujours validé. Le POST d'analyse ne l'est que depuis 8712564 : la timeline des matchs antérieurs peut changer dans ces cas (voir le ticket, section Risques).
- **Hors périmètre, à signaler.** `posteOf` et `computePlayerAnalysis` lisent toujours les events dans l'ordre stocké. Le « premier poste non nul » d'un joueur dans un set dépend donc encore de l'ordre de stockage. C'est le même type d'écart que celui que ce ticket corrige pour la timeline, mais le ticket exclut explicitement `computePlayerAnalysis`.
- Coût mémoire de l'overlay : le rejeu garde une liste de `Point` et de `Timeout` par set, en plus de la trajectoire que l'overlay construisait déjà. Pour un set réaliste (~60 à 100 events), ça fait une centaine de petits objets par set, et en échange il y a un tri en moins par set. Je n'ai pas fait de mesure.
