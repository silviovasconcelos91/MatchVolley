---
id: MV-003
titre: Exécuter les tests d'intégration par défaut et en CI avant le déploiement
type: technique
statut: TERMINÉ
priorite: haute
cree: 2026-11-20
maj: 2026-11-21
depend_de: []
branche: feat/MV-003-tests-integration
debat: docs/reviews/2026-11-21-MV-003-tests-integration/
---

# MV-003 — Exécuter les tests d'intégration par défaut et en CI avant le déploiement

## Demande initiale
> Créé les tickets de dettes

Dette ARCH-D02, relevée pendant la revue de MV-001 (`docs/reviews/2026-11-20-MV-001-event-malus/r1-architect.md`, ARCH-D02 et Q2 ; `SYNTHESE.md` : « Dette IT/CI (ARCH-D02) : ticket séparé »). La dette existait avant MV-001.

## Problème et valeur
- `./mvnw test` ne lance aucune classe `*IT` : `pom.xml` ne configure ni surefire ni failsafe, et les includes surefire par défaut (`*Test`, `Test*`, `*Tests`, `*TestCase`) excluent `*IT`. CLAUDE.md affirme pourtant « `./mvnw test` → tous les tests (unitaires + *IT) ».
- La CI (`.github/workflows/build.yml:23`, `mvn -Pnative -DskipTests package`) ne lance aucun test, et elle déploie sur Railway à chaque push sur `master`.
- Conséquence : les 11 classes `*IT`, dont `DataIsolationIT` (filet de l'isolation entre utilisateurs), `PublicLiveRateLimitIT` et les contrats live, ne tournent que si on les cible explicitement (`-Dtest='*Test,*IT'`). Une fuite de données entre utilisateurs ou une rupture de contrat peut partir en production sans être détectée, en particulier quand un agent suit CLAUDE.md à la lettre.

## User story
En tant que développeur (humain ou agent) qui pousse sur `master`, je veux que `./mvnw test` et la CI exécutent les tests unitaires et d'intégration, afin qu'une régression d'isolation ou de contrat bloque le déploiement.

## Critères d'acceptation
- [x] **CA-1** — Étant donné Postgres de test démarré (`docker compose up -d postgres-test`), quand on lance `./mvnw test`, alors le rapport inclut les classes `*IT` (au moins `DataIsolationIT`, `LiveStatsControllerIT`, `PublicLiveControllerIT`) et le nombre total de tests est égal à celui de `./mvnw test '-Dtest=*Test,*IT'`.
- [x] **CA-2** — `pom.xml` configure `maven-surefire-plugin` pour inclure `**/*IT.java` **en plus** des includes par défaut (les `*Test` continuent de tourner). Aucune autre modification de `pom.xml` (ni dépendance, ni plugin failsafe).
- [x] **CA-3** — Étant donné un `*IT` volontairement cassé (vérification manuelle, non commitée), quand on lance `./mvnw test`, alors le build échoue.
- [x] **CA-4** — `./mvnw test -Dtest=PlayerServiceTest` lance toujours ce seul test (la commande ciblée de CLAUDE.md reste valide).
- [x] **CA-5** — Étant donné un push sur `master`, quand la CI s'exécute, alors un job lance `./mvnw test` (JVM, Java 21) contre un Postgres 16 (service container exposé sur le port 5433, base `volleymatch_test`, identifiants de `application-test.properties`) avant le build natif ; le build natif, l'image Docker et le push ne s'exécutent que si ce job réussit (`needs:`).
- [x] **CA-6** — Étant donné un test qui échoue, quand la CI s'exécute, alors aucune image n'est poussée sur Docker Hub (le déploiement Railway n'est pas déclenché).
- [x] **CA-7** — Le déclencheur du workflow reste `push` sur `master` uniquement : aucun job sur les pull requests.
- [x] **CA-8** — Le job de test n'utilise aucun secret : il s'appuie sur `application-test.properties` et les valeurs par défaut de `application.properties` (toutes les variables `${...}` y ont un défaut).
- [x] **CA-9** — CLAUDE.md, section « Commandes » : `./mvnw test` y est décrit comme lançant unitaires + `*IT` (Postgres de test requis), ce qui devient exact. Aucune autre section de CLAUDE.md n'est modifiée.
- [x] **CA-10** — Aucun test n'est supprimé ni désactivé ; si un IT échoue une fois exécuté par défaut, il est corrigé (ou l'échec est remonté comme bug dans un ticket séparé).

## Règles métier
- Isolation multi-tenant : `DataIsolationIT` doit être un filet effectif (CLAUDE.md, « Sécurité / multi-tenant »).
- Les IT exigent Postgres sur `localhost:5433`, base `volleymatch_test` (`src/test/resources/application-test.properties:1-3`, `docker-compose.yml`, service `postgres-test`).

## Hors périmètre
- Job de tests sur les pull requests : décision utilisateur (Q2 = B), la CI reste limitée au push sur `master`.
- Failsafe / `./mvnw verify` et commande « unitaires seuls » : non retenu (Q1 = A).
- Faire tourner les IT avec Flyway activé (aujourd'hui `create-drop`, Flyway désactivé : une migration cassée n'est pas détectée). Valeur réelle, mais changement de stratégie de test distinct : ticket suivant possible.
- Testcontainers (nouvelle dépendance) : le service container GitHub Actions suffit.
- Couverture de code, rapports de tests publiés, cache Maven : améliorations de confort.
- Harmoniser la version Java de la CI native (GraalVM `java-version: '25'`, `build.yml:18`) avec `java.version` 21 (`pom.xml:17`) : écart constaté, à traiter séparément.

## Impacts
- **API / contrat front** : aucun.
- **Données** : aucune.
- **Public / overlay** : aucun.
- **Autres** :
  - `pom.xml` (configuration surefire), `.github/workflows/build.yml` (job de test + `needs:`) et CLAUDE.md (section « Commandes ») modifiés, avec l'accord explicite de l'utilisateur (voir Décisions). Toute modification au-delà de ce périmètre reste interdite.
  - Durée de la CI allongée de la durée des tests JVM (quelques minutes) avant le build natif.
  - `./mvnw test` en local exige désormais Postgres de test (déjà documenté).

## Décisions
| Question | Décision | Par | Date |
|---|---|---|---|
| Q1 — Mécanisme pour exécuter les IT | A : surefire inclut `**/*IT.java`, `./mvnw test` lance tout | Utilisateur | 2026-11-20 |
| Q2 — Périmètre CI | B : tests uniquement sur push `master`, avant le build natif ; pas de job sur les pull requests | Utilisateur | 2026-11-20 |
| Q3 — Accord pour modifier les fichiers protégés | A : accord explicite pour modifier `pom.xml`, `.github/workflows/build.yml` et la section « Commandes » de CLAUDE.md | Utilisateur | 2026-11-20 |
| Environnement du job de test | JVM classique Java 21 (comme `java.version`), pas de test natif | PO | 2026-11-20 |

## Risques
- Un IT instable (rate limit, horloge, ordre d'exécution) bloque le déploiement — mitigation : relancer, et si instable, ticket de correction plutôt que désactivation.
- `./mvnw test` plus lent en local (contexte Spring + Postgres requis) — accepté (Q1 = A) ; `-Dtest=...` permet de cibler.
- Sans job sur PR, une régression n'est détectée qu'au push `master` (le déploiement est bloqué, mais `master` est rouge) — accepté (Q2 = B).
- Postgres de test absent en local → échec des IT au lieu d'un skip silencieux — voulu ; documenté.

## Historique
| Date | Statut | Événement |
|---|---|---|
| 2026-11-20 | BROUILLON | Création depuis la dette ARCH-D02 (revue MV-001) |
| 2026-11-20 | PRÊT | Réponses Q1-Q3 de l'utilisateur intégrées, accord pour `pom.xml`, CI et CLAUDE.md tracé |
| 2026-11-21 | EN COURS | Développement lancé via dev-debate (branche feat/MV-003-tests-integration, base 8712564) — exécution autonome autorisée par l'utilisateur |
| 2026-11-21 | EN REVUE | Implémentation terminée (./mvnw test : 268 tests, IT inclus, vérifié orchestrateur), revue critic/architect |
| 2026-11-21 | TERMINÉ | Débat terminé : PRÊT AVEC RÉSERVES, 1 round, voir docs/reviews/2026-11-21-MV-003-tests-integration/SYNTHESE.md. Mergé sur master en local (autorisation utilisateur), CLAUDE.md « Commandes » modifié mais non commité |
