---
type: architecture-review
date: 2026-11-21
perimetre: diff courant depuis 8712564 sur pom.xml, .github/workflows/build.yml et CLAUDE.md (section « Commandes » uniquement)
base: feat/MV-003-tests-integration@8712564
verdict: CONFORME
bloquants: 0
majeurs: 0
mineurs: 0
---

# Revue d'architecture — MV-003 : tests d'intégration par défaut et en CI avant déploiement

## Synthèse
Le changement fait exécuter les `*IT` par `./mvnw test` (includes surefire) et ajoute un job CI `test` (JVM 21, Postgres 16 en service container sur :5433) dont dépend `build-and-push` via `needs: test`. La solution est la plus simple compatible avec les décisions Q1 = A et Q2 = B du ticket : pas de failsafe, pas de nouvelle dépendance, pas de secret, déclencheur inchangé. Les dix CA sont couverts et correctement interprétés ; les seules preuves non exécutables localement (CA-5, CA-6 sur GitHub) reposent sur une sémantique `needs:` standard et sont signalées comme telles par la note d'implémentation. Aucun constat.

Examiné : `git diff 8712564 -- pom.xml .github/workflows/build.yml CLAUDE.md` (pour CLAUDE.md, seule la ligne `./mvnw test` de « Commandes », `CLAUDE.md:30`), `src/test/resources/application-test.properties`, `src/main/resources/application.properties`, `docker-compose.yml`, `.mvn/wrapper/maven-wrapper.properties`, mode git de `mvnw`, inventaire de `src/test/java`.

Vérification des CA :
- **CA-1 / CA-2** : `pom.xml:153-167` déclare `maven-surefire-plugin` avec les 4 includes par défaut répétés + `**/*IT.java`. Rien d'autre dans `pom.xml`. Aucun fichier de test hors `*Test`/`*IT` à part deux classes de support (`LiveSessionITSupport`, `LiveSnapshotFixtures`) et aucun `*Tests`/`*TestCase`/`Test*` : l'égalité avec `-Dtest=*Test,*IT` (268) est donc structurelle, pas fortuite. `BaseIT` est abstraite (`BaseIT.java:22`), non exécutée.
- **CA-3 / CA-4 / CA-10** : preuves manuelles documentées dans `00-implementation.md` ; surefire donne priorité à `-Dtest`. Aucun `@Disabled` ajouté.
- **CA-5** : `build.yml:10-41` (Temurin 21, `postgres:16`, `5433:5432`, `volleymatch_test`, `postgres/postgres` = `application-test.properties:1-3` et service `postgres-test` de `docker-compose.yml`), healthcheck `pg_isready`, `build.yml:44` `needs: test`. Toutes les classes `@SpringBootTest` (`BaseIT`, `AuthControllerIT`, `CorsIT`) sont en `@ActiveProfiles("test")` : aucune ne tentera la base de dev :5432, absente du runner.
- **CA-6** : le seul push d'image est `docker/build-push-action` dans `build-and-push` (`build.yml:70-78`), sauté si `test` échoue.
- **CA-7** : `build.yml:3-5` inchangé, aucun `pull_request`.
- **CA-8** : aucun `secrets.` dans le job `test` ; toutes les `${...}` de `application.properties:8-21` ont un défaut.
- **CA-9** : `CLAUDE.md:30` seule ligne de « Commandes » touchée ; énoncé désormais exact.

## Constats

Aucun.

## Dette existante remise en question

### ARCH-D01 — Pas de sérialisation des déploiements sur `master` [MINEUR]
- **Catégorie** : conception
- **Localisation** : `.github/workflows/build.yml:1-7`, `.github/workflows/build.yml:76`
- **Constat** : le workflow n'a pas de bloc `concurrency:`. Deux pushes rapprochés sur `master` lancent deux exécutions parallèles qui poussent toutes deux le tag mutable `volleymatch:latest` ; celle qui termine en dernier gagne, même si c'est le commit le plus ancien.
- **Règle** : le déploiement doit refléter le dernier état validé de `master` (intention du ticket MV-003 : ne déployer que ce qui a passé les tests).
- **Impact** : une version plus ancienne (testée, mais périmée) peut écraser la plus récente en production. Préexistant ; la durée variable du nouveau job `test` rend l'inversion d'ordre un peu plus probable, sans la créer.
- **Aggravé par le diff : non** (marginalement, par l'allongement du pipeline)
- **Correction attendue** (ticket séparé, hors périmètre MV-003) :
  1. Ajouter au niveau workflow `concurrency: { group: deploy-master, cancel-in-progress: true }` (ou `false` pour mettre en file).
  2. Optionnellement taguer aussi l'image par `${{ github.sha }}` pour rendre le déploiement traçable.
- **Critère de validation** : deux pushes consécutifs sur `master` → une seule exécution aboutit au push d'image, celle du commit le plus récent.

## Questions ouvertes

Aucune. Les arbitrages pertinents (failsafe, job sur PR, tests en natif, version Java de la CI native) sont déjà tranchés ou explicitement renvoyés hors périmètre par le ticket.

## Ordre de traitement recommandé

Aucun constat à traiter. ARCH-D01 relève d'un ticket séparé si l'utilisateur le juge utile.
