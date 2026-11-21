---
type: perf-review
round: 1
bloquants: 0
majeurs: 0
mineurs: 2
---
# Revue performance — MV-002 rejeu du score unique (LiveSetReplay)

Hypothèse de charge : match de 3 à 5 sets, ~60-100 events par set (~300 au total), poll overlay ~1/s à 2/s par spectateur. Tout ce qui suit est du CPU en mémoire. Aucune requête SQL n'est ajoutée ni retirée (`computeState` travaille sur le snapshot déjà chargé).

## Bilan chemin chaud (`computeState`, par set puis par appel, n = events du set)

| Poste | Avant | Après |
|---|---|---|
| Tris de la liste d'events | 2 (`orderedEvents` dans la boucle, puis dans `topScorers`), chacun `stream().sorted().toList()` (copie + tri + copie) | 1 (`new ArrayList` + `sort`, stable, ~O(n) si déjà trié). Gain net : 1 tri et 2 copies en moins par set |
| Passes sur les events | 2 `malusOf` + 1 boucle score + 1 boucle `topScorers` (+ 1 tri de plus) = 4 passes + 2 tris | 2 `malusOf` + 1 boucle de rejeu + 1 boucle `points()` + 1 boucle `timeoutCounts` (sur les seuls timeouts) + 1 boucle `topScorers` = 5 passes + 1 tri |
| Allocations | `ScoreDto` par point + trajectoire | idem + 1 `Point` (24 o) par point + 1 `Timeout` par temps mort + 2 `unmodifiableList` + 1 liste d'events triée |
| Ordre de grandeur par appel | ~300 events : 2 tris sur ~300 éléments déjà triés (~300 comparaisons chacun) | 1 tri (~300 comparaisons) + ~150 `Point` (~3,6 Ko) |

Conclusion : net positif ou neutre. Le surcoût (quelques Ko d'objets éphémères, 1 passe linéaire de plus) est de l'ordre de la microseconde à la dizaine de microsecondes par poll, très en dessous du coût de la désérialisation du snapshot JSON et de la sérialisation de la réponse, qui sont inchangés. Aucun cache ni mesure supplémentaire n'est justifié. La note d'implémentation dit n'avoir pas mesuré : c'est acceptable à cette échelle.

## Bilan analyse (`getMatchAnalysis`, chemin privé, rare)

Avant : `startScore` appelé 3 fois (direct, `buildTimeline`, `buildTimeouts`) = 6 passes `malusOf`, plus 2 boucles. Après : 1 `LiveSetReplay.of` par set = 1 tri + 2 `malusOf` + 1 boucle. Gain léger, chemin non chaud. Les méthodes publiques `buildTimeline/buildTimeouts/startScore` refont un rejeu complet chacune si elles sont appelées séparément, mais `getMatchAnalysis` ne le fait pas : vérifié dans le diff (un seul `of` par set). Pas de recalcul répété.

Native image : classe finale, records internes, aucune réflexion, `Comparator` statique : rien à déclarer.

Isolation par utilisateur : non concernée (calcul pur sur DTO).

### PERF-001 — `toPoint` exécuté pour tous les points alors que seuls les 5 derniers sont exposés [MINEUR]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LivePublicViewService.java:127-130` (boucle sur `replay.points()`), `lastPoints` ligne ~178 (`LAST_POINTS_LIMIT = 5`).
- **Constat** : à chaque poll, un `PublicPointDto` (+ appels `liveSessionMapper.toPublicPlayer/toPublicAction`, 2 à 3 objets) est construit pour chaque point du match, puis `lastPoints` n'en garde que 5. Ce comportement préexiste à MV-002 (même schéma dans l'ancien code) et n'est pas aggravé par le diff.
- **Chiffrage** : ~150 points par match de 5 sets, soit ~150 DTO + ~300 objets de mapping par poll, pour 5 conservés. À 2 polls/s : ~900 allocations/s, soit quelques dizaines de Ko/s, négligeable pour le GC. Sous native image (GC sans JIT) cela reste imperceptible.
- **Preuve** : `allPoints.add(toPoint(...))` dans la boucle, puis `lastPoints(allPoints)` ne garde que `LAST_POINTS_LIMIT`.
- **Correction proposée** : optionnelle. Conserver les `Point` bruts et ne mapper que les 5 derniers (`sequence != null`) après la boucle. Gain faible, complexité faible mais non nulle (état à garder entre sets). Je ne l'exige pas.
- **Question au développeur** : aucune obligatoire. Hors périmètre de MV-002.

### PERF-002 — Rejeu matérialisé (`points`, `timeouts`, `events`) conservé pour toute la durée de `computeState` [MINEUR]
- **Localisation** : `LivePublicViewService.java:109,117` (`replays` accumulé pour `topScorers`).
- **Constat** : tous les `LiveSetReplay` du match restent vivants jusqu'à `topScorers`, au lieu d'être libérés set par set. C'est le coût que la note signale sans le chiffrer.
- **Chiffrage** : ~300 events, ~150 `Point`, quelques `Timeout`, 5 listes copiées : ~10 à 15 Ko vivants quelques microsecondes. Aucun impact mémoire mesurable sur Railway, même à plusieurs spectateurs en parallèle (~15 Ko x quelques requêtes concurrentes).
- **Preuve** : `replays.add(replay)` dans la boucle de `computeState`.
- **Correction proposée** : aucune. Complexifier (accumuler `topScorers` en ligne) ne rapporterait rien. Signalé pour mémoire.
- **Question au développeur** : aucune.

## Points vérifiés sans constat
- Le tri est stable et en O(n) sur entrée déjà triée (cas nominal, séquences croissantes) : pas de risque O(n log n) coûteux ni de O(n²). Pas de `stream()` imbriqué ajouté.
- `Comparator` en `static final` : pas d'allocation de comparateur par appel (mieux que l'ancien code, qui en recréait un par `orderedEvents`).
- `new ArrayList<>(replay.points().size() + 1)` et `timelineOf` pré-dimensionnées : bien.
- Rejeu d'un set vide ou `events` null : retourne des `List.of()` sans allocation de tri.
- `malusOf` reste appelé 2 fois sur la liste triée (2 passes O(n)). Fusionnable dans la boucle de rejeu, mais gain de ~300 itérations par poll : ne pas demander.
- `timeoutCounts` : boucle sur les seuls temps morts (≤ ~6 par set), négligeable.

## Verdict
Aucun blocage ni point majeur. L'objectif perf du ticket (un seul tri par set et par appel, sur les deux chemins) est atteint et vérifié dans le code. Les deux mineurs sont informatifs.
