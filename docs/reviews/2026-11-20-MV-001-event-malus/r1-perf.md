---
type: perf-review
round: 1
bloquants: 0
majeurs: 0
mineurs: 1
---
# Revue performance — MV-001 event malus

Hypothèses : set = 50 à 100 events, match = ~300 events, overlay polled ~2/s par spectateur (max 120/min par client).

### PERF-001 — Passes supplémentaires sur les events (malusOf x2 par set à chaque poll, startScore x3 dans l'analyse) [MINEUR]
- **Localisation** : `LivePublicViewService.java:117-118` (`malusOf` appelé 2 fois par set), `LiveStatsAnalysisService.java:59,168,196` (`startScore` appelé par `buildTimeline`, `buildTimeouts` et pour le champ, soit 6 passes par set), `LiveStatsPayloadValidator.validateMalusPlacement` (2 passes par set).
- **Constat** : chaque appel parcourt la liste d'events du set. Aucune allocation notable (int, pas de stream). Pas de tri ni de désérialisation ajoutés.
- **Chiffrage** : overlay, +2 passes de ~100 comparaisons de String par set, soit ~1000 comparaisons par poll pour 5 sets, à ~2 polls/s : de l'ordre de la microseconde à la dizaine de microsecondes, négligeable devant la désérialisation JSON du snapshot (déjà présente) et le tri `orderedEvents`. Analyse et PUT : chemins non chauds, même ordre de grandeur. Complexité reste O(n), pas de O(n²) introduit.
- **Preuve** : diff ci-dessus ; `malusOf` est une simple boucle `for`, `validateMalusPlacement` ne construit la chaîne `where` que pour un event malus (max 2 par set).
- **Correction proposée** : aucune exigée. Optimisation prématurée. Si on touche ce code, on pourrait calculer `startScore` une fois et le passer aux deux builders, mais le gain est invisible et ajouterait de la complexité de signature.
- **Question au développeur** : aucune.

## Points vérifiés sans constat
- Pas de requête SQL, pas de migration, pas d'index concerné ; `LiveEventDto` reste en jsonb, +1 champ Integer nullable (quelques octets par event sérialisé, uniquement pour les malus, `null` sinon).
- Chemin chaud overlay : pas de regex, pas de stream imbriqué, pas de cache/Map statique ajouté. Le `continue` sur malus évite même du travail.
- Natif : aucune réflexion nouvelle (record couvert par `BindingReflectionHintsRegistrar` d'après la note ; `LiveScoreDto` est un DTO de réponse comme `LiveTimeoutDto`). Non vérifié par build natif, hors périmètre perf.
- Pas de NPE sur `event.sequence()` dans `validateMalusPlacement` : la nullité est rejetée en amont (`validateEvent`, ligne 138).
- Remarque hors perf (pour l'architecte/critic) : l'écart CA-2 à CA-7 côté POST (validateur non appelé sur `POST /matches/{id}/live-stats`) signalé par le développeur est un écart de couverture des CA, pas un sujet de performance ; je ne le chiffre pas ici.
