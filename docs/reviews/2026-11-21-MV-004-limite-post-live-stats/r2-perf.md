---
type: perf-review
round: 2
bloquants: 0
majeurs: 0
mineurs: 0
---
# Revue performance, round 2 : MV-004

Code relu : `LivePayloadSizeFilter.java` (version actuelle), `r1-reponse.md`. Je n'ai pas relancé de test (302 verts selon l'orchestrateur) et je n'ai pas mesuré moi-même : les chiffres de coût ci-dessous sont des ordres de grandeur issus de la lecture du code.

| ID | Verdict | Justification |
|----|---------|---------------|
| PERF-001 | VALIDÉ | `getInputStream()` mémorise le flux (`if (stream == null)`), `getReader()` passe par `getInputStream()`, donc un seul compteur par requête. Coût : 2 champs, 2 tests de nullité. Lecture : un second appel ou le reader ne remet plus le compteur à 0. Le test dédié existe. Pas de régression d'isolation : le wrapper ne touche à aucune donnée utilisateur. |
| PERF-002 | ACCEPTÉ | Le report est fondé : décision Q1 du ticket, et `application*.properties` est interdit sans demande explicite. Aucun chiffre nouveau de ma part. Le risque reste un risque documenté sur des DTO de quelques centaines d'octets. À traiter dans le ticket de dette éventuel. |

## Coût réel du passage à PathPattern

### Chemin chaud `GET /api/v1/public/live/{code}` : inchangé
- `shouldNotFilter` : `HttpMethod.PUT.matches(method)` puis `HttpMethod.POST.matches(method)`, soit deux comparaisons de chaînes, puis `return true`. Ni `RequestPath.parse`, ni `PathPattern.matches`, ni allocation. C'est un coût du même ordre que la version regex (qui sortait aussi sur la méthode), donc zéro différence mesurable à 120 req/min par client (~2/s).
- Ce constat vaut pour tous les GET/DELETE/OPTIONS et pour toute requête non PUT/POST.

### PUT et POST (toutes routes confondues)
- Ils paient un `RequestPath.parse(uri, contextPath)` (quelques objets : `RequestPath`, `PathContainer` avec liste de segments, un par segment, soit 5 à 6 pour `/api/v1/matches/{id}/live-stats`) puis un `PathPattern.matches`, avec patterns précompilés (`static final`).
- Ordre de grandeur : de 1 à 5 microsecondes et de l'ordre de 1 Ko d'allocations courtes par requête, contre moins de 1 microseconde pour la regex. Surcoût d'environ 1 à 4 microsecondes.
- Fréquence : ces requêtes sont les écritures authentifiées (sauvegardes de snapshot du PUT, qui sont les plus fréquentes, de l'ordre de une toutes les quelques secondes par match en direct au plus, et POST ponctuels). Au pire quelques requêtes par seconde pour tout le système : un surcoût cumulé de moins de 20 microsecondes par seconde. Négligeable devant la désérialisation Jackson et l'écriture en base de la même requête (millisecondes à dizaines de millisecondes pour un corps de 100 à 1000 Ko).
- Le filtre passe après la chaîne Spring Security : un appel sans JWT est déjà rejeté en 401 et ne paie pas ce parsing.
- Sans JIT en native : le parsing est du code AOT déjà froid, mais il s'exécute en quelques microsecondes dans tous les cas. Les `PathPattern` et le `PathPatternParser.defaultInstance` n'utilisent pas de réflexion, donc pas de hint natif nécessaire (le code de Spring MVC les utilise déjà pour son routage).
- Conclusion : le surcoût de CRIT-001 est justifié et n'est pas un constat de performance. Je retire mon argument du round 1 (« ne pas remplacer par PathPattern ») : l'argument de correction l'emporte, et le coût est négligeable à l'échelle réelle.

## Nouveaux constats sur le code modifié
Aucun constat bloquant, majeur ni mineur.

Remarque sans gravité : `RequestPath.parse` décode lazily les segments, et une séquence `%` invalide pourrait lever `IllegalArgumentException` dans le filtre. `StrictHttpFirewall` rejette les encodages malformés en amont (chaîne Spring Security avant ce filtre), donc il n'y a pas de 500 attendu. Si le reviewer critique veut une certitude, un test avec `%zz` suffirait. Je ne l'exige pas.

Pas de nouveaux problèmes de mémoire : un `BufferedReader` n'est créé que si `getReader()` est appelé, ce qui ne se produit pas sur ces routes aujourd'hui (Jackson utilise `getInputStream()`).

## Conclusion
Tous les IDs perf sont fermés (PERF-001 VALIDÉ, PERF-002 ACCEPTÉ). Aucun ID ouvert.
