---
type: critic-review
round: 2
bloquants: 0
majeurs: 1
mineurs: 0
---
# Revue critique — MV-001 event `malus`, round 2 (verdicts)

Vérifié dans le code :
- `git diff bd673c1` ;
- `LiveEventRules.java` et `LiveEventRulesTest.java`, non suivis ;
- `LiveStatsService.java` ;
- les tests ajoutés.

Côté front, en lecture seule : `git status -sb` et `MatchContext.tsx`.

| ID | Verdict | Justification |
|----|---------|---------------|
| CRIT-001 | VALIDÉ | `payloadValidator.validate(request)` est la première instruction de `LiveStatsService.saveLiveStats`, avant `existsById` et toute persistance. Le test unitaire utilise un vrai `@Spy`, pas un mock, et vérifie qu'aucun `save` ni `attachMatch` n'est appelé. Les IT POST couvrent CA-2 (3, 0, null), CA-3, CA-4 dans les deux sens, CA-5, CA-6 et CA-7 (rejet et acceptation), et vérifient chaque fois l'absence du match. Le ticket (l. 26, 33, Q4), la javadoc de `malusOf` et le contrat sont alignés. Le branchement introduit en revanche une régression : voir CRIT-101. |
| CRIT-002 | VALIDÉ | Le `default` du switch annonce désormais `'point', 'fault' or 'neutral'` (l. 140). Le test vérifie `requires action.key 'malus'`, une chaîne absente du message par défaut : supprimer le contrôle explicite ferait échouer le test. |
| CRIT-003 | VALIDÉ | Le test sur une `sequence` égale (2 et 2) est ajouté. Passer `>=` à `>` le ferait échouer. |
| CRIT-004 | VALIDÉ | L'ancien test est renommé pour ce qu'il prouve. Le nouveau test porte un malus avec `player.id = 3` et vérifie `matchStats` et `setStats[0]`. Sans le filtre `isMalus`, la clé `malus` apparaîtrait dans `neutral`. |
| CRIT-005 | ACCEPTÉ | La preuve tient. `git status -sb` dans `MatchVolleyFront` affiche `master...origin/master [ahead 2]` : `a962d34` n'est pas publié, donc aucune fenêtre de données n'a existé. L'ordre de déploiement figure dans les Risques du ticket (l. 114). C'était le seul correctif que je demandais. |
| CRIT-006 | VALIDÉ | `Integer firstScoringSequence = null`, et la comparaison n'a lieu que s'il existe un event scorant. Test : un temps mort suivi de `malus(Integer.MAX_VALUE)` dans un set sans point est accepté. |

Sur le refactor `LiveEventRules` (ARCH-002), je n'ouvre pas de constat.
- Le comportement est identique : j'ai relu le code déplacé ligne à ligne contre la version du round 1.
- Le `grep` des anciennes références `LiveStatsPayloadValidator.(isMalus|isTimeout|malusOf|MINE|OPP|TIMEOUT_KEY)` ne renvoie plus rien.
- La classe est `public final` avec un constructeur privé et sans état, ce qui est sans risque.
- `MALUS_CATEGORY` reste privé au validateur. C'est cohérent : seul le validateur s'appuie sur la catégorie.

---

## Nouveaux constats (code modifié depuis le round 1)

### CRIT-101 — Le POST rejette désormais un match de plus de 5 sets, que le front peut produire (set terminé à égalité) → match non sauvegardé [MAJEUR]
- **Localisation** :
  - `src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java:35` (nouvel appel) ;
  - `LiveStatsPayloadValidator.java` : `validate` rejette `sets().size() > MAX_SETS`, et `validateSet` rejette `setNumber > MAX_SETS` (`MAX_SETS = 5`).
- **Constat** : avant ce round, la règle « au plus 5 sets » ne s'appliquait qu'au PUT snapshot. Le branchement la rend bloquante pour la soumission finale. Or le front n'arrête le match qu'à 3 sets gagnés (`SetBanner.tsx:33`, `matchOver = mySets === 3 || oppSets === 3`). Un set terminé manuellement à égalité ne compte pour personne (`MatchContext.tsx:730-741`, `FORCE_END_SET` → `winner = null`, commenté « tournoi temps »). Après 5 sets, le bouton « Démarrer le set suivant » reste donc proposé et `NEXT_SET` crée le set 6 (`LiveStatsContext.tsx:68-70`).
- **Scénario de défaillance** : un tournoi au temps dont un set s'arrête sur une égalité, par exemple à 18-18. Après 5 sets, le score en sets est de 2-2 et le coach joue un 6e set. Le payload final contient alors `setNumber: 6`, 6 sets. Le POST renvoie 400 `"a match has at most 5 sets"` au lieu de 201 avant ce round. `postLiveStats` lève `Failed to send live stats` et le match n'est pas enregistré. Sans session live, aucun PUT n'avait signalé le problème pendant le match. C'est précisément le risque que le développeur craignait au round 1 et que j'avais jugé surévalué. Ma contre-preuve (« même `buildLiveStatsPayload` que le PUT ») ne couvrait que la forme des events, pas le nombre de sets. Je corrige donc mon propre argument.
- **Preuve** : le code front cité (`origin/master` et `master` locale ont la même logique de bannière et de fin forcée). Test qui échouerait : `LiveStatsControllerIT#should_return201_when_posted_match_has_six_sets_after_a_tied_set` (six sets, le 3e avec `wonBy: null`) → 400 obtenu.
- **Correction proposée** : décision humaine, car la règle `MAX_SETS` est antérieure au ticket. Trois options :
  - (a) assouplir `MAX_SETS` (ou le supprimer) pour les deux endpoints, puisque la règle ne correspond pas à l'usage « tournoi temps » du front ;
  - (b) garder la règle et corriger le front, qui doit déclarer le match terminé après le 5e set ;
  - (c) garder la règle et accepter ce cas, en le documentant.

  Je recommande (a) ou (b). En l'état, (c) fait perdre un match.
- **Question au développeur** : as-tu un argument métier pour plafonner à 5 sets un format où une égalité ne compte pour personne ?
- **Recommandation** : `ARBITRAGE`. L'utilisateur a décidé « 400 sur toutes les règles du contrat » (Q4) sans connaître ce cas.

Je n'ai pas trouvé d'autre règle du validateur qu'un payload du front actuel enfreindrait :
- `venue` est dérivé de `isHome` ;
- `jersey` est typé `number` ;
- `sequence` vaut `i + 1` ;
- `wonBy` vaut `mine`, `opp` ou `null` ;
- les numéros de set ne sont jamais dupliqués.
