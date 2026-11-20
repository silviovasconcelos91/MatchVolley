# Synthèse — MV-001 Event malus dans les stats live
- Branche : feat/MV-001-event-malus · base bd673c1 · rounds : 3/3
- Tests : `./mvnw test '-Dtest=*Test,*IT'` → 268 tests, 0 failure, 0 error, 0 skipped (vérifié par l'orchestrateur)
- Verdict : **PRÊT AVEC RÉSERVES** (build natif non vérifié ; dettes hors périmètre à ticketer)

## Arbitrages requis
Aucun ouvert. Arbitrages rendus par l'utilisateur pendant le débat :
- Q4 / CRIT-001 / ARCH-001 : validateur branché sur `POST /matches/{id}/live-stats`.
- Q5 / CRIT-101 : `MAX_SETS` assoupli côté backend → relevé de 5 à 10 (set nul en tournoi au temps ⇒ 6e set possible).
- Dette IT/CI (ARCH-D02) : ticket séparé.

## Corrigé pendant le débat
| ID | Gravité | Correctif |
|----|---------|-----------|
| CRIT-001 / ARCH-001 | MAJEUR | `payloadValidator.validate(request)` en tête de `LiveStatsService.saveLiveStats` ; 6 IT POST → 400 (CA-2..7) + 1 IT POST 201 ; test unitaire « rien persisté » |
| CRIT-101 | MAJEUR | `MAX_SETS` 5 → 10 (POST + PUT) ; tests 6 sets / 10 sets acceptés, 11 → 400 ; overlay et analyse vérifiés à 6 sets |
| ARCH-002 | MINEUR | Extraction `LiveEventRules` (MINE/OPP, isTimeout, isMalus, malusOf) + `LiveEventRulesTest` |
| CRIT-002 | MINEUR | Test CA-4 sur message exact ; message `default` corrigé |
| CRIT-003 | MINEUR | Test malus à `sequence` égale au premier point → 400 |
| CRIT-004 | MINEUR | Test CA-16 niveau joueur ajouté, ancien test renommé |
| CRIT-006 | MINEUR | Sentinelle `Integer.MAX_VALUE` remplacée par `Integer` nullable + test |

## Contestations acceptées par les relecteurs
| ID | Raison retenue |
|----|----------------|
| CRIT-005 | Front malus non publié (commit non poussé) → aucune donnée malus sans montant possible ; ordre de déploiement backend → front inscrit dans les risques du ticket |
| PERF-001 | Coût chiffré négligeable (~1000 comparaisons/poll, pas de SQL) ; mineur clos après une contestation |

## Reportés / mineurs non traités
| ID | Raison |
|----|--------|
| ARCH-D01 | Dette existante : recalcul du score dupliqué en 3 boucles (overlay trié, analyse non) — ticket séparé ; y harmoniser `"mine"`/`"opp"` en dur dans `LiveStatsAnalysisService:158-159` |
| ARCH-D02 | Dette existante : `./mvnw test` ne lance aucun `*IT` (pas de config surefire) et CI en `-DskipTests` — ticket séparé (modif `pom.xml`) |
| — | Pas de limite de taille du corps sur le POST live-stats ni du nombre d'events par set (dette antérieure) — ticket séparé |
| — | Front : commentaire `setNumber // (1-5)` dans `MatchVolleyFront/src/data/liveStats.ts:105` désormais faux — à transmettre à la session front |

## Critères d'acceptation
| CA | Couvert par | Statut |
|----|-------------|--------|
| CA-1 | `LiveStatsPayloadValidatorTest#should_accept_when_malus_is_first_event_of_set` ; `LiveSessionControllerIT#should_return200_when_snapshot_has_valid_malus` ; `LiveStatsControllerIT#should_return201_and_keep_payload_set_score_when_set_has_malus` | ✅ |
| CA-2 | `LiveStatsPayloadValidatorTest#should_reject_when_malus_amount_is_not_one_or_two` ; PUT + `LiveStatsControllerIT#should_return400_when_posted_malus_amount_is_invalid` | ✅ |
| CA-3 | `LiveStatsPayloadValidatorTest#should_reject_when_non_malus_event_has_malus_amount` ; `LiveStatsControllerIT#should_return400_when_posted_non_malus_event_has_malus_amount` | ✅ |
| CA-4 | `LiveStatsPayloadValidatorTest#should_reject_when_malus_category_used_with_other_key` / `..._key_used_with_other_category` ; POST IT | ✅ |
| CA-5 | `LiveStatsPayloadValidatorTest#should_reject_when_malus_has_player_trajectory_or_scoredFor` ; POST IT | ✅ |
| CA-6 | `LiveStatsPayloadValidatorTest#should_reject_when_malus_sequence_is_after_a_scoring_event` (+3) ; PUT + POST IT | ✅ |
| CA-7 | `LiveStatsPayloadValidatorTest#should_reject_when_same_team_has_two_malus_in_a_set` (+2) ; PUT + POST IT | ✅ |
| CA-8 | `LiveSessionControllerIT#should_return200_when_snapshot_events_have_no_malus_field`, `LiveStatsControllerIT#should_return201_and_null_malus_amount_when_payload_has_no_malus_field` | ✅ |
| CA-9 | `LivePublicViewServiceTest#should_deduct_malus_from_set_score_when_malus_then_points` ; `PublicLiveControllerIT#should_deduct_malus_from_public_score_and_restore_it_on_undo` | ✅ |
| CA-10 | `LivePublicViewServiceTest#should_start_trajectory_at_negative_score_when_malus_alone` | ✅ |
| CA-11 | `LivePublicViewServiceTest#should_ignore_malus_in_serving_and_last_points_when_point_follows` | ✅ |
| CA-12 | `LivePublicViewServiceTest#should_exclude_malus_from_last_points_top_scorers_and_timeouts` | ✅ |
| CA-13 | `LivePublicViewServiceTest#should_restore_score_when_next_snapshot_drops_the_malus` ; PUT×2 IT | ✅ |
| CA-14 | `LiveStatsAnalysisServiceTest#should_startTimeline_from_malus_score_without_malus_entry`, `#should_deductMalus_in_timeline_when_events_are_not_in_sequence_order` | ✅ |
| CA-15 | `LiveStatsAnalysisServiceTest#should_scoreTimeout_with_malus_and_count_afterPoint_as_points_played` | ✅ |
| CA-16 | `LiveStatsAnalysisServiceTest#should_excludeMalus_from_team_set_and_player_actionStats`, `#should_excludeMalus_from_player_actionStats_when_malus_carries_a_player` | ✅ |
| CA-17 | `LiveStatsControllerIT#should_return201_and_keep_payload_set_score_when_set_has_malus` | ✅ |
| CA-18 | `LiveStatsAnalysisServiceTest#should_exposeStartScore_with_both_malus_when_set_has_malus`, `#should_exposeZeroStartScore_when_set_has_no_malus_or_no_events` | ✅ |
| CA-19 | `LiveStatsPayloadValidatorTest#should_accept_when_malus_of_two_points_in_deciding_set` | ✅ |

## Non vérifié
- Build natif (`./mvnw -Pnative -DskipTests package`) non lancé. Architecte : aucun hint requis (`LiveEventDto` couvert via `LiveStatsRequest`, `LiveScoreDto` comme `LiveTimeoutDto`). À lancer avant push sur `master` (CI native).
- `00-implementation.md` non mis à jour après round 1 (indique encore « POST non couvert ») : `r1-reponse.md` fait foi.
