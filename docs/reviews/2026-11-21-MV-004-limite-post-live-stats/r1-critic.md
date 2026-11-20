---
type: critic-review
round: 1
bloquants: 1
majeurs: 1
mineurs: 4
---
# Revue critique — MV-004 : limite de 1 Mo sur POST /matches/{id}/live-stats

Périmètre : `git diff 9e68a69` (hors CLAUDE.md, .gitignore, .claude/, docs/architecture/) et fichiers non suivis `LivePayloadSizeFilter.java`, `LivePayloadSizeFilterTest.java`.

Ce que j'ai vérifié et qui tient :
- **Ordre des filtres et CA-5.** La chaîne Spring Security passe avant le filtre de taille. Une requête sans JWT reçoit un 401 de l'entry point sans lecture du corps. Le dispatch `/error` qui suit n'est pas filtré, parce que `OncePerRequestFilter` ignore les dispatch d'erreur par défaut. J'accepte la justification.
- **Renommage et message générique.** Je ne relève aucune référence restante sous `src/` ni dans les docs front. Les seules occurrences de `SnapshotBodySizeFilter` sont dans `target/` (artefacts AOT périmés, régénérés au build).
- **Bornes.** `> MAX_BYTES` est appliqué de façon cohérente en Content-Length et en chunked : 1 048 576 octets passent, 1 048 577 sont rejetés. Le test unitaire de la borne est correct.
- **Chemin chunked.** `PayloadTooLargeException` est une `RuntimeException` levée pendant la lecture Jackson, dans le `DispatcherServlet`, et `GlobalExceptionHandler` la traduit en 413 enveloppé. L'IT chunked est un vrai chunked : la longueur -1 est vérifiée.

Mais la justification « le firewall rend la regex incontournable » est fausse, et le contournement est trivial.

### CRIT-001 — La regex sur `getRequestURI()` brut se contourne par un simple encodage `%XX` : la limite ne protège pas contre un client malveillant [BLOQUANT]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/config/LivePayloadSizeFilter.java:35-41` (et `:31-32`)
- **Constat** : `shouldNotFilter` compare les regex à `request.getRequestURI()`, c'est-à-dire l'URI **non décodée**. Spring MVC route sur le chemin **décodé** : `PathPattern` compare `PathSegment.valueToMatch()`, qui vaut `StringUtils.uriDecode(segment)` dans `DefaultPathContainer:128`. `StrictHttpFirewall` ne bloque qu'une liste fermée d'encodages : `%2F`, `%2E`, `%25`, `%3B`, `%5C`, `%00`, `%0A`, `%0D` (`StrictHttpFirewall:90-113`). `%61` (`a`), `%6C` (`l`), etc. passent.
- **Scénario de défaillance** : un utilisateur authentifié (l'inscription est ouverte) envoie `POST /api/v1/matches/x/live-st%61ts` avec un corps de 500 Mo.
  - Le firewall accepte la requête.
  - La regex ne la reconnaît pas, donc `shouldNotFilter` renvoie `true` et le corps n'est pas plafonné.
  - Spring MVC route vers `LiveStatsController#saveLiveStats`, et Jackson désérialise les 500 Mo en mémoire.
  - Résultat obtenu : le risque d'OOM que le ticket veut supprimer est intact, avec un chemin d'attaque d'une ligne. Résultat attendu : 413.
  - Même contournement sur `PUT /api/v1/live-sessions/{id}/sn%61pshot`. Il existait déjà avant MV-004, mais c'est désormais le même code.
- **Preuve** : j'ai lancé un programme jetable (scratchpad, hors projet) sur le classpath de test du projet et la classe compilée `target/classes`. Il utilise `MockHttpServletRequest` avec un corps de 1 Mo + 10 000 octets, `new StrictHttpFirewall().getFirewalledRequest(req)`, `PathPatternParser.defaultInstance.parse(<mapping>).matches(RequestPath.parse(uri, null).pathWithinApplication())`, puis `new LivePayloadSizeFilter().doFilter(...)`. Sortie :
  ```
  /api/v1/matches/m1/live-stats          firewall=accepted mvcRoute=true filterStatus=413 chainCalled=false
  /api/v1/matches/m1/live-st%61ts        firewall=accepted mvcRoute=true filterStatus=200 chainCalled=true
  /api/v1/matches/m1/%6Cive-stats        firewall=accepted mvcRoute=true filterStatus=200 chainCalled=true
  /api/v1/live-sessions/abc/sn%61pshot   firewall=accepted mvcRoute=true filterStatus=200 chainCalled=true
  ```
  Test IT qui échouerait aujourd'hui : `should_return413_when_post_path_is_percent_encoded_and_body_exceeds_one_megabyte`. Il envoie `oversizedBody` sur `/api/v1/matches/enc/live-st%61ts` avec un `RestTemplate`/`HttpClient` configuré pour ne pas ré-encoder l'URI (par exemple `URI.create(...)` passé tel quel) et attend 413. Avec la fixture actuelle, il obtient 500 (titre > 255, voir CRIT-002) au lieu de 413. Le cas unitaire équivalent, `"POST, /api/v1/matches/m1/live-st%61ts"` dans `should_filter_when_request_is_live_payload_upload`, renvoie `shouldNotFilter == true`.
- **Correction proposée** : faire matcher le filtre avec la **même sémantique que Spring MVC**, pas avec une regex sur l'URI brute. Par exemple :
  - `PathPattern` précompilés (`PathPatternParser.defaultInstance.parse("/api/v1/matches/{matchId}/live-stats")`), appliqués à `RequestPath.parse(request.getRequestURI(), request.getContextPath()).pathWithinApplication()` ;
  - ou à défaut `UrlPathHelper` avec décodage, ou `request.getServletPath()` (décodé et normalisé par Tomcat avec le `DispatcherServlet` sur `/`).

  Ajouter les cas encodés (`%61`, `%6C`, majuscules/minuscules d'hex) aux tests unitaires « filtré », et une IT de bout en bout.
- **Question au développeur** : la note affirme que « la regex ne peut pas être contournée par ces variantes de chemin ». Quelles variantes avez-vous testées en dehors de `;`, `//` et `..` ? Si vous maintenez la regex sur `getRequestURI()`, montrez que l'URI ci-dessus n'atteint pas le contrôleur dans l'application réelle.

### CRIT-002 — Fixture des tests de rejet impossible à persister : « rien n'est persisté » (CA-1, CA-2, CA-5) n'est pas prouvé, et la cause du 500 n'a pas été cherchée [MAJEUR]
- **Localisation** : `src/test/java/vasconcelos/volleymatch/LiveStatsControllerIT.java`, `oversizedBody(...)` et sa Javadoc « would pass validation », utilisée par les 3 tests de rejet
- **Constat** : la fixture gonfle le payload avec un `title` de plus d'un million de caractères. Or `matches.title` est en `VARCHAR(255)` (`V1__init.sql:41`, `@Column(name = "title")` donc longueur 255 en `create-drop`), et `LiveStatsPayloadValidator` ne contrôle pas `title`. Ce payload ne peut donc **jamais** être enregistré : sans le filtre, il produit une `DataIntegrityViolationException` dans `matchRepository.save`, sans handler, d'où un 500. C'est précisément le « 500 non investigué » du cycle TDD. Le développeur a observé le symptôme sans voir que sa fixture était invalide.
- **Scénario de défaillance** : on retire la vérification `findById(...).isEmpty()` / `GET → 404`, ou on casse le filtre de façon à laisser passer la requête jusqu'au service sans changer le statut (par exemple un futur refactoring qui intercepte l'exception et réécrit 413 après l'appel à la chaîne). Les assertions de non-persistance resteraient vertes, puisque la base refuse ce titre de toute façon. Le volet « rien n'est persisté » des CA-1, CA-2 et CA-5 est vrai par construction de la fixture, pas grâce au filtre. La Javadoc « would pass validation » est trompeuse : le payload passe le validateur, mais pas la persistance.
- **Preuve** : raisonnement sur le schéma et le validateur cités. Le résultat TDD rapporté par le développeur le confirme : 500 et non 201 avant le correctif.
- **Correction proposée** : construire la fixture surdimensionnée à partir d'un payload **réellement persistable**. Par exemple `realisticMatch(id, 10, 340)` sérialisé (environ 1,08 Mo), ou un payload valide complété par un champ inconnu ignoré par Jackson, en gardant un `title` court. Ajouter une assertion de garde qui prouve qu'un payload de la même forme sous 1 Mo est accepté (201). Le test de rejet montre alors que c'est bien la limite qui empêche la persistance.
- **Question au développeur** : sans le filtre, votre fixture aurait-elle été enregistrée ? Si non, qu'est-ce qui, dans vos tests, prouve que c'est la limite de 1 Mo, et pas la contrainte de colonne, qui garantit « rien n'est persisté » ?

### CRIT-003 — Bug préexistant révélé : `title` (et probablement `teamName`/`venue`) de plus de 255 caractères donne un 500 non enveloppé sur le POST [MINEUR, hors périmètre]
- **Localisation** : `LiveStatsPayloadValidator` (aucune règle sur `title`) ; `Match.java:38-39` ; `V1__init.sql:41`
- **Constat** : un POST valide dont le titre fait 256 caractères (environ 300 octets, rien à voir avec la limite de 1 Mo) produit un 500 hors enveloppe `ApiResponse`. Ce n'est pas causé par MV-004, mais c'est la vraie cause du 500 observé, et la note la classe « non investiguée ».
- **Scénario de défaillance** : `title = "x".repeat(256)` → 500 au lieu de 400. Le POST étant la sauvegarde finale, le coach perd son match sur un message d'erreur opaque.
- **Preuve** : schéma et validateur cités. Test à écrire : `should_return400_when_title_exceeds_255_characters`, qui échoue aujourd'hui avec 500.
- **Correction proposée** : hors ticket. Ouvrir une dette « bornes de longueur des champs texte du payload live (400) ». Ne pas l'absorber dans MV-004.
- **Question au développeur** : confirmez-vous la cause, et acceptez-vous qu'elle fasse l'objet d'un ticket ?

### CRIT-004 — La « marge de 9 % » est mesurée avec un `JsonMapper` par défaut, pas avec le sérialiseur qui envoie [MINEUR]
- **Localisation** : `LiveStatsControllerIT.java`, `serializedSize(...)` et les tests 5×100 / 10×300 ; note d'implémentation, section « Mesure »
- **Constat** : la taille est calculée par `JsonMapper.builder().build()`. Le corps réellement envoyé passe par le convertisseur de `RestTemplate`, et en production par `JSON.stringify` côté front. Les deux peuvent différer sur les `null` (`malusAmount: null`, `liveSessionId`), le format d'`Instant` ou les espaces. Le 201 prouve que le corps envoyé faisait au plus 1 Mo, mais les 953 101 octets et la marge de 9 % ne décrivent pas le payload du front.
- **Scénario de défaillance** : le front sérialise des champs que le test omet, ou l'inverse. La marge réelle du pire cas est alors inconnue, alors que la note s'appuie sur elle pour juger le risque de perdre un match.
- **Preuve** : lecture du code de test.
- **Correction proposée** : formuler la marge comme une estimation dans la note. Si possible, mesurer un export réel du front (CA-3 parle d'une « fixture réelle »).
- **Question au développeur** : la fixture a-t-elle été comparée à un vrai payload du front ?

### CRIT-005 — `LimitedRequest` n'enveloppe pas `getReader()` [MINEUR]
- **Localisation** : `LivePayloadSizeFilter.java:56-66`
- **Constat** : seul `getInputStream()` est plafonné. Un consommateur qui lirait le corps par `getReader()` contournerait la garde chunked. Aujourd'hui, `ServletServerHttpRequest.getBody()` passe par `getInputStream()`, donc pas d'impact démontré.
- **Scénario de défaillance** : ajout futur d'un `@RequestBody String`, d'un filtre de log du payload ou d'un convertisseur qui passe par le `Reader`. Le corps chunked n'est alors plus borné.
- **Correction proposée** : surcharger `getReader()` en construisant un `BufferedReader` sur le `LimitedInputStream`, avec l'encodage de la requête. Optionnel.
- **Question au développeur** : aucune si vous l'écartez explicitement.

### CRIT-006 — Assertion d'enveloppe faible sur le PUT (CA-4) [MINEUR]
- **Localisation** : `LiveSessionControllerIT.java:244`
- **Constat** : `contains("\"status\":413")` ne vérifie ni `data: null` ni le nouveau message. Or c'est justement le texte qui a changé et le comportement « rendu générique » visé par CA-4. Le POST, lui, a une assertion complète via `assertTooLargeAndNotPersisted`.
- **Correction proposée** : désérialiser en `ApiResponse` et vérifier `data` null, `status` 413 et `message` égal à « Request body exceeds 1 MB », comme pour le POST.
- **Question au développeur** : aucune.

## Couverture des CA
| CA | Statut critic |
|----|---------------|
| CA-1 | 413 prouvé sur le chemin nominal. **Contournable** (CRIT-001). « Rien n'est persisté » non prouvé par le test (CRIT-002) |
| CA-2 | Idem CA-1 (fixture et contournement identiques) |
| CA-3 | Couvert (5×100 et 10×300 en 201) |
| CA-4 | Couvert, assertion de message absente (CRIT-006) |
| CA-5 | Couvert (401). Même faiblesse de fixture pour la non-persistance, sans impact pratique : le 401 précède la lecture |
| CA-6 | Couvert pour les URI littérales ; le test unitaire ignore les URI encodées (CRIT-001) |
| CA-7 | Respecté |
| CA-8 | Respecté |
