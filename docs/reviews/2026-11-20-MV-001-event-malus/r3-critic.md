---
type: critic-review
round: 3
bloquants: 0
majeurs: 0
mineurs: 0
---
# Revue critique — MV-001 event `malus`, round 3 (CRIT-101)

| ID | Verdict | Justification |
|----|---------|---------------|
| CRIT-101 | VALIDÉ | `MAX_SETS = 10` (`LiveStatsPayloadValidator.java:33`), avec une javadoc qui explique la borne. Les deux contrôles (nombre de sets, l. 48, et `setNumber`, l. 62) utilisent la constante, et le POST comme le PUT passent par le même `validate`. Le scénario de mon round 2 est reproduit tel quel par `sixSetsAfterTiedSet()` : set 3 nul avec `wonBy: null`, 2-2 après 5 sets, puis un 6e set. Ce scénario est vérifié sur le validateur, sur le POST de bout en bout (201, puis 6 sets dans l'analyse avec `startScore {0,-1}`), sur le PUT avec l'overlay public (`currentSet.setNumber 6`, `setsWon {2,2}`) et sur le service overlay en test unitaire. Les bornes sont testées des deux côtés : 10 sets acceptés, 11 rejetés avec le message `at most 10 sets`, sans rien persister au POST. |

## Argument « relever plutôt que supprimer »
Accepté. Une borne évite qu'un `setNumber` absurde (999) devienne le `currentSet` affiché aux spectateurs. À 10, il faudrait 5 sets nuls dans un même match pour la dépasser. Ce n'est pas réaliste, et le dépassement produit un 400 explicite, pas une perte silencieuse. Une borne plus serrée (6 ou 7) recréerait le risque de CRIT-101.

## Tests existants modifiés (6, 7, 9 → 11) : intention conservée
J'ai vérifié les trois tests :
- `LiveStatsPayloadValidatorTest#should_reject_when_set_number_out_of_range` ;
- `LiveSessionControllerIT#should_return400_when_snapshot_payload_invalid` ;
- `LiveSessionServiceTest#should_throw_bad_request_when_snapshot_payload_invalid`.

Chacun envoie un payload d'**un seul** set, `set(11, null, List.of())`. Le contrôle du nombre de sets (1 ≤ 10) ne peut donc pas déclencher le rejet. La seule règle violée est `setNumber > MAX_SETS`, ce qui est bien l'intention d'origine : « `setNumber` hors limite → rejet ». Avec la nouvelle borne, garder 6, 7 ou 9 aurait rendu ces tests faux, car ces valeurs sont désormais valides. Passer à 11 est donc le seul changement correct, et ce n'est pas un affaiblissement.

Remarque sans constat : les tests IT et service n'asserent que le type d'exception ou le statut 400, pas le message. C'était déjà le cas avant ce ticket, et avec un unique set à vide aucune autre règle ne peut produire le rejet.

## Nouveaux problèmes introduits par le correctif
Aucun constat.
- Pas de `CHECK` sur `set_number` en base (le développeur cite `V6`), et l'overlay comme l'analyse itèrent sans borne.
- Docs alignées : `live-stats-api-contract.md:40` et `live-overlay-api-doc.md`.
- Un point pour la session front, hors périmètre backend : le commentaire `setNumber // numéro du set (1-5)` dans `MatchVolleyFront/src/data/liveStats.ts:105` est maintenant faux. Je n'ai trouvé côté front aucune logique qui suppose 5 sets au maximum.
- La limite de taille du corps du POST est une dette signalée par le développeur, antérieure à MV-001 et non aggravée. Elle relève d'un ticket séparé et je ne l'ouvre pas ici.

## IDs encore ouverts
Aucun.
