# Synthèse — MV-003 Exécuter les tests d'intégration par défaut et en CI
- Branche : feat/MV-003-tests-integration · base 8712564 · rounds : 1/3 (relecteurs : critic, architect ; perf écarté, aucun chemin chaud)
- Tests : `./mvnw test` (sans option) → 268 tests, 0 failure, 0 error, 9 classes *IT exécutées (vérifié par l'orchestrateur)
- Verdict : **PRÊT AVEC RÉSERVES** (workflow CI non exécuté réellement ; IT de rate limit potentiellement instable)
- Exécution autonome autorisée par l'utilisateur (absent) : décisions d'orchestration notées ci-dessous.

## Arbitrages requis
Aucun (0 bloquant, 0 majeur).

## Corrigé pendant le débat
| ID | Gravité | Correctif |
|----|---------|-----------|
| CRIT-002 | MINEUR | `timeout-minutes: 15` ajouté au job `test` (appliqué par l'orchestrateur, 1 ligne, périmètre build.yml autorisé par Q3) |

## Contestations acceptées par les relecteurs
Aucune.

## Reportés / mineurs non traités
| ID | Raison |
|----|--------|
| CRIT-001 | `PublicLiveRateLimitIT` instable si ses 4 requêtes chevauchent un changement de minute (fenêtre calendaire `now/60_000`) → ~0,05–0,5 % des runs CI bloqueraient le déploiement. Correctif = horloge injectable ou attente de minute pleine : touche du code de test/limiteur hors périmètre MV-003. **À ticketer.** |
| CRIT-003 / ARCH-D01 | Pas de bloc `concurrency:` dans le workflow : deux pushes rapprochés peuvent publier `:latest` dans le désordre. Préexistant ; change le comportement de déploiement (annulation de runs) → décision utilisateur. **À ticketer.** |

## Critères d'acceptation
| CA | Couvert par | Statut |
|----|-------------|--------|
| CA-1 | `./mvnw test` → 268 tests = total `-Dtest=*Test,*IT` (orchestrateur + développeur) | ✅ |
| CA-2 | Diff `pom.xml` : seule config `maven-surefire-plugin` (includes par défaut + `**/*IT.java`), pas de dépendance/failsafe | ✅ |
| CA-3 | `DataIsolationIT` cassé volontairement → `BUILD FAILURE` (développeur), fichier restauré | ✅ |
| CA-4 | `-Dtest=PlayerServiceTest` → seul ce test (13 méthodes) | ✅ |
| CA-5 | Job `test` Temurin 21 + service `postgres:16` 5433, `needs: test` sur `build-and-push` (parsing YAML + relecture) | ⚠️ non exécuté |
| CA-6 | `needs:` bloque le push Docker, seule source du déploiement Railway (relecture critic/architect) | ⚠️ non exécuté |
| CA-7 | Déclencheur inchangé : push `master` uniquement | ✅ |
| CA-8 | Aucun `secrets.*` dans le job `test` ; tests verts sans variables d'env | ✅ |
| CA-9 | CLAUDE.md : seule la ligne `./mvnw test` de « Commandes » modifiée (non commitée : copie de travail utilisateur contient d'autres changements) | ✅ |
| CA-10 | Aucun test supprimé/désactivé | ✅ |

## Non vérifié
- Exécution réelle du workflow GitHub Actions (premier run au push sur `master`).
- Modification de CLAUDE.md laissée **non commitée** (fichier contenant d'autres changements non commités de l'utilisateur).
