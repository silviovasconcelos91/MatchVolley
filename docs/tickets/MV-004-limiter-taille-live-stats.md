---
id: MV-004
titre: Limiter à 1 Mo la taille du corps de POST live-stats
type: dette
statut: TERMINÉ
priorite: moyenne
cree: 2026-11-20
maj: 2026-11-21
depend_de: [MV-001]
branche: feat/MV-004-limite-post-live-stats
debat: docs/reviews/2026-11-21-MV-004-limite-post-live-stats/
---

# MV-004 — Limiter à 1 Mo la taille du corps de POST live-stats

## Demande initiale
> Créé les tickets de dettes

Dette relevée pendant la revue de MV-001 (`docs/reviews/2026-11-20-MV-001-event-malus/SYNTHESE.md`, « Reportés » : « Pas de limite de taille du corps sur le POST live-stats ni du nombre d'events par set » ; `r2-reponse.md`, CRIT-101). La dette existait avant MV-001.

## Problème et valeur
- `SnapshotBodySizeFilter` plafonne le corps à 1 Mo (413), mais uniquement sur `PUT /api/v1/live-sessions/{id}/snapshot` (`SnapshotBodySizeFilter.java:29-35`). `POST /api/v1/matches/{matchId}/live-stats` (`LiveStatsController.java:28`) n'a aucune limite : un client authentifié peut envoyer un corps de plusieurs centaines de Mo, entièrement désérialisé en mémoire puis stocké en JSON. Sur un conteneur Railway en image native (petit heap), cela suffit à provoquer un OutOfMemory et à couper l'API pour tous les clubs.
- Le POST et le PUT partagent le même contrat et le même validateur (MV-001, décision Q4) : seule la limite de taille diffère. La limite de 1 Mo borne aussi indirectement le nombre total d'events (environ 3 000), ce qui rend un plafond d'events par set inutile (décision Q2).

## User story
En tant qu'exploitant de l'API, je veux que la soumission finale d'un match ait la même limite de taille que le snapshot live, afin qu'un payload démesuré soit rejeté proprement (413) au lieu de saturer la mémoire du serveur.

## Critères d'acceptation
- [x] **CA-1** — Étant donné un `POST /api/v1/matches/{id}/live-stats` authentifié avec `Content-Length` > 1 Mo, quand la requête arrive, alors la réponse est 413 dans l'enveloppe `ApiResponse` (`{ data: null, message, status: 413 }`) et rien n'est persisté (`GET /matches/{id}/live-stats` → 404).
- [x] **CA-2** — Étant donné le même POST > 1 Mo envoyé en chunked (sans `Content-Length`), alors 413 et rien n'est persisté (même garde que `LimitedInputStream`).
- [x] **CA-3** — Étant donné un POST valide de taille réaliste (match de 5 sets, ~100 events par set), alors 201 (pas de régression).
- [x] **CA-4** — Le `PUT /live-sessions/{id}/snapshot` garde son comportement (`LiveSessionControllerIT#should_return413_when_snapshot_body_exceeds_one_megabyte` reste vert ; si le message est rendu générique, seule l'assertion de message est adaptée, pas l'intention du test).
- [x] **CA-5** — Étant donné un POST > 1 Mo sans JWT, alors la réponse est 401 ou 413 (jamais 500 ni 201).
- [x] **CA-6** — Les autres endpoints ne sont pas filtrés : un `GET /matches/{id}/live-stats` et un `POST /teams` ne passent pas par la limite (test unitaire du `shouldNotFilter` du filtre).
- [x] **CA-7** — Le filtre existant est étendu (même classe ou classe renommée), pas dupliqué : une seule implémentation de la limite de 1 Mo sous `src/main`.
- [x] **CA-8** — `live-stats-api-contract.md` documente le code `413` (« Body > 1 Mo, rien n'est enregistré ») dans le tableau des codes du POST.

## Règles métier
- Un event JSON fait de l'ordre de 300 octets (joueur, action, trajectoire) : 1 Mo représente environ 3 000 events, au-delà de 10 sets très longs (60 à 80 échanges et 150 à 300 events dans un set disputé, 2 points d'écart sans plafond). À vérifier par le développeur sur une fixture réelle (CA-3).
- Tout match suivi en live a déjà vu ses snapshots acceptés sous 1 Mo, avec le même contrat que le POST final.
- Le POST est la sauvegarde finale du match : un rejet doit être explicite (413 avec message), jamais silencieux.

## Hors périmètre
- Plafond du nombre d'events par set : écarté (décision Q2), la limite de 1 Mo borne déjà le total ; un plafond ajouterait le risque de rejeter un match légitime (cf. CRIT-101).
- Limite de taille sur les autres endpoints (teams, players, auth) : écarté (décision Q1), petits DTO.
- Limite du nombre de matchs ou de sessions par utilisateur (quota) : autre sujet.
- Rate limiting des endpoints authentifiés : seuls `/public/**` sont rate-limités, sujet distinct.
- Comportement du front sur un 413 au POST (retry, conservation locale du match) : relève de la session front, à vérifier avant déploiement (voir Risques).

## Impacts
- **API / contrat front** : nouveau code `413` sur `POST /matches/{id}/live-stats`, documenté dans `live-stats-api-contract.md`. Rétrocompatible pour tout payload réaliste.
- **Données** : aucune migration. Les matchs déjà stockés ne sont ni relus ni rejetés (la limite s'applique à l'écriture).
- **Public / overlay** : aucun changement ; aucune donnée nouvelle exposée.
- **Autres** : sécurité (déni de service par un compte authentifié). Le filtre écrit l'enveloppe JSON à la main (`SnapshotBodySizeFilter.java:43-46`) ; le message « Snapshot exceeds 1 MB » doit devenir générique ou dépendre du chemin. Pas de modification de `application*.properties` (les réglages Tomcat/servlet ne bornent pas les corps JSON). Pas de réflexion nouvelle, a priori pas de hint natif.

## Décisions
| Question | Décision | Par | Date |
|---|---|---|---|
| Q1 — Périmètre de la limite de taille | A : 1 Mo sur `POST /matches/{id}/live-stats` uniquement, en élargissant le filtre existant (413) | Utilisateur | 2026-11-20 |
| Q2 — Plafond du nombre d'events par set | A : pas de plafond ; volet retiré du ticket | Utilisateur | 2026-11-20 |

## Risques
- Rejet d'un match légitime au POST final (perte de saisie côté coach) — mitigation : limite identique au PUT déjà en production, message explicite, vérification côté front du comportement sur 413 avant déploiement.
- Ordre des filtres (rate limit, JWT, taille) mal maîtrisé : le corps est lu avant le rejet — mitigation : CA-1/CA-2/CA-5 en IT, et relecture `critic`.

## Historique
| Date | Statut | Événement |
|---|---|---|
| 2026-11-20 | BROUILLON | Création depuis la dette « limites d'entrée » (revue MV-001) |
| 2026-11-20 | PRÊT | Réponses Q1 (POST seul, 1 Mo) et Q2 (pas de plafond d'events) intégrées ; titre recentré |
| 2026-11-21 | EN COURS | Développement lancé via dev-debate (base 9e68a69) — exécution autonome autorisée par l'utilisateur |
| 2026-11-21 | EN REVUE | Implémentation terminée (./mvnw test : 288 tests verts, vérifié orchestrateur), revue critic/perf/architect |
| 2026-11-21 | TERMINÉ | Débat terminé : PRÊT, 2 rounds (bloquant CRIT-001 contournement par URI encodée corrigé), voir docs/reviews/2026-11-21-MV-004-limite-post-live-stats/SYNTHESE.md. Mergé sur master en local (autorisation utilisateur) |
