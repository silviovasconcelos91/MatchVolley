# Réponse développeur — round 2
| ID | Position | Résumé |
|----|----------|--------|
| CRIT-101 | CORRIGÉ | `MAX_SETS` passe de 5 à 10, une borne de sécurité qui s'applique au POST comme au PUT (même validateur). 6 sets → 201 au POST et 200 au PUT, 11 sets → 400. L'overlay et l'analyse sont vérifiés sur 6 sets |

## CRIT-101 — CORRIGÉ
Arbitrage utilisateur : assouplir `MAX_SETS` sur le POST et le PUT. Le choix entre supprimer la limite et la relever m'était laissé.

- **Correctif** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsPayloadValidator.java:33`, où `MAX_SETS = 10` est accompagné d'une javadoc qui explique la borne. Les deux contrôles existants utilisent la constante sans changement de logique :
  - nombre de sets (`:48-49`) ;
  - `setNumber` entre 1 et `MAX_SETS` (`:62-63`).

  Le POST et le PUT passent par le même `validate` (`LiveStatsService.java:35`, `LiveSessionService.java:95`), donc l'assouplissement vaut pour les deux.

- **Pourquoi relever la limite plutôt que la supprimer** :
  - **Une borne reste utile.**
    - L'overlay prend comme `currentSet` le set au plus grand `setNumber` (`LivePublicViewService.computeState`, tri par `setNumber`). Sans borne, un `setNumber` absurde (999, ou `Integer.MAX_VALUE`) serait accepté puis affiché aux spectateurs comme « Set 999 ».
    - La règle de forme existante « `setNumber` unique entre 1 et N » reste ainsi pertinente au lieu de disparaître.
    - Elle borne aussi une dimension du payload au POST, qui n'a pas de filtre de taille. `SnapshotBodySizeFilter` (1 Mo) ne couvre que le PUT snapshot (`SnapshotBodySizeFilter.java:33-34`).
  - **Je ne la présente pas comme une protection anti-abus suffisante.** Le nombre d'events par set reste non borné. La vraie limite de taille du POST relève d'un filtre de corps de requête, hors périmètre (voir Points ouverts).
  - **Pourquoi 10.** Un match s'arrête à 3 sets gagnés (`SetBanner.tsx:33`), donc au plus 5 sets décisifs. Chaque set au-delà ne peut venir que d'un set terminé à égalité, par une fin forcée à score égal en tournoi au temps (`MatchContext.tsx:730-741`). 10 sets, c'est 5 sets décisifs plus 5 sets nuls dans un même match, alors qu'un seul set nul est déjà un cas rare. Une borne plus serrée (6 ou 7) recréerait le même risque que CRIT-101 (match perdu au POST) pour un gain nul. Le front n'a pas de plafond : `NEXT_SET` fait `currentSet + 1` sans limite (`LiveStatsContext.tsx:68-70`).
  - **Coût d'un dépassement** : 400 explicite (« a match has at most 10 sets »). Ce n'est pas une perte silencieuse, mais un match à 11 sets serait bien rejeté. Je l'accepte, car ce scénario n'est pas réaliste.

- **Vérification « rien d'autre ne suppose 5 sets »** :
  - Aucune contrainte en base sur `set_number` (`V6__add_live_stats.sql:15`, seulement `INTEGER NOT NULL` ; aucun `CHECK` sur le numéro de set).
  - Aucune autre occurrence de `5` liée aux sets dans `src/main` (`grep MAX_SETS|<= 5|> 5|5 sets`).
  - L'overlay et l'analyse itèrent sur la liste des sets, sans borne.

- **Tests (TDD)** : ils étaient rouges avant le correctif (6 échecs observés), sauf le test unitaire overlay, déjà vert. Ce dernier confirme que le service ne supposait rien et sert de verrou.
  - Fixtures (`LiveSnapshotFixtures.java:90,101`) :
    - `sixSetsAfterTiedSet()` reproduit le scénario du critic : set 3 à égalité 1-1 avec `wonBy: null`, 2-2 après 5 sets, puis un 6e set avec un malus `opp` -1 et un point `mine` ;
    - `numberedSets(n)` génère n sets numérotés.
  - Validateur :
    - `LiveStatsPayloadValidatorTest#should_accept_when_match_has_six_sets_after_a_tied_set` ;
    - `#should_accept_when_match_has_ten_sets` (borne incluse) ;
    - `#should_reject_when_match_has_more_than_ten_sets` (message `at most 10 sets`).
  - POST, de bout en bout :
    - `LiveStatsControllerIT#should_return201_when_posted_match_has_six_sets_after_a_tied_set` : 201, et l'analyse renvoie les 6 sets. Le 6e a `startScore {0, -1}` et une timeline d'une entrée.
    - `#should_return400_when_posted_match_has_more_than_ten_sets` : 400 dans l'enveloppe, et rien n'est persisté.
  - PUT et overlay :
    - `PublicLiveControllerIT#should_accept_sixth_set_snapshot_and_show_it_as_current_set` : PUT 200 avec `applied: true`, puis GET public : 6 sets, `setsWon {2,2}`, `currentSet.setNumber 6`, score `{1, -1}`.
    - `LivePublicViewServiceTest#should_show_sixth_set_as_current_when_a_set_ended_tied` : en plus, le set nul garde son score 1-1 et `wonBy: null`.

- **Tests existants ajustés, pas supprimés** : trois tests utilisaient un numéro de set entre 6 et 9 comme exemple de valeur **hors limite**. Sous la nouvelle borne, ce sont des valeurs valides. J'ai remplacé la valeur par 11, et l'intention de chaque test (rejet d'un `setNumber` hors limite) est conservée :
  - `LiveStatsPayloadValidatorTest#should_reject_when_set_number_out_of_range` (l. 52) : 6 → 11 ;
  - `LiveSessionControllerIT#should_return400_when_snapshot_payload_invalid` (l. 138) : 7 → 11 ;
  - `LiveSessionServiceTest#should_throw_bad_request_when_snapshot_payload_invalid` (l. 178) : 9 → 11. Ce test est apparu au premier lancement de la suite complète.

- **Docs** :
  - `live-stats-api-contract.md:40` : `setNumber` 1–10, avec l'explication du set nul.
  - `live-overlay-api-doc.md:170` : règle de validation « au plus 10, `setNumber` entre 1 et 10 ».
  - `live-overlay-api-doc.md:448` : même ligne de contrat que le fichier précédent.
  - `live-stats-analysis-api-doc.md` : aucune mention de limite, donc inchangé.

- **Ticket** : `docs/tickets/MV-001-event-malus-live-stats.md:108`, nouvelle décision Q5 (limite assouplie sur POST et PUT, borne de 10 choisie par le développeur).

- **Réponse à la question du critic** (« argument métier pour plafonner à 5 sets ? ») : aucun. La limite de 5 vient de la règle FIVB d'un match en 3 sets gagnants. Elle est fausse dès qu'un set nul ne compte pour personne. Elle ne tenait jusqu'ici que parce que le POST n'était pas validé, et le PUT n'était pas utilisé pour ces matchs. Le constat est fondé et je le reconnais.

## Points ouverts (non traités, hors périmètre)
- **Limite de taille du corps du POST** : `SnapshotBodySizeFilter` ne s'applique qu'au PUT snapshot. Le POST n'a pas de plafond de taille, et la borne de 10 sets ne limite pas le nombre d'events. Cette dette existait avant MV-001 et le diff ne l'aggrave pas. À traiter dans un ticket séparé si l'on veut une vraie protection anti-abus. La décision revient à l'humain.

## Conflits entre relecteurs
Aucun. Ce correctif retire du travail au validateur au lieu d'en ajouter, donc il n'a aucun impact perf.

## Résultat `./mvnw test`
- Commande : `./mvnw test '-Dtest=*Test,*IT'`.
- `[INFO] Tests run: 268, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`. C'était 261 au round 1. Les 7 tests ajoutés : 3 validateur, 2 IT POST, 1 IT PUT/overlay, 1 overlay unitaire.
