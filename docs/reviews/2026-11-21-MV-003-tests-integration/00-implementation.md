# Implémentation — MV-003 Exécuter les tests d'intégration par défaut et en CI avant le déploiement

## Intention
`./mvnw test` ne lançait aucune classe `*IT` (includes surefire par défaut) et la CI déployait sur Railway sans aucun test.
Le ticket exige que `./mvnw test` lance unitaires + `*IT`, et que la CI exécute ces tests (JVM 21, Postgres 16 en service container sur :5433) avant le build natif et le push Docker, qui ne doivent tourner que si les tests passent.

## Choix de conception
- **`<includes>` surefire = 4 includes par défaut répétés + `**/*IT.java`** — **pourquoi** : déclarer `<includes>` *remplace* la liste par défaut de surefire (`**/Test*.java`, `**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`) ; ne mettre que `**/*IT.java` aurait coupé les tests unitaires. Vérifié par `help:effective-pom` : le parent Spring Boot 4.1.0-RC1 ne configure aucun include surefire (seulement la version 3.5.5). Alternative écartée : failsafe / `verify` (Q1 = A, hors périmètre).
- **Pas de `<version>` sur le plugin surefire** — **pourquoi** : la version est gérée par le parent Spring Boot (`pluginManagement`, 3.5.5), comme `maven-compiler-plugin` déjà déclaré sans version dans le même `pom.xml`.
- **`BaseIT` est matché par `**/*IT.java` mais n'est pas exécuté** — **pourquoi** : la classe est `abstract` (`BaseIT.java:22`), JUnit Platform ne l'instancie pas. Constaté : aucune entrée `BaseIT` dans le rapport.
- **CA-4 sans configuration particulière** — **pourquoi** : `-Dtest=...` a priorité sur `<includes>` dans surefire ; la commande ciblée de CLAUDE.md reste valide (vérifié).
- **Job `test` séparé + `needs: test` sur `build-and-push`** — **pourquoi** : `needs:` garantit qu'un échec de test saute tout le job natif/Docker (dont `docker/build-push-action`), donc aucune image poussée. Alternative écartée : ajouter une étape de test dans le job existant — elle tournerait sous GraalVM 25 (pas Java 21, contraire à la décision PO) et mélangerait secrets Docker Hub et tests.
- **JDK du job de test : `actions/setup-java@v4`, Temurin 21** — **pourquoi** : décision PO « JVM classique Java 21 ». Le choix de distribution (Temurin) est arbitraire mais standard ; aucune dépendance projet ajoutée.
- **`./mvnw` (wrapper) dans le job de test, alors que le job natif garde `mvn`** — **pourquoi** : CA-5 demande `./mvnw test` ; `mvnw` est commité en mode 100755 (`git ls-files -s mvnw`). Le job natif n'a pas été touché (harmonisation hors périmètre).
- **Service container `postgres:16`, `5433:5432`, `POSTGRES_DB=volleymatch_test`, `postgres/postgres`, healthcheck `pg_isready`** — **pourquoi** : reproduit exactement le service `postgres-test` de `docker-compose.yml` et l'URL de `application-test.properties:1-3`, aucune surcharge de propriété nécessaire. Le healthcheck évite que les tests démarrent avant que Postgres accepte les connexions (GitHub attend que le service soit « healthy » avant les steps).
- **Identifiants Postgres en clair dans le workflow** — **pourquoi** : ce ne sont pas des secrets (base jetable du runner, mêmes valeurs déjà commitées dans `application-test.properties` et `docker-compose.yml`). CA-8 : aucun `${{ secrets.* }}` dans le job `test`.
- **`permissions: contents: read` sur le job `test`** — **pourquoi** : moindre privilège pour un job qui ne fait que lire le code. Limité à ce job pour ne rien changer au comportement de `build-and-push` (cache `type=gha`).
- **Pas de cache Maven (`setup-java` `cache: maven`)** — **pourquoi** : explicitement hors périmètre (« cache Maven : amélioration de confort »).
- **CLAUDE.md : une seule ligne modifiée dans « Commandes »** — **pourquoi** : la ligne affirmait déjà « unitaires + *IT » (faux avant ce ticket) ; j'ai ajouté « Postgres de test requis » et la mention de la CI (CA-9). Édition sur place, les modifications non commitées de l'utilisateur ailleurs dans le fichier sont intactes.

## Fichiers
- `pom.xml` — ajout du bloc `maven-surefire-plugin` (includes), rien d'autre (ni dépendance, ni failsafe).
- `.github/workflows/build.yml` — nouveau job `test` (Java 21 + service Postgres 16 :5433, `./mvnw test`) ; `needs: test` sur `build-and-push`. Déclencheur `on.push.branches: [master]` inchangé. Ajout incident d'un saut de ligne final (le fichier n'en avait pas).
- `CLAUDE.md` — section « Commandes », ligne `./mvnw test` uniquement.

Aucun fichier de `src/` modifié.

## Critères d'acceptation
| CA | Preuve |
|----|--------|
| CA-1 | `./mvnw test` (après changement) : `Tests run: 268, Failures: 0, Errors: 0, Skipped: 0` avec `AuthControllerIT`, `CorsIT`, `DataIsolationIT`, `LiveSessionControllerIT`, `LiveStatsControllerIT`, `PlayerControllerIT`, `PublicLiveControllerIT`, `PublicLiveRateLimitIT`, `TeamControllerIT` dans le rapport. Référence `./mvnw test '-Dtest=*Test,*IT'` : **268**. Avant changement, `./mvnw test` : **184** (test « rouge » du TDD de ce ticket). |
| CA-2 | `git diff pom.xml` : +15 lignes, uniquement le plugin surefire avec 4 includes par défaut + `**/*IT.java`. Les `*Test` tournent toujours (inclus dans les 268). |
| CA-3 | Vérification manuelle : ajout de `assertThat(true).isFalse()` dans `DataIsolationIT#should_notSeeOtherUsersTeams_when_queryingTeams` → `./mvnw test` exit code 1, `Tests run: 268, Failures: 1`, `BUILD FAILURE`. Fichier restauré depuis une copie (`git status` propre sur le fichier), puis run complet vert. |
| CA-4 | `./mvnw test -Dtest=PlayerServiceTest` → `Tests run: 13` dans `PlayerServiceTest` uniquement, `BUILD SUCCESS`. |
| CA-5 | `build.yml` : job `test` (`setup-java` 21 Temurin, service `postgres:16` ports `5433:5432`, `POSTGRES_DB=volleymatch_test`, `postgres/postgres`, step `./mvnw test`), `build-and-push.needs: test`. Syntaxe vérifiée par parsing SnakeYAML 2.6 (Python absent du poste) : `jobs=[test, build-and-push]`, `build-and-push.needs=test`. **Non exécuté sur GitHub** (impossible localement). |
| CA-6 | Conséquence de `needs: test` : si `test` échoue, `build-and-push` est sauté, donc `docker/build-push-action` (seul push) ne s'exécute pas. **Non prouvé par exécution réelle** ; repose sur la sémantique documentée de `needs:`. Hypothèse : Railway se déclenche sur la nouvelle image Docker Hub (non vérifiable depuis le dépôt). |
| CA-7 | Parsing YAML : `on={push={branches=[master]}}`, aucun `pull_request`. |
| CA-8 | Parsing YAML : la sérialisation du job `test` ne contient pas `secrets`. Toutes les `${...}` de `application.properties` ont un défaut (`JWT_SECRET`, `RESEND_*`, `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `PUBLIC_WEB_URL`, `LIVE_RATE_LIMIT_PER_MINUTE`) et `app.jwt.secret` est surchargé par `application-test.properties`. Confirmé localement : les 268 tests passent sans aucune variable d'environnement définie. |
| CA-9 | `CLAUDE.md:30` modifiée ; aucune autre ligne de CLAUDE.md touchée par ce ticket. |
| CA-10 | Aucun test supprimé ni désactivé ; aucun IT n'a échoué une fois exécuté par défaut (268/268 vert, 3 runs complets). |

## Vérifications
- `./mvnw test` avant changement : `Tests run: 184, Failures: 0, Errors: 0, Skipped: 0`.
- `./mvnw test '-Dtest=*Test,*IT'` : `Tests run: 268, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.
- `./mvnw test` après changement : `Tests run: 268, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` (deux fois, dont une après la restauration de CA-3).
- `./mvnw test -Dtest=PlayerServiceTest` : `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS`.
- IT cassé volontairement : `Tests run: 268, Failures: 1` — `BUILD FAILURE`, exit 1.
- YAML : parsé sans erreur par SnakeYAML 2.6 (jar du dépôt Maven local, via `java` source-launcher, script hors dépôt).

## Points d'attention
- **Workflow non exécuté** : la première exécution réelle aura lieu au push sur `master`. Points à surveiller : téléchargement de Maven par `mvnw` sur le runner (wrapper « script only », pas de jar commité), disponibilité du port 5433 sur `ubuntu-latest` (normalement libre), durée (~2-4 min de tests + téléchargement des dépendances sans cache).
- **Écart avec le ticket** : le ticket parle de « 11 classes `*IT` » ; il y en a 10 fichiers, dont `BaseIT` abstraite, soit **9 classes exécutées**. Sans impact sur les CA.
- **`PublicLiveRateLimitIT` en CI** : test dépendant du temps (fenêtre par minute) ; stable en local sur 3 runs. Risque d'instabilité sur runner lent identifié dans le ticket (mitigation : relance, puis ticket de correction).
- **Le build natif ne relance pas les tests** (`-DskipTests` conservé) : les tests ne tournent qu'en JVM (décision PO). L'écart GraalVM 25 / Java 21 reste hors périmètre.
- `./mvnw test` en local échoue désormais si Postgres de test n'est pas démarré (voulu, documenté dans CLAUDE.md).
- Git signale `LF will be replaced by CRLF` sur `build.yml` (config `core.autocrlf` locale) : sans effet sur GitHub Actions.
- Rien n'est commité ; CLAUDE.md contient aussi des modifications préexistantes de l'utilisateur, non liées à ce ticket.
