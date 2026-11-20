---
type: critic-review
round: 1
bloquants: 0
majeurs: 0
mineurs: 3
---
# Revue critique — MV-003 Exécuter les tests d'intégration par défaut et en CI avant le déploiement

Périmètre relu : `git diff 8712564 -- pom.xml .github/workflows/build.yml`, section « Commandes » de CLAUDE.md, note `00-implementation.md`, ticket MV-003 (CA-1 à CA-10).

## Points vérifiés sans constat (preuves)

| Sujet | Vérification |
|---|---|
| `mvnw` exécutable sur le runner | `git ls-files -s mvnw` → `100755`. `.gitattributes` : `/mvnw text eol=lf`, et `git show HEAD:mvnw` commence par `#!/bin/sh\n` (pas de `\r`). Pas de `/bin/sh^M` possible au checkout Linux. |
| Wrapper « only-script » | `.mvn/wrapper/maven-wrapper.properties` : `distributionType=only-script`, Maven 3.9.14 téléchargé par curl/wget (présents sur `ubuntu-latest`), pas de `distributionSha256Sum` à valider. `JAVA_HOME` est posé par `setup-java`. |
| Service container | Job exécuté sur l'hôte (pas de `container:`), donc `localhost:5433` correspond bien au mapping `5433:5432`. DB, user et mot de passe identiques à `application-test.properties:1-3` et au service `postgres-test` de `docker-compose.yml`. |
| Healthcheck | `pg_isready -U postgres -d volleymatch_test`, 10 × 5 s. Cas limite examiné : pendant l'init de l'image officielle, un serveur temporaire n'écoute que sur le socket unix et `pg_isready` (socket) peut répondre « prêt » avant le redémarrage final. Je n'en fais pas un constat : le redémarrage dure environ 1 s, alors que la première connexion JDBC arrive après le checkout, `setup-java`, le téléchargement de Maven et des dépendances, puis la compilation (plusieurs dizaines de secondes). Aucun scénario réaliste. |
| `needs:` / CA-6 | `build-and-push.needs: test` : si `test` échoue ou est annulé, le job natif est sauté, donc `docker/build-push-action` ne s'exécute pas. Hypothèse « Railway tire l'image Docker Hub » confirmée par le dépôt : `DockerfileRailway` fait `COPY target/volleymatch` (binaire natif pré-construit, `target/` ignoré par git), donc Railway ne peut pas builder depuis le repo. |
| CA-7 | Déclencheur `on.push.branches: [master]` inchangé, pas de `pull_request`. |
| CA-8 | Aucun `${{ secrets.* }}` dans le job `test`. Les `@Value` sans défaut du code (`app.jwt.*`, `app.base-url`, `resend.*`) sont toutes résolues par `application.properties` (avec défauts `${X:...}`) ou `application-test.properties`. |
| Includes surefire / CA-2 | Les 4 includes par défaut de surefire 3.x sont bien répétés + `**/*IT.java`. Fichiers de test non matchés : `LiveSessionITSupport.java` et `LiveSnapshotFixtures.java` (des supports, pas des tests : c'est voulu). `BaseIT` est abstraite. `process-aot` est lié à `prepare-package`, après `test` : rien de natif ne s'exécute dans `./mvnw test`. Aucun autre changement dans `pom.xml`. |
| CA-1 / CA-4 | 268 = 268 (aucune classe `*Tests`/`Test*`/`*TestCase` ne crée d'écart entre les deux commandes). `-Dtest` est prioritaire sur `<includes>`. |
| Ordre d'exécution sous Linux | Le `runOrder` par défaut de surefire (`filesystem`) donnera sur ext4 un ordre différent de l'ordre alphabétique NTFS vérifié en local. J'ai cherché une dépendance d'ordre. Il existe 2 contextes Spring (le contexte par défaut, et celui de `PublicLiveRateLimitIT` via `@TestPropertySource`), tous deux en `create-drop` sur la **même** base : démarrer le second vide la base du premier, qui reste en cache. C'est sans effet : les ids sont en `IDENTITY`/`UUID`, donc aucun pool de séquence en mémoire ne peut entrer en collision. `BaseIT` réenregistre son user à chaque `@BeforeEach`. Aucun IT n'asserte une taille de liste globale de l'utilisateur partagé (`DataIsolationIT:40` utilise `noneMatch`). Pas d'exécution parallèle configurée. En local, `TeamControllerIT` tourne déjà après le vidage et passe. Pas de constat. |
| CA-9 | La ligne `./mvnw test` de « Commandes » est exacte après le changement (unitaires + `*IT`, Postgres requis, lancé par la CI avant le build natif). |
| CA-3 / CA-10 | Aucun fichier `src/` modifié (git status). Aucun test supprimé ni désactivé. |

Aucun CA non couvert ou mal interprété.

---

### CRIT-001 — `PublicLiveRateLimitIT` échoue de façon déterministe si ses 4 requêtes chevauchent une frontière de minute, et la note se trompe de cause [MINEUR]
- **Localisation** : `src/test/java/vasconcelos/volleymatch/PublicLiveRateLimitIT.java:24-35`, `src/main/java/vasconcelos/volleymatch/config/PublicRateLimiter.java:33-38`. La justification attaquée est dans `00-implementation.md`, « Points d'attention » (« stable en local sur 3 runs », « risque sur runner lent »).
- **Constat** : le limiteur utilise une fenêtre fixe **alignée sur la minute epoch** (`windowId = now / 60_000`), pas une fenêtre glissante qui partirait de la première requête. Le test envoie 3 requêtes, puis attend un 429 sur la 4e.
- **Scénario de défaillance** : les requêtes 1 à 3 partent à `hh:mm:59.98` (fenêtre N) et la 4e à `hh:(mm+1):00.01` (fenêtre N+1, compteur remis à 1). La 4e est autorisée et renvoie 404 au lieu de 429 : `assertThat(blocked.getStatusCode()).isEqualTo(TOO_MANY_REQUESTS)` échoue, le job `test` est rouge, le déploiement est bloqué. Probabilité par run ≈ durée des 4 requêtes / 60 s (de l'ordre de 0,05 à 0,5 % selon le runner). C'est rare, mais ça arrivera sur la durée de vie de `master`, et un runner rapide n'y échappe pas non plus.
- **Preuve** : raisonnement sur le code cité (`current.id() != windowId` → `new Window(windowId, 1)`). Pour le reproduire de façon déterministe, il faut un test qui fixe le `Clock` à `59_990 ms` modulo 60 000 pour les 3 premiers appels, puis à `60_010 ms` pour le 4e : il obtient `0` (autorisé) au lieu d'un `Retry-After`. Trois runs locaux ne disent rien d'un défaut à 0,1 %.
- **Correction proposée** : hors du strict périmètre de MV-003, donc conforme à CA-10 et à la section Risques du ticket : ouvrir un ticket de correction du test, sans le désactiver. Deux pistes : (a) dans ce contexte dédié, remplacer le bean `Clock` (`TimeConfig`) par une horloge contrôlée, après avoir vérifié qu'aucun composant appelé par register/login ne dépend de ce `Clock` pour l'expiration ; (b) au minimum, attendre la minute suivante en début de test si on est à moins de ~5 s d'une frontière. Corriger aussi la note : la cause est la frontière de minute, pas la lenteur du runner.
- **Question au développeur** : confirmes-tu que tu ouvres le ticket (ou que tu documentes ce défaut dans le ticket MV-003, section Risques) plutôt que de laisser « relancer » comme seule mitigation ?

### CRIT-002 — Pas de `timeout-minutes` sur le job `test` [MINEUR]
- **Localisation** : `.github/workflows/build.yml`, job `test`.
- **Constat** : le job hérite du délai par défaut de GitHub Actions, soit 360 minutes. Les IT font des appels HTTP réels (`TestRestTemplate`) et des accès JDBC, sans timeout global côté surefire (`forkedProcessExitTimeoutInSeconds`/`surefire.timeout` non configurés).
- **Scénario de défaillance** : un IT qui se bloque (deadlock de verrou Postgres, requête HTTP sans réponse, contexte qui ne démarre pas parce qu'Hikari attend) occupe le runner pendant 6 h avant l'échec. Pendant ce temps, l'auteur du push n'a pas de retour et le déploiement reste en attente.
- **Preuve** : configuration absente dans le diff. Le temps nominal annoncé par la note est de 2 à 4 min.
- **Correction proposée** : `timeout-minutes: 15` sur le job `test`. Une ligne dans le workflow, sans toucher à `pom.xml`.
- **Question au développeur** : un plafond de 15 à 20 min te paraît-il incompatible avec un cas réel (premier run sans cache) ?

### CRIT-003 — Deux pushes rapprochés peuvent publier `:latest` dans le désordre (préexistant, fenêtre élargie) [MINEUR]
- **Localisation** : `.github/workflows/build.yml` (pas de bloc `concurrency:`), tag unique `volleymatch:latest`.
- **Constat** : chaque push lance un run indépendant, et la dernière image poussée gagne. Le nouveau job `test` ajoute plusieurs minutes **variables** avant le push : téléchargement de Maven et des dépendances sans cache (cache hors périmètre), démarrage de Postgres, IT.
- **Scénario de défaillance** : le push A (commit 1) précède de 1 min le push B (commit 2). Le run A subit un téléchargement Maven Central lent et prend 3 min de plus que B. Le run B pousse `:latest` (commit 2), puis le run A pousse `:latest` (commit 1). Railway déploie le commit 1 et la correction du commit 2 disparaît de la prod sans qu'aucun job soit rouge.
- **Preuve** : sémantique de GitHub Actions sans `concurrency`. Le défaut existait avant MV-003, mais la variance de durée ajoutée par un job non caché élargit la fenêtre.
- **Correction proposée** : `concurrency: { group: deploy-master, cancel-in-progress: true }` au niveau du workflow, ce qui annule le run obsolète. À défaut, ouvrir un ticket séparé. Je n'en fais pas une condition de clôture de MV-003.
- **Question au développeur** : est-ce que tu l'intègres (une ligne, même fichier déjà modifié avec accord) ou est-ce que tu le renvoies à un ticket ?
