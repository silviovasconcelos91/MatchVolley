---
type: architecture-review
date: 2026-11-21
perimetre: diff courant depuis 9e68a69 (git diff 9e68a69 + fichiers non suivis), hors CLAUDE.md, .gitignore, .claude/, docs/architecture/
base: feat/MV-004-limite-post-live-stats@9e68a69 (modifications non commitées)
verdict: CONFORME
bloquants: 0
majeurs: 0
mineurs: 1
---

# Revue d'architecture — MV-004 : limite de 1 Mo sur POST live-stats

## Synthèse
Le changement étend l'ancien `SnapshotBodySizeFilter` (renommé `LivePayloadSizeFilter`) à `POST /api/v1/matches/{matchId}/live-stats`. Il garde une seule constante `MAX_BYTES`, un seul `LimitedInputStream` et un message générique. Il couvre le rejet anticipé sur `Content-Length` et le rejet en chunked via `PayloadTooLargeException` → `GlobalExceptionHandler`. La solution s'inscrit dans l'architecture existante. Le filtre reste dans `config/`, comme `PublicRateLimitFilter`. Il n'introduit ni deuxième implémentation, ni dépendance, ni réflexion nouvelle (pas de hint natif à prévoir). Le contrat est documenté.

Les huit CA sont couverts et correctement interprétés :
- CA-1 : IT `Content-Length`, enveloppe, rien en base et `GET` → 404.
- CA-2 : vrai chunked via `HttpClient`, longueur -1 vérifiée.
- CA-3 : 5×100 et 10×300 → 201.
- CA-4 : IT snapshot inchangée, avec en plus une assertion sur l'enveloppe.
- CA-5 : `isIn(401, 413)` et rien de persisté.
- CA-6 : test unitaire de `shouldNotFilter` sur 10 routes, dont `GET live-stats` et `POST /teams`.
- CA-7 : `grep` de `SnapshotBodySize` sous `src/main` vide, une seule occurrence de `1024L * 1024L`.
- CA-8 : `live-stats-api-contract.md:20`.

Un seul point mineur concerne l'organisation des fixtures de test. La revue a aussi relevé une dette existante : le POST renvoie 500 sur une chaîne de plus de 255 caractères.

## Constats

### ARCH-001 — Générateur de payload réaliste isolé dans l'IT au lieu de `LiveSnapshotFixtures` [MINEUR]
- **Catégorie** : tests
- **Localisation** : `src/test/java/vasconcelos/volleymatch/LiveStatsControllerIT.java:489` (`realisticMatch`), `:463` (`oversizedBody`)
- **Constat** : la fabrique de match réaliste (`realisticMatch(matchId, sets, eventsParSet)`, environ 317 octets par event) est une méthode privée de `LiveStatsControllerIT`. Le projet centralise pourtant la construction des payloads live dans une classe de fixtures partagée, utilisée par 10 classes de test.
- **Règle** : cohérence avec le pattern établi. Les payloads live de test sont construits dans `src/test/java/vasconcelos/volleymatch/LiveSnapshotFixtures.java:24-114` (`attackPoint`, `neutral`, `set`, `snapshot`…). Ce générateur sert à mesurer la marge sous 1 Mo, qui est une règle métier du ticket. C'est précisément le genre de fixture qu'un futur test de validateur, de perf ou de snapshot PUT voudra réutiliser. `LiveSessionControllerIT:235` construit d'ailleurs son propre corps surdimensionné.
- **Impact** : faible. Si l'on veut vérifier la même marge sur le `PUT /snapshot` (même contrat, même limite), le générateur sera dupliqué ou réécrit. C'est un troisième endroit où l'on fabrique des payloads live.
- **Correction attendue** :
  1. Déplacer `realisticMatch(String matchId, int setCount, int eventsPerSet)` dans `LiveSnapshotFixtures`, en `static` package-private. Réutiliser si possible `set(...)` (ligne 82) pour construire les sets.
  2. Dans `LiveStatsControllerIT`, appeler `LiveSnapshotFixtures.realisticMatch(...)` et supprimer la méthode privée.
  3. `oversizedBody` peut rester local : il produit du JSON brut gonflé, propre au test HTTP.
- **Critère de validation** : `grep -n "realisticMatch" src/test` ne trouve la définition que dans `LiveSnapshotFixtures.java`, et `./mvnw test -Dtest=LiveStatsControllerIT` reste vert.

## Dette existante remise en question

### ARCH-D01 — Les chaînes du payload live ne sont pas bornées à 255 caractères : 500 au lieu de 400 [MAJEUR indicatif]
- **Catégorie** : api | persistance
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsPayloadValidator.java` (aucun contrôle de longueur : `grep "length\|255\|title"` ne renvoie rien), `src/main/resources/db/migration/V1__init.sql:36,41` (`id VARCHAR(255)`, `title VARCHAR(255)`), `src/main/java/vasconcelos/volleymatch/model/match/Match.java:38-39`, `src/main/java/vasconcelos/volleymatch/config/GlobalExceptionHandler.java` (aucun handler pour `DataIntegrityViolationException`)
- **Constat** : la note d'implémentation (`00-implementation.md`, « Cycle TDD ») indique qu'avant le filtre, un POST surdimensionné finissait en **500**, sans cause investiguée. Le corps de test gonfle `title`, une colonne `VARCHAR(255)`. Rien ne valide la longueur de `title`, `matchId` ou `teamName` avant la persistance, et aucune exception de contrainte SQL n'est traduite. Même avec la limite de 1 Mo, un POST de 2 Ko avec un `title` de 300 caractères doit donc produire une 500 hors enveloppe `ApiResponse`. Ce dernier point est déduit, pas testé.
- **Règle** : invariant 4 (toute erreur dans `ApiResponse`, 400 pour un payload invalide, cf. `live-stats-api-contract.md:17`) et invariant 3 (entité et colonnes cohérentes). Le contrat annonce « 400 : Payload invalide… rien n'est enregistré ».
- **Impact** : l'erreur client est renvoyée en 500. Le front ne peut pas distinguer ce cas d'une panne serveur, et la sauvegarde finale d'un match est perdue sans message exploitable. Le même risque existe probablement pour le `PUT /snapshot`, dont `title` est `NOT NULL VARCHAR(255)` (`V9__add_live_sessions.sql:8`).
- **Aggravé par le diff : non.** Le filtre ne fait que masquer le cas au-delà de 1 Mo.
- **Correction suggérée (ticket séparé)** : ajouter dans `LiveStatsPayloadValidator` des contrôles de longueur (au maximum 255) sur `matchId`, `title`, `teamName`, `venue`, en levant l'exception 400 existante. Ajouter le cas dans `LiveStatsPayloadValidatorTest` et une IT `should_return400_when_title_exceeds_255_characters`.

## Questions ouvertes

### Q1 — Ouvrir un ticket pour ARCH-D01 ?
- Options : (A) créer un ticket de dette « bornes de longueur du payload live », hors MV-004 ; (B) l'intégrer à MV-004 ; (C) ignorer.
- Recommandation : **A**. Le sujet sort du périmètre décidé en Q1 (taille globale du corps uniquement). Il touche le validateur partagé par POST et PUT et mérite ses propres CA.

## Ordre de traitement recommandé
1. ARCH-001 : indépendant et non bloquant, applicable dans ce ticket ou reportable.
2. ARCH-D01 : hors périmètre, à porter dans un nouveau ticket via le skill `ticket` (voir Q1).
