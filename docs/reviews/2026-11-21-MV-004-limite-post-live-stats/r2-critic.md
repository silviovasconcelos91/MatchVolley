---
type: critic-review
round: 2
bloquants: 0
majeurs: 0
mineurs: 0
---
# Verdict critic — round 2 — MV-004

| ID | Verdict | Justification |
|----|---------|---------------|
| CRIT-001 | VALIDÉ | Le filtre fait désormais le matching avec `PathPattern` sur `RequestPath.parse(uri, contextPath).pathWithinApplication()` (`LivePayloadSizeFilter.java:47-61`), c'est-à-dire avec la même sémantique que Spring MVC. J'ai revérifié sur 24 variantes de chemin (détail ci-dessous) : aucune n'atteint le handler sans passer par la limite. Le test unitaire couvre 8 cas encodés et un context path. L'IT POST encodé est complétée par un IT de garde qui prouve que la route encodée atteint bien le contrôleur (201). L'IT PUT encodé vérifie que le snapshot n'est pas modifié. Le développeur rapporte ces IT rouges avant correction. |
| CRIT-002 | VALIDÉ | `oversizedMatch` = `realisticMatch(id, 10, 360)` : titre court, valide, persistable, dont le corps sérialisé fait plus de 1 Mio (vérifié par `oversizedBody`). J'accepte la neutralisation temporaire du filtre (les 4 IT de rejet authentifiés passent alors à 201/200) comme preuve que « rien n'est persisté » découle maintenant de la limite. La limite résiduelle sur CA-5 (401 avant lecture) est conforme au CA et explicitement assumée. |
| CRIT-003 | ACCEPTÉ (report) | Cause confirmée. Il s'agit d'un bug antérieur à MV-004, hors décision Q1, et identique à ARCH-D01. C'est le report que j'avais moi-même proposé. Il reste à ouvrir le ticket de dette, ce qui relève de l'arbitrage humain et ne bloque pas MV-004. |
| CRIT-004 | VALIDÉ | `00-implementation.md:39` et `:44` présentent la marge de ~9 % comme une estimation sur fixture, mesurée avec un `JsonMapper` par défaut, et non comme une mesure d'un export front. La mesure sur payload réel est renvoyée aux Risques du ticket, avant déploiement. |
| CRIT-005 | VALIDÉ | `getReader()` (`:97-105`) décode le même `LimitedInputStream` mémorisé. Le test `..._through_reader_...` couvre le cas. L'encodage par défaut UTF-8 est pertinent pour du JSON. Le `CharacterEncodingFilter` de Boot force de toute façon UTF-8 en amont, donc un `charset` invalide dans `Content-Type` n'atteint pas `Charset.forName`. |
| CRIT-006 | VALIDÉ | `assertTooLargeEnvelope` vérifie `data` null, `status` 413 et le message exact. Il est réutilisé par l'IT PUT encodé. |

## Revérification de CRIT-001 sur d'autres encodages

Méthode : programme jetable dans le scratchpad, hors projet, lancé sur le classpath de test et `target/classes` (issu du `clean test`).
- Un contrôleur factice porte les mêmes mappings (`@PostMapping("/matches/{matchId}/live-stats")` et `@PutMapping("/live-sessions/{id}/snapshot")` sous `/api/v1`), monté en `MockMvc` standalone avec le vrai `LivePayloadSizeFilter`.
- Chaque requête envoie un corps de 1 Mio + 10 000 octets.
- Chaque URI passe aussi par `StrictHttpFirewall` par défaut.
- Le projet n'a pas de `configurePathMatch` ni de réglage de trailing slash, donc les valeurs par défaut de MVC s'appliquent, comme en standalone.

| Requête | Firewall | Résultat (filtre + MVC) |
|---|---|---|
| `POST …/m1/live-stats` | accepté | 413 |
| `POST …/m1/live-st%61ts` | accepté | 413 |
| `POST …/m1/live%2Dstats` | accepté | 413 |
| `POST …/%20/live-stats` (matchId encodé) | accepté | 413 |
| `PUT …/abc/sn%61pshot` | accepté | 413 |
| `POST …/m1/live-st%41ts` (casse : `A`) | accepté | 404 (MVC sensible à la casse) |
| `POST /API/v1/…`, `…/Live-Stats` | accepté | 404 |
| `POST …/m1/live-stats/` (trailing slash) | accepté | 404 (trailing slash non toléré par MVC 7) |
| `POST …/m1/live-st%61ts/` | accepté | 404 |
| `PUT …/abc/snapshot/` | accepté | 404 |
| `POST …/m1/live-stats.json`, `…live-stats%20`, `…live-stats%09` | accepté | 404 |
| `POST …/m1/live-stats;a=b`, `…/matches;a=b/…` (matrix params) | **rejeté** | 413 (le filtre ne dépend plus du firewall) |
| `POST …/m1//live-stats`, `//api/…` (double slash) | rejeté | 404 |
| `POST …/./live-stats`, `…/x/../matches/…` | rejeté | 404 |
| `POST …live-stats%3Ba=b`, `…live-stats%2F`, `…m1%2Fx/…` | rejeté | 404 / 413 |
| `post …/live-stats` (méthode en minuscules) | rejeté | 405 (`RequestMethod.resolve` sensible à la casse, comme `HttpMethod.matches`) |

Invariant vérifié : chaque fois que MVC route vers le handler, le filtre s'applique. Chaque fois que le filtre ne s'applique pas, MVC ne route pas : 404 ou 405, sans lecture du corps par un handler. Aucune divergence trouvée.

Je n'ai pas pu reproduire en `MockMvc` le cas `%zz` (encodage invalide). `StringUtils.uriDecode` lèverait une exception dans `shouldNotFilter`, mais Tomcat rejette ces URI en 400 avant la chaîne de servlet, et `DispatcherServlet` fait le même parsing. Pas de nouveau comportement introduit, donc pas de constat.

## Nouveaux constats (code modifié depuis le round 1)

Aucun. J'ai relu sur le code modifié :
- la mémorisation stream/reader (`LimitedRequest`) ;
- `LiveSnapshotFixtures.realisticMatch` / `oversizedMatch` ;
- les nouveaux IT de `LiveStatsControllerIT` et `LiveSessionControllerIT` ;
- les nouveaux tests unitaires.

Le non-respect de l'`IllegalStateException` du spec servlet (mélange `getInputStream()`/`getReader()`) est assumé par le développeur. Il est sans consommateur et sans impact sur la garde : même compteur.

Remarque sans gravité : la découverte du `SnapshotBodySizeFilter.class` périmé dans `target/` invalide le « 288 tests verts » du round 0 côté PUT, mais pas le résultat actuel (`./mvnw clean test`, 302 verts). La CI part d'un checkout propre.

## IDs encore ouverts
- CRIT-003 : reporté, à arbitrer par l'humain (création d'un ticket de dette « bornes de longueur des champs texte du payload live »). Non bloquant pour MV-004.
