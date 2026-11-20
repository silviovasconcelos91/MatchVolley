# Synthèse — MV-004 Limiter à 1 Mo la taille du corps de POST live-stats
- Branche : feat/MV-004-limite-post-live-stats · base 9e68a69 · rounds : 2/3
- Tests : `./mvnw clean test` → 302 tests, 0 failure, 0 error (vérifié par l'orchestrateur, build propre)
- Verdict : **PRÊT** (build natif non vérifié localement)
- Exécution autonome autorisée par l'utilisateur (absent).

## Arbitrages requis
Aucun. Le conflit apparent perf (« PathPattern inutile ») vs critic (contournement) a été levé : perf a retiré son objection au round 2 (surcoût ~1–4 µs par PUT/POST, chemin chaud GET inchangé).

## Corrigé pendant le débat
| ID | Gravité | Correctif |
|----|---------|-----------|
| CRIT-001 | BLOQUANT | Contournement par URI encodée (`live-st%61ts`, `sn%61pshot` → 201/200 sans limite, y compris PUT préexistant). Matching via `PathPattern` précompilés sur le chemin décodé de l'application (comme Spring MVC) ; 8 cas unitaires encodés + context path, 2 IT encodés → 413, IT de garde < 1 Mo → 201. Critic : 24 variantes rejouées, aucune n'atteint le contrôleur sans limite |
| CRIT-002 | MAJEUR | Fixture surdimensionnée = match valide (10 sets × 360 events, titre court, ~1,14 Mo) ; filtre neutralisé temporairement → 201/200, prouvant que « rien persisté » vient de la limite |
| CRIT-004 | MINEUR | Marge de 9 % requalifiée en estimation sur fixture |
| CRIT-005 / PERF-001 | MINEUR | Flux limité mémorisé une fois par requête ; `getReader()` lit ce flux |
| CRIT-006 | MINEUR | Test 413 du PUT vérifie l'enveloppe complète |
| ARCH-001 | MINEUR | `realisticMatch` / `oversizedMatch` déplacés dans `LiveSnapshotFixtures` |

## Contestations acceptées par les relecteurs
Aucune.

## Reportés / mineurs non traités
| ID | Raison |
|----|--------|
| CRIT-003 / ARCH-D01 | Pas de borne de longueur sur les champs texte du payload live (`title` VARCHAR(255), etc.) → 500 hors enveloppe. Préexistant. **À ticketer.** |
| PERF-002 | Pas de plafond de repli Tomcat pour les autres POST/PUT authentifiés : hors périmètre (Q1) et nécessiterait de modifier les properties. |

## Critères d'acceptation
| CA | Couvert par | Statut |
|----|-------------|--------|
| CA-1 | `LiveStatsControllerIT#should_return413_envelope_and_persist_nothing_when_post_content_length_exceeds_one_megabyte` ; `LivePayloadSizeFilterTest` | ✅ |
| CA-2 | `LiveStatsControllerIT#should_return413_envelope_and_persist_nothing_when_chunked_post_exceeds_one_megabyte` (vrai chunked) | ✅ |
| CA-3 | `LiveStatsControllerIT#should_return201_when_posted_realistic_five_set_match_with_hundred_events_per_set` (+ 10×300 events) | ✅ |
| CA-4 | `LiveSessionControllerIT#should_return413_when_snapshot_body_exceeds_one_megabyte` | ✅ |
| CA-5 | `LiveStatsControllerIT#should_return401_or_413_and_persist_nothing_when_unauthenticated_post_exceeds_one_megabyte` | ✅ |
| CA-6 | `LivePayloadSizeFilterTest#should_not_filter_when_request_is_not_live_payload_upload` (10 cas) | ✅ |
| CA-7 | Une seule classe porte la limite (`LivePayloadSizeFilter`, ex-`SnapshotBodySizeFilter`) — vérification statique | ✅ |
| CA-8 | `live-stats-api-contract.md` : code 413 du POST documenté | ✅ |

## Non vérifié
- Build natif (aucune réflexion ajoutée selon perf/architect). Vérifié par la CI au push.
- Message du 413 du PUT changé en « Request body exceeds 1 MB » (non documenté auparavant ; le front ne s'appuie pas dessus d'après le critic).
- Le vert du round 0 était faussé par un `SnapshotBodySizeFilter.class` périmé dans `target/` ; les résultats retenus viennent de `./mvnw clean test`.
