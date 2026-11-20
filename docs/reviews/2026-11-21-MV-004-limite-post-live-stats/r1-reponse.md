# Réponse développeur — round 1

| ID | Position | Résumé |
|----|----------|--------|
| CRIT-001 | CORRIGÉ | Le matching passe par `PathPattern` sur le chemin décodé (sémantique Spring MVC) ; contournement `%61` reproduit en IT (201/200) puis corrigé (413) sur POST et PUT |
| CRIT-002 | CORRIGÉ | La fixture surdimensionnée est un match valide et persistable (titre court, 10 × 360 events) ; filtre neutralisé, les 3 IT de rejet authentifiés passent bien à 201 |
| CRIT-003 | REPORTÉ | Cause du 500 confirmée (`matches.title VARCHAR(255)`, aucun contrôle de longueur) ; dette hors MV-004 (= ARCH-D01) |
| CRIT-004 | CORRIGÉ | La note présente la marge comme une estimation sur fixture, pas comme une mesure d'un payload front ; pas d'export réel disponible |
| CRIT-005 | CORRIGÉ | `getReader()` est enveloppé sur le même flux compté |
| CRIT-006 | CORRIGÉ | Le test PUT 413 vérifie l'enveloppe complète (`data`, `status`, `message`) |
| PERF-001 | CORRIGÉ | Le flux est mémorisé : un seul compteur par requête, `getReader()` inclus |
| PERF-002 | REPORTÉ | Plafond global hors périmètre (décision Q1), modification des properties interdite |
| ARCH-001 | CORRIGÉ | `realisticMatch` et `oversizedMatch` déplacés dans `LiveSnapshotFixtures` |

## CRIT-001 — CORRIGÉ
- **Constat reconnu.** Mon affirmation de la note (« la regex ne peut pas être contournée ») était fausse : je n'avais considéré que `;`, `//` et `..`, pas les encodages que `StrictHttpFirewall` laisse passer. Je l'ai reproduit en IT avant correction : `POST /api/v1/matches/post-encoded-too-large/live-st%61ts` avec 1,14 Mo → **201, match enregistré** ; `PUT /live-sessions/{id}/sn%61pshot` → **200**.
- **Correctif** : `src/main/java/vasconcelos/volleymatch/config/LivePayloadSizeFilter.java:42-61`. Deux `PathPattern` précompilés (`PathPatternParser.defaultInstance.parse("/api/v1/matches/{matchId}/live-stats")` et `.../live-sessions/{sessionId}/snapshot`) sont appliqués à `RequestPath.parse(getRequestURI(), getContextPath()).pathWithinApplication()`.
- **Pourquoi `PathPattern` plutôt que `getServletPath()`** : c'est exactement le mécanisme de routage de Spring MVC (`ServletRequestPathUtils` → `RequestPath` ; `PathSegment.valueToMatch()` décode chaque segment et retire les paramètres matriciels). Le filtre et le contrôleur ne peuvent donc pas diverger.
  - `getServletPath()` est décodé par Tomcat, mais sa valeur dépend du mapping du `DispatcherServlet` (`/` aujourd'hui) et des règles de normalisation du conteneur, pas de celles de Spring MVC.
  - En test unitaire, `MockHttpServletRequest` laisse `servletPath` vide : le test aurait validé une valeur artificielle.
  - `/live-stats;jsessionid=x` est aussi filtré désormais (le firewall le rejette déjà, mais le filtre ne dépend plus de cette défense).
- **Tests** :
  - `LivePayloadSizeFilterTest#should_filter_when_live_payload_upload_path_is_percent_encoded` (8 cas) : `live-st%61ts`, `%6Cive-stats`, `%6cive-stats` (hex majuscule et minuscule), `/api/v%31/%6D%61tches/...`, matchId encodé `live%20match`, `;jsessionid`, `sn%61pshot`, `%73napshot`. 7 cas sur 8 étaient rouges avant correction ; le matchId encodé passait déjà.
  - `LivePayloadSizeFilterTest#should_filter_when_application_has_context_path` : le chemin est bien relatif à l'application.
  - `LiveStatsControllerIT#should_return413_and_persist_nothing_when_post_path_is_percent_encoded_and_body_exceeds_one_megabyte` : de bout en bout via `HttpClient` + `URI.create` (l'URI brute est vérifiée : `getRawPath()` se termine par `%61ts`). 413, enveloppe, rien en base, `GET` → 404. Rouge avant (201).
  - `LiveStatsControllerIT#should_return201_when_post_path_is_percent_encoded_and_body_is_under_one_megabyte` : garde. La même route encodée atteint bien le contrôleur et enregistre (201), donc le 413 ci-dessus vient de la limite, pas d'un 404.
  - `LiveSessionControllerIT#should_return413_and_keep_snapshot_when_snapshot_path_is_percent_encoded_and_body_exceeds_one_megabyte` : 413, enveloppe, `revision` reste 0 et `snapshot` null. Rouge avant (200).
  - Les 10 cas « non filtré » de CA-6 restent verts.

## CRIT-002 — CORRIGÉ
- **Réponse à la question** : non, l'ancienne fixture n'aurait jamais été enregistrée. Le titre de plus d'un million de caractères dépasse `matches.title VARCHAR(255)`, d'où le 500 du round 0. Les assertions « rien n'est persisté » étaient vraies par construction.
- **Correctif** :
  - `src/test/java/vasconcelos/volleymatch/LiveSnapshotFixtures.java:130` : `oversizedMatch(matchId)` = `realisticMatch(matchId, 10, 360)`. Match valide, titre court, il ne dépasse 1 Mio que par son nombre d'events (~1,14 Mo).
  - `LiveStatsControllerIT.java:501` : `oversizedBody` sérialise ce match et vérifie lui-même que le corps fait plus de 1 Mio. Javadoc corrigée (« valid, persistable… exceeds 1 MiB only by its number of events »).
  - Utilisée par les IT de rejet : Content-Length, chunked, URI encodée, sans JWT. Le nouvel IT PUT encodé utilise aussi `oversizedMatch`.
- **Vérification demandée** : j'ai neutralisé temporairement le filtre (`if (true) return true;` en tête de `shouldNotFilter`), puis restauré la copie de sauvegarde (vérifié : 0 occurrence restante, `./mvnw clean test` relancé ensuite). Résultat avec filtre neutralisé :
  ```
  LiveStatsControllerIT.should_return413_envelope_and_persist_nothing_when_post_content_length_exceeds_one_megabyte  expected: 413 but was: 201
  LiveStatsControllerIT.should_return413_envelope_and_persist_nothing_when_chunked_post_exceeds_one_megabyte         expected: 413 but was: 201
  LiveStatsControllerIT.should_return413_and_persist_nothing_when_post_path_is_percent_encoded_...                   expected: 413 but was: 201
  LiveSessionControllerIT.should_return413_and_keep_snapshot_when_snapshot_path_is_percent_encoded_...              expected: 413 but was: 200
  LiveSessionControllerIT.should_return413_when_snapshot_body_exceeds_one_megabyte                                   expected: 413 but was: 400
  ```
  Sans le filtre, le payload est donc enregistré (201). Le « rien n'est persisté » des tests de rejet authentifiés prouve maintenant l'effet de la limite.
- **Limite assumée** : le test sans JWT (CA-5) reste vert sans filtre (401 avant lecture). C'est attendu : le CA accepte 401, et ce test prouve « jamais 500 ni 201 », pas l'effet du filtre. Le test historique du PUT (`{"title":"xxx…"}`, CA-4) donnerait 400 sans filtre. Je ne l'ai pas modifié au-delà de l'assertion d'enveloppe (CA-4 : « pas l'intention du test »). La preuve forte côté PUT est le nouvel IT encodé, avec fixture valide.

## CRIT-003 — REPORTÉ
- **Cause confirmée** : `V1__init.sql:41` (`title VARCHAR(255)`) et `LiveStatsPayloadValidator` sans contrôle de longueur. Le 500 du round 0 venait de là (voir la neutralisation de CRIT-002 : avec une fixture au titre court, le même chemin donne 201).
- **Pourquoi reporté** : c'est un bug préexistant, indépendant de la taille du corps. Il touche le validateur partagé POST/PUT et sort de la décision Q1. C'est le même constat que ARCH-D01. Je recommande l'option A de l'architecte : un ticket de dette « bornes de longueur des champs texte du payload live (400) ». Arbitrage humain.

## CRIT-004 — CORRIGÉ
- **Correctif** : `00-implementation.md`, section Tests (ligne « Mesure ») et Points d'attention. La marge de ~9 % est présentée comme une estimation mesurée avec un `JsonMapper` Jackson 3 par défaut sur une fixture, pas sur un export du front.
- **Réponse à la question** : non, la fixture n'a pas été comparée à un vrai payload du front. Le dépôt backend n'en contient pas, et `live-stats-api-contract.md` ne donne qu'un exemple de 2 sets. Comme la marge sert à juger le risque de perdre un match, je suggère de mesurer un export réel côté front avant déploiement. C'est déjà le point « Risques » du ticket.
- **Remarque** : les corps des IT de rejet sont désormais sérialisés par le test lui-même (`oversizedBody`). La taille envoyée (> 1 Mio) est donc exactement celle qui est assertée.

## CRIT-005 — CORRIGÉ
- **Correctif** : `LivePayloadSizeFilter.java:97-105`. `getReader()` renvoie un `BufferedReader` mémorisé sur le `LimitedInputStream` de la requête. L'encodage est celui de la requête ; à défaut, UTF-8, l'encodage JSON de RFC 8259, plutôt que l'ISO-8859-1 par défaut du servlet, inadapté au JSON.
- **Test** : `LivePayloadSizeFilterTest#should_throw_payload_too_large_when_chunked_body_read_through_reader_exceeds_one_megabyte`, rouge avant correction.
- **Limite** : le wrapper ne reproduit pas l'`IllegalStateException` du spec servlet en cas de mélange `getInputStream()`/`getReader()`. Les deux partagent le même compteur, ce qui est la propriété qui compte ici. Aucun consommateur actuel ne mélange les deux.

## CRIT-006 — CORRIGÉ
- **Correctif** : `LiveSessionControllerIT.java:244-256`. Le corps est désérialisé en `ApiResponse` et `assertTooLargeEnvelope` vérifie `data` null, `status` 413 et `message` « Request body exceeds 1 MB ». Le même helper sert à l'IT PUT encodé.

## PERF-001 — CORRIGÉ
- **Correctif** : `LivePayloadSizeFilter.java:80-106`. `getInputStream()` crée le `LimitedInputStream` une seule fois, donc un seul compteur par requête, et `getReader()` lit ce même flux (CRIT-005). Coût : 2 champs et 2 tests `null`.
- **Test** : `LivePayloadSizeFilterTest#should_keep_counting_across_calls_when_input_stream_is_requested_twice`. 512 Kio lus par un premier appel, puis `readAllBytes()` sur un second appel lève `PayloadTooLargeException`. Rouge avant correction (compteur remis à 0).
- **Réponse à la question** : sur ces deux routes, seul le convertisseur Jackson de `@RequestBody` lit le corps. `RequestLoggingConfig.java:14` fait `setIncludePayload(false)`, donc il ne lit pas le corps. `grep "ContentCaching|getReader|getInputStream" src/main` ne renvoie rien en dehors du filtre.

## PERF-002 — REPORTÉ
- Fondé mais hors périmètre : la décision Q1 limite le ticket au POST live-stats, et la section « Hors périmètre » exclut teams/players/auth. Un plafond Tomcat global passe par `application*.properties`, interdit sans demande explicite (CLAUDE.md, Interdits). À arbitrer par l'humain, éventuellement dans le même ticket de dette que CRIT-003.

## ARCH-001 — CORRIGÉ
- **Correctif** : `LiveSnapshotFixtures.java:109-152`. `realisticMatch(matchId, setCount, eventsPerSet)` réutilise `set(...)` ; s'y ajoutent `oversizedMatch(matchId)` et un `realisticEvent` privé. La méthode privée de `LiveStatsControllerIT` est supprimée. `oversizedBody` (JSON brut) reste local à l'IT, comme suggéré.
- **Critère** : `grep -rn "realisticMatch" src/test` ne trouve la définition que dans `LiveSnapshotFixtures.java`. Les usages sont `LiveSnapshotFixtures.realisticMatch(...)` dans l'IT. `oversizedMatch` est réutilisé par `LiveSessionControllerIT`.
- Effet de bord : `set(...)` met les scores du set à 0-0 au lieu de 25-23, soit environ 3 octets de moins par set. C'est négligeable et le validateur ne contrôle pas les scores. Les assertions de taille (5×100 dans [100 000 ; 1 Mio], 10×300 < 1 Mio, oversized > 1 Mio) restent vraies.

## Conflits entre relecteurs
- **CRIT-001 contre PERF (point 1, « ne pas remplacer par un `PathPattern` »)**. Perf jugeait le `PathPattern` inutile pour des raisons de performance. Critic l'exige pour une raison de correction. La correction l'emporte : une limite contournable ne protège de rien. Compromis qui préserve l'argument perf : le test de méthode reste en tête et court-circuite. Seuls les PUT et POST paient un `RequestPath.parse` + un `PathPattern.matches` (patterns précompilés), soit quelques microsecondes. Le chemin chaud public `GET /public/live/{code}` sort à la première comparaison, sans aucun parsing, comme avant.
- Aucun autre conflit.

## Remarque hors constats — classe périmée dans `target/`
En neutralisant le filtre (CRIT-002) **sans `clean`**, le PUT historique répondait encore 413 « Snapshot exceeds 1 MB ». L'ancien `SnapshotBodySizeFilter.class` (et son `__BeanDefinitions` AOT) était resté dans `target/classes` après le renommage, et Spring le chargeait toujours comme `@Component`. Le vert du round 0 sur le PUT était donc en partie dû à l'ancien filtre. Le POST n'était pas concerné : l'ancien filtre ne le couvrait pas. Tous les résultats ci-dessous viennent de `./mvnw clean test`, et `target/` ne contient plus aucune trace de `SnapshotBodySizeFilter`. La CI part d'un checkout propre, donc pas d'impact sur le déploiement.

## Résultat `./mvnw test`
`./mvnw clean test` : `Tests run: 302, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`
