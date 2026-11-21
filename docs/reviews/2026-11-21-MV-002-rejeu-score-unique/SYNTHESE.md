# Synthèse — MV-002 Unifier le rejeu du score d'un set entre l'overlay et l'analyse
- Branche : feat/MV-002-rejeu-score-unique · base 73e5c66 · rounds : 1/3
- Tests : `./mvnw clean test` → 325 tests, 0 failure, 0 error (vérifié par l'orchestrateur, build propre, IT inclus)
- Verdict : **PRÊT** (build natif non vérifié localement)
- Exécution autonome autorisée par l'utilisateur (absent) : décisions d'orchestration notées ci-dessous.

## Arbitrages requis
Aucun. Décision prise par l'orchestrateur (recommandation architecte + critic) :
- Q1 / CRIT-001 : un point de `sequence` null est compté (score, trajectoire) mais absent de `lastPoints` (`PublicPointDto.sequence` est `int`, contrat inchangé). État impossible avec des données validées (snapshot validé, `sequence >= 1` exigé depuis 76f5285).

Pas de round de verdict : relecteurs à 0 bloquant / 0 majeur ; tous les mineurs corrigés ou contestés une fois avec justification (règle : clos).

## Corrigé pendant le débat
| ID | Gravité | Correctif |
|----|---------|-----------|
| CRIT-001 | MINEUR | Note corrigée (« état impossible », pas « bug existant ») ; comportement conservé (Q1) |
| CRIT-002 | MINEUR | Justification corrigée (POST validé seulement depuis 8712564) ; risque ajouté au ticket (timeline des analyses antérieures peut changer) ; test `should_align_timeline_and_timeouts_when_unvalidated_timeout_carries_scoredFor` |
| CRIT-003 / ARCH-001 | MINEUR | `startScore(List)` supprimée (aucun appelant) ; javadocs renvoient à `LiveSetReplay` |
| CRIT-004 | MINEUR | Test « temps mort final actif » remplacé par 2 scénarios valides (sans malus ; temps mort puis malus avant premier point) |
| CRIT-005 | MINEUR | Doc analyse : `timeouts` suit l'ordre de `sequence` |

## Contestations acceptées par les relecteurs
| ID | Raison retenue |
|----|----------------|
| PERF-001 | `PublicPointDto` construit pour tous les points alors que 5 gardés : préexistant, coût négligeable, rien exigé |
| PERF-002 | Garder les rejeux jusqu'à `topScorers` est ce qui supprime le 2e tri par set (~15 Ko transitoires) |

## Reportés / mineurs non traités
| ID | Raison |
|----|--------|
| ARCH-D01 | `computePlayerAnalysis` / `posteOf` lisent encore les events dans l'ordre stocké (poste d'un joueur dépend de l'ordre) — hors périmètre, **à ticketer** |

## Gains
- Overlay (chemin chaud) : 2 tris par set → 1 ; comparateur `static final`.
- Analyse : `startScore` calculé 3× → 1 rejeu par set ; timeline et temps morts triés par `sequence` (corrige la timeline fausse sur events stockés dans le désordre).

## Critères d'acceptation
| CA | Couvert par | Statut |
|----|-------------|--------|
| CA-1 | `LiveStatsAnalysisServiceTest#should_buildTimeline_in_sequence_order_when_events_stored_unordered` | ✅ |
| CA-2 | `LiveStatsAnalysisServiceTest#should_placeTimeout_by_sequence_when_events_stored_unordered` | ✅ |
| CA-3 | `LiveScoreReplayConsistencyTest#should_give_same_score_progression_in_overlay_and_analysis_when_set_is_stored_unordered` | ✅ |
| CA-4 | `LiveSetReplayTest#should_replay_null_sequences_last_in_stored_order_and_keep_ties_stable` + overlay/analyse | ✅ |
| CA-5 | `LiveSetReplayTest#...keep_ties_stable`, `LiveStatsAnalysisServiceTest#should_keep_stored_relative_order_when_events_share_a_sequence`, `LivePublicViewServiceTest#should_keep_stored_relative_order_when_two_events_share_a_sequence` | ✅ |
| CA-6 | Aucune ligne existante supprimée sous `src/test` ; tests overlay/analyse verts | ✅ |
| CA-7 | Grep : incréments de score uniquement dans `LiveSetReplay` | ✅ |
| CA-8 | Grep : plus de `"mine"`/`"opp"` dans `LiveStatsAnalysisService` | ✅ |
| CA-9 | `LiveSetReplayTest` (set vide, events null, malus seul, malus + points, temps morts, …) | ✅ |

## Non vérifié
- Build natif (aucune réflexion ajoutée selon perf/architect) — vérifié par la CI au push.
- Effet réel sur les analyses stockées avant 8712564 (pas d'accès aux données de prod).
