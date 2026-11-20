---
type: architecture-review
date: 2026-11-20
perimetre: diff courant (git diff bd673c1 + fichiers non suivis, hors .claude/ et docs/tickets/) — ticket MV-001
base: feat/MV-001-event-malus@bd673c1 (modifications non commitées)
verdict: A_CORRIGER
bloquants: 0
majeurs: 1
mineurs: 1
---

# Revue d'architecture — MV-001 event `malus` dans les stats live

## Synthèse
Le diff ajoute l'event `malus` (handicap de début de set) : un composant `malusAmount` sur `LiveEventDto`, la validation dans `LiveStatsPayloadValidator`, la déduction du malus dans l'overlay public et dans l'analyse (`startScore`, timeline, temps morts, `afterPoint` = points joués), ainsi que les trois docs racine. Il suit fidèlement le modèle du commit timeout (bd673c1). Il ne crée ni endpoint, ni migration, ni réflexion nouvelle, et l'isolation reste inchangée. Le choix d'appliquer le malus comme score de départ (`malusOf`) plutôt qu'en décrémentant dans la boucle est bon : il rend le résultat indépendant de l'ordre stocké, alors que l'analyse ne trie pas par `sequence`.
Point décisif : les CA-2 à CA-7 exigent un 400 sur `POST /matches/{id}/live-stats`, mais ce POST n'appelle pas le validateur. Ces CA ne sont donc pas satisfaits sur ce chemin, et l'analyse consomme ensuite ces données sans garde-fou. Le ticket repose ici sur une prémisse fausse, que le développeur a correctement signalée. La correction dépend d'un arbitrage (Q1).

## Constats

### ARCH-001 — CA-2 à CA-7 non satisfaits sur `POST /matches/{id}/live-stats` : le payload final n'est pas validé [MAJEUR]
- **Catégorie** : api
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java:29-46` (`saveLiveStats`, aucun appel au validateur) ; `src/main/java/vasconcelos/volleymatch/service/LiveStatsPayloadValidator.java:52-63` (`malusOf` additionne sans plafond ni dédoublonnage) ; `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java:156-160` (`startScore` consomme `malusOf` sur les données du POST) ; ticket `docs/tickets/MV-001-event-malus-live-stats.md:33-41`.
- **Constat** : le ticket définit la validation comme « commune à `POST /matches/{id}/live-stats` et `PUT /live-sessions/{id}/snapshot` » (l.33). CA-2 à CA-7 attendent un 400 sur les deux chemins. Dans le code, `LiveStatsPayloadValidator.validate` n'est appelé qu'à un seul endroit : `LiveSessionService.java:95` (PUT snapshot). `LiveStatsService.saveLiveStats` et `LiveStatsController.saveLiveStats` (`LiveStatsController.java:28-45`) persistent le payload tel quel. Un POST avec `malusAmount: 5`, deux malus `mine` dans le même set ou un malus après le premier point est donc stocké en 201. L'analyse le restitue ensuite sans contrôle : `startScore` vaut `-5`, ou `-4` pour deux malus de 2. Le tableau CA de `00-implementation.md` reconnaît que le POST n'est pas couvert. Aucun IT `POST … → 400` n'existe dans `LiveStatsControllerIT`.
- **Règle** : un CA non couvert ou mal interprété est un écart majeur (consigne de revue). Invariant 1 : la validation métier appartient au service. Le modèle conforme dans le projet est `LiveSessionService.java:95`, qui valide avant toute persistance. La doc `live-overlay-api-doc.md:456` présente d'ailleurs le contrat comme « utilisé par `PUT …/snapshot` et par la soumission finale `POST` » : il s'agit d'un seul contrat, mais il est appliqué sur un seul de ses deux points d'entrée.
- **Impact** : les données de match persistées (source de l'analyse et des stats saison) échappent aux règles que le ticket vient de poser. `malusOf` suppose explicitement un payload validé (javadoc l.48-51), mais cette hypothèse n'est vraie que pour l'overlay. Pour l'analyse, l'invariant « au plus un malus de 1 ou 2, avant le premier point » n'est garanti par rien côté serveur.
- **Correction attendue** (option A, recommandée ; voir Q1 pour l'arbitrage) :
  1. `LiveStatsService.java` : injecter `private final LiveStatsPayloadValidator payloadValidator;` et appeler `payloadValidator.validate(request);` en première instruction de `saveLiveStats`, avant `existsById`. Rien dans le controller.
  2. `LiveStatsControllerIT.java` : ajouter `should_return400_when_posted_malus_amount_is_invalid` (malus `malusAmount: 3` → 400, enveloppe `ApiResponse`, aucun match créé : un `GET …/live-stats` derrière renvoie 404) et `should_return400_when_posted_set_has_two_malus_for_same_team`.
  3. Vérifier que les fixtures POST existantes de `LiveStatsControllerIT` et des autres IT qui postent des live-stats (dont `DataIsolationIT`, `PlayerControllerIT` le cas échéant) sont valides au sens du validateur. Sinon, les corriger : ce sont des fixtures, pas des tests à désactiver.
  4. `live-stats-api-contract.md` : documenter que le POST renvoie 400 selon les mêmes règles de validation que le PUT snapshot (renvoi à la section de `live-overlay-api-doc.md` ou recopie des règles).
  5. Corriger le ticket : la phrase « le `POST` final échoue » de la l.26 est fausse (le POST acceptait déjà le malus).
  Si l'humain choisit l'option B (ne pas valider le POST) : corriger CA-1 à CA-8 dans le ticket pour les restreindre au PUT, et rendre `malusOf` défensif pour l'analyse (au plus un malus par équipe, `malusAmount` borné à {1, 2}, sinon ignoré), avec un test unitaire `should_ignore_invalid_malus_when_stored_payload_was_not_validated`.
- **Critère de validation** : option A, `grep -n "payloadValidator.validate" src/main/java/vasconcelos/volleymatch/service/LiveStatsService.java` renvoie une ligne ; `./mvnw test '-Dtest=*Test,*IT'` est vert, nouveaux IT POST 400 compris. Option B : ticket amendé et test défensif vert.

### ARCH-002 — `malusOf` (calcul de score) logé dans le validateur : 3e helper de sémantique d'event hors de sa place [MINEUR]
- **Catégorie** : conception
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LiveStatsPayloadValidator.java:40-63` ; appelants `LivePublicViewService.java:117-118,127` et `LiveStatsAnalysisService.java:77,158-159`.
- **Constat** : le diff ajoute `isMalus` et surtout `malusOf`, une fonction de **calcul de score**, en statiques package-private de `LiveStatsPayloadValidator`. Les deux services de restitution les appellent. Avec `MINE`/`OPP` et `isTimeout` (bd673c1), le validateur devient de fait le référentiel de la sémantique des events. `malusOf` n'est pourtant pas de la validation. Elle repose même sur l'hypothèse que la validation a eu lieu, ce qui n'est pas vrai pour toutes ses sources de données (voir ARCH-001).
- **Règle** : invariant 7, cohérence du domaine (chaque logique dans le bon composant), et étape 7 de la méthode : une abstraction manque quand le même motif apparaît pour la 3e fois. Le développeur a lui-même identifié l'alternative (`LiveEvents`) et l'a écartée comme hors périmètre (`00-implementation.md`, « Choix de conception »).
- **Impact** : faible aujourd'hui, mais chaque nouvel event (après timeout et malus) ajoutera des helpers au validateur et des branches dans les trois boucles de score (voir ARCH-D01). Le couplage « restitution → validateur » rend le validateur difficile à faire évoluer seul.
- **Correction attendue** :
  1. Créer `src/main/java/vasconcelos/volleymatch/service/LiveEventRules.java` (`final`, constructeur privé, package-private ou public selon les appelants) contenant `MINE`, `OPP`, `TIMEOUT_KEY`, `MALUS_KEY`, `isTimeout`, `isMalus`, `malusOf`.
  2. Dans `LiveStatsPayloadValidator`, `LivePublicViewService` et `LiveStatsAnalysisService`, remplacer les références `LiveStatsPayloadValidator.xxx` par `LiveEventRules.xxx` (imports statiques compris, `LivePublicViewService.java:30-31`).
  3. Déplacer les tests unitaires éventuels de ces helpers vers `LiveEventRulesTest`.
  Acceptable en alternative : un ticket de refactorisation séparé, à traiter avant le prochain type d'event. À noter dans la synthèse si c'est ce choix qui est retenu.
- **Critère de validation** : `grep -rn "LiveStatsPayloadValidator\.\(isMalus\|isTimeout\|malusOf\)\|LiveStatsPayloadValidator.MINE\|LiveStatsPayloadValidator.OPP" src/main` est vide ; `./mvnw test '-Dtest=*Test,*IT'` est vert.

## Dette existante remise en question

### ARCH-D01 — Rejeu du score dupliqué en trois boucles, avec deux politiques d'ordre différentes [MAJEUR indicatif]
- **Catégorie** : conception
- **Localisation** : `src/main/java/vasconcelos/volleymatch/service/LivePublicViewService.java:115-154` (trie par `sequence`, `orderedEvents` l.176-183) ; `src/main/java/vasconcelos/volleymatch/service/LiveStatsAnalysisService.java:165-180` (`buildTimeline`) et `:187-206` (`buildTimeouts`), qui suivent l'ordre stocké.
- **Constat** : le score cumulé d'un set est recalculé à partir des events dans trois boucles indépendantes. L'overlay trie par `sequence`, l'analyse non (« Events are expected in sequence order (as stored) »), et rien n'impose cet ordre au stockage (POST non validé, et le validateur ne contrôle pas l'ordre de la liste). Chaque type d'event a dû être ajouté dans les trois boucles : timeout dans bd673c1, malus ici.
- **Règle** : invariant 7 (pas de deuxième façon de faire une même chose) ; logique dupliquée.
- **Impact** : un payload stocké dans le désordre donne une timeline fausse dans l'analyse, alors que l'overlay serait juste. Le diff a contourné le problème pour le malus (score de départ, test `should_deductMalus_in_timeline_when_events_are_not_in_sequence_order`), mais pas pour les points eux-mêmes.
- **Correction attendue** : ticket dédié. Extraire un rejeu unique (ex. `LiveSetReplay.of(events)` qui trie par `sequence`, part de `startScore` et expose les points avec leur score après), consommé par `computeState`, `buildTimeline` et `buildTimeouts`.
- **Critère de validation** : une seule implémentation du cumul de score sous `src/main` ; un test d'analyse avec events stockés dans le désordre produit la même timeline que l'overlay.
- **Aggravé par le diff** : oui (une branche malus de plus dans chaque boucle). Voir ARCH-002.

### ARCH-D02 — `./mvnw test` n'exécute aucun `*IT`, et la CI ne lance aucun test [MAJEUR indicatif]
- **Catégorie** : tests
- **Localisation** : `pom.xml` (aucune configuration surefire/failsafe, vérifié par `grep surefire|failsafe` vide) ; `.github/workflows/build.yml:23` (`mvn -Pnative -DskipTests package`).
- **Constat** : avec les includes surefire par défaut, les classes `*IT` ne tournent pas, contrairement à ce que dit CLAUDE.md (« tous les tests (unitaires + *IT) »). La CI ne lance aucun test. `DataIsolationIT`, présenté comme le filet de l'invariant 2, ne s'exécute donc que si on le cible explicitement (`-Dtest='*Test,*IT'`).
- **Règle** : invariant 6 et invariant 2 (`DataIsolationIT` doit être un filet effectif).
- **Impact** : une régression d'isolation ou de contrat peut atteindre `master` sans qu'aucun test ne la détecte, surtout quand un agent suit à la lettre la commande de CLAUDE.md.
- **Correction attendue** : décision humaine (voir Q2), car `pom.xml` ne se modifie pas sans demande explicite.
- **Critère de validation** : `./mvnw test` (ou `./mvnw verify`) affiche un nombre de tests incluant les `*IT` (247 au lieu de 173 sur ce diff).
- **Aggravé par le diff** : non.

## Questions ouvertes

### Q1 — Valider le payload au `POST /matches/{id}/live-stats` ?
- **Option A** : brancher `LiveStatsPayloadValidator` dans `LiveStatsService.saveLiveStats` (ARCH-001, option A). Cela durcit le POST pour **toutes** les règles du contrat (setNumber, jersey, cohérence `scoredFor`…), pas seulement le malus.
- **Option B** : garder le POST permissif, restreindre CA-1 à CA-8 au PUT dans le ticket, et rendre `malusOf` défensif.
- **Recommandation : A.** Le contrat est unique et déjà documenté comme partagé (`live-overlay-api-doc.md:456`). Le front construit les deux payloads avec le même code, et tout match suivi en direct a déjà vu ses snapshots validés à chaque PUT. Le risque de rejeter une sauvegarde finale légitime est donc faible, et il est visible (400 explicite). À l'inverse, des données invalides stockées en silence faussent l'analyse et les stats saison durablement. Si l'humain craint une perte de match côté front, il peut demander une vérification préalable du comportement du front sur un 400 au POST (retry ou conservation locale). Cette vérification relève de la session front.

### Q2 — Faire tourner les `*IT` par défaut (et en CI) ?
- **Option A** : configurer surefire (`<includes>` avec `**/*IT.java`) dans `pom.xml`. C'est la plus simple, et elle colle à CLAUDE.md.
- **Option B** : ajouter failsafe (`./mvnw verify`) et mettre à jour CLAUDE.md.
- **Option C** : seulement corriger CLAUDE.md (`-Dtest='*Test,*IT'`).
- En complément pour A ou B : un job CI de tests sur PR, avec un service Postgres.
- **Recommandation : A**, plus un job CI de tests, dans un ticket outillage séparé. Ce n'est pas dans le périmètre de MV-001.

### Avis sur les trois points signalés par le développeur
1. **Validateur absent du POST** : constat ARCH-001 et Q1 ci-dessus.
2. **IT non exécutés par `./mvnw test`, CI sans tests** : confirmé, dette ARCH-D02 et Q2. Elle ne bloque pas ce diff, puisque les 247 tests ont été lancés explicitement.
3. **Build natif non vérifié** : je ne vois pas de risque. `malusAmount` est un composant de `LiveEventDto`, déjà atteint par `BindingReflectionHintsRegistrar` sur `LiveStatsRequest` (`NativeConfig.java:30`), qui parcourt les records imbriqués : c'est ce qui couvre le snapshot (`AttributeConverter`) et le `jsonb` de `MatchLiveSetEvent.events`. `LiveScoreDto` n'est atteint que comme type de retour de controller (`LiveMatchAnalysisResponse` → `LiveSetAnalysisDto`), que Spring AOT infère, exactement comme `LiveTimeoutDto`. Le diff n'ajoute ni projection JPQL, ni converter, ni JSON manuel, ni import `com.fasterxml`. Aucun hint n'est à ajouter. Ce raisonnement n'a pas été confirmé par un build natif.

## Ordre de traitement recommandé
1. Q1 (arbitrage humain), puis ARCH-001 selon l'option retenue : branchement du validateur dans le service, IT POST 400, fixtures, doc contrat, correction du ticket.
2. ARCH-002 : extraction de `LiveEventRules`. Le faire après ARCH-001, dont il est indépendant fonctionnellement : avec l'option B, le `malusOf` défensif doit être écrit directement dans `LiveEventRules`. Peut aussi être reporté à un ticket de refactorisation, regroupé avec ARCH-D01.
3. ARCH-D01 et ARCH-D02 : tickets séparés, hors MV-001.
