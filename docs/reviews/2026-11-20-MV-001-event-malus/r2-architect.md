---
type: architecture-review
round: 2
date: 2026-11-20
perimetre: diff courant (git diff bd673c1 + fichiers non suivis, hors .claude/ et docs/tickets/) — ticket MV-001
base: feat/MV-001-event-malus@bd673c1 (modifications non commitées)
verdict: CONFORME
bloquants: 0
majeurs: 0
mineurs: 0
---

# Revue d'architecture MV-001, round 2 : verdicts

Arbitrages appliqués : Q1, validateur branché sur le POST (option A) ; Q2, IT et CI traités dans un ticket séparé, hors MV-001.

| ID | Verdict | Justification |
|---|---|---|
| ARCH-001 | ACCEPTÉ (correctif validé) | Le validateur est appelé en première instruction de `saveLiveStats` (`LiveStatsService.java:35`), avant `existsById` (l.36) et avant toute écriture : `matchRepository.save` l.42, `liveSessionRepository.save` l.46, `attachMatch` l.49. Il est injecté par constructeur (`final`, l.28), au même endroit et sur le même modèle que `LiveSessionService.java:95`. Le controller n'est pas touché. Le 400 passe par `BadRequestException`, mappée par `GlobalExceptionHandler`. Les IT POST couvrent CA-2 à CA-7, et chaque cas vérifie aussi l'absence du match en base (`LiveStatsControllerIT.java:263-285` et suivantes). La mutation décrite par le développeur (7 tests rouges quand l.35 est neutralisée) prouve que ces tests dépendent bien de ce branchement. Le contrat (`live-stats-api-contract.md`) et le ticket sont mis à jour. |
| ARCH-002 | ACCEPTÉ (correctif validé) | `LiveEventRules` (`service/LiveEventRules.java`) est une classe utilitaire `final`, sans état, avec un constructeur privé. Elle ne dépend que de `dto/live/LiveEventDto` : elle n'accède ni aux repositories ni aux entités, et n'est pas un bean Spring. Elle respecte donc le sens `service → dto`. Il existe un précédent de statique public partagé entre services (`LiveStatsAnalysisService.posteOf`, appelé par `PlayerService`). Le validateur ne garde que des constantes privées de validation (`LiveStatsPayloadValidator.java:27-32`). Aucune référence `LiveStatsPayloadValidator.(isMalus\|isTimeout\|malusOf\|MINE\|OPP)` ne subsiste dans `src` : grep vérifié. `LiveEventRulesTest` est en AssertJ, sans contexte Spring. La visibilité `public` est justifiée par le test depuis le package `vasconcelos.volleymatch` et sans risque, puisqu'il s'agit de fonctions pures. La javadoc de `malusOf` est désormais exacte, car les deux chemins d'écriture sont validés. |

## Points MAINTENUS
Aucun.

## Nouveaux constats (ARCH-1xx), sur le code modifié depuis le round 1
Aucun.

Remarque sans gravité, non comptée : `LiveStatsAnalysisService.java:158-159` passe encore les littéraux `"mine"`/`"opp"` à `malusOf`, alors que `LiveEventRules.MINE`/`OPP` existent. Ce service utilisait déjà des littéraux avant le diff (`:75`, `:172`, `:197`). L'homogénéisation peut se faire dans le ticket de rejeu unique (ARCH-D01).

## Dette restante (hors MV-001, non bloquante)
- ARCH-D01, rejeu du score dupliqué en trois boucles : ticket séparé. `LiveEventRules` est l'endroit naturel où placer un futur `LiveSetReplay`.
- ARCH-D02, IT non exécutés par `./mvnw test` et CI sans tests : ticket séparé, selon l'arbitrage Q2.

## Réponse finale
Rapport : `docs/reviews/2026-11-20-MV-001-event-malus/r2-architect.md`. Verdict CONFORME. 0 bloquant, 0 majeur, 0 mineur. Aucun ID architecte ouvert.
