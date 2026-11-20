---
type: perf-review
round: 1
bloquants: 0
majeurs: 0
mineurs: 2
---
# Revue performance — MV-004 : limite de 1 Mo sur POST live-stats (LivePayloadSizeFilter)

Périmètre lu : `LivePayloadSizeFilter` (nouveau), version précédente `SnapshotBodySizeFilter` (`git show 9e68a69`), `LiveStatsController`, `GlobalExceptionHandler`, note d'implémentation. Aucune propriété `server.*` / swallow / multipart dans `application*.properties`. Je n'ai pas lancé de test : le diff n'ajoute aucune requête SQL.

## Réponses aux trois points à chiffrer

### 1. Coût du filtre sur les requêtes non filtrées : négligeable
- `shouldNotFilter` fait `getMethod()` + `getRequestURI()`. Le `&&` court-circuite : la regex n'est évaluée que pour un PUT (snapshot) ou un POST (live-stats). Le diff ajoute 1 comparaison de méthode par requête et jusqu'à 2 appels `matcher().matches()` sur les seuls PUT/POST.
- Les regex sont `static final Pattern` : aucune compilation par appel. Les patterns sont ancrés avec un `[^/]+` sans backtracking catastrophique (borné par la longueur de l'URI, qui est limitée par Tomcat à 8 Ko).
- Chemin chaud `GET /api/v1/public/live/{code}` : c'est un GET, donc zéro regex, juste un `equals` de méthode. À 120 req/min par client (~2/s), le surcoût du filtre est de l'ordre de la microseconde par requête, soit des millisecondes cumulées par minute. Aucun gain à chercher ici (optimisation prématurée : ne pas remplacer par un `PathPattern`).
- `OncePerRequestFilter` ajoute en plus un attribut de requête par appel, comme avant le ticket.

### 2. Mémoire du buffering du corps (native image, petit heap) : borné, c'est une amélioration nette
- Avant le ticket : `POST /matches/{id}/live-stats` sans limite. Un compte authentifié pouvait envoyer N centaines de Mo, que Jackson désérialisait dans `LiveStatsRequest`. Le coût mémoire croissait sans borne avec la taille du corps (risque d'OOM natif).
- Après : le corps est plafonné à 1 048 576 octets, soit environ 3 000 événements à ~317 o/event (mesuré par le développeur : 953 101 o pour 10×300 ; 158 845 o pour 5×100, cas réaliste).
- Ordre de grandeur du pic par requête : octets lus (≤ 1 Mo) plus l'arbre d'objets désérialisés. Pour des records avec chaînes courtes et listes, le facteur typique est ×5 à ×10, soit environ 5 à 10 Mo au pire. Pour le cas réaliste (160 Ko), environ 1 Mo. C'est une estimation de ma part, non mesurée.
- Concurrence : l'endpoint exige un JWT et il n'y a pas de rate limit, donc N sauvegardes finales simultanées coûtent environ N × 10 Mo au pire. Un club ne sauvegarde un match qu'une fois par match : même 10 requêtes simultanées font environ 100 Mo au pire, ce qui reste raisonnable pour un compte authentifié. Ce n'est pas un défaut de ce ticket. Le filtre transforme un risque non borné en un risque borné par requête.
- Le filtre lui-même ne bufferise rien : `LimitedInputStream` compte les octets et délègue. Il n'ajoute aucune copie ni allocation proportionnelle au corps (une instance wrapper + une instance de flux par requête filtrée).
- En rejet sur `Content-Length` > 1 Mo : réponse écrite sans lire le corps, coût constant (quelques dizaines d'octets).

### 3. Chunked : borné et correct
- `getContentLengthLong()` vaut -1 en chunked, donc le contrôle anticipé est sauté et seul le comptage en lecture s'applique. L'exception est levée au premier read qui dépasse 1 Mo, donc au plus 1 Mo + un buffer de lecture (quelques Ko) est consommé avant rejet.
- Un `Content-Length` menteur n'est pas exploitable : Tomcat délimite le corps selon l'en-tête, donc on ne peut pas lire plus que déclaré, et le comptage reste actif sur les lectures de toute façon.
- L'ordre des filtres (sécurité avant ce filtre) fait qu'un appel sans JWT ne lit jamais le corps. Tomcat avale ensuite au plus `maxSwallowSize` (2 Mo par défaut), puis ferme la connexion : coût borné, noté par le développeur.
- Le chemin chunked passe par `PayloadTooLargeException` puis `GlobalExceptionHandler`. Les tests confirment le 413 enveloppé, et rien n'est persisté.

## Constats

### PERF-001 — Le compteur d'octets est par appel de `getInputStream()`, et `getReader()` n'est pas enveloppé [MINEUR]
- **Localisation** : `src/main/java/vasconcelos/volleymatch/config/LivePayloadSizeFilter.java:62-65` (comportement hérité de `SnapshotBodySizeFilter`, non introduit par MV-004).
- **Constat** : `LimitedRequest.getInputStream()` crée un nouveau `LimitedInputStream` (compteur à 0) à chaque appel, et `getReader()` n'est pas surchargé, donc il délègue à la requête réelle sans comptage.
- **Chiffrage** : aujourd'hui seul `@RequestBody` Jackson lit le corps, une fois, via `getInputStream()`. Le plafond tient donc sur le chemin réel. Si un futur filtre ou resolver relit le corps ou utilise `getReader()`, la borne de 1 Mo ne s'appliquerait plus. Le coût n'est pas mesurable aujourd'hui.
- **Preuve** : le code cité ci-dessus. Pas de `getReader` dans la classe.
- **Correction proposée** : à ne pas faire maintenant. Si le filtre est étendu à d'autres routes, mémoriser le `LimitedInputStream` dans le wrapper (un seul compteur) et surcharger `getReader()` pour qu'il lise via ce flux. Environ 10 lignes.
- **Question au développeur** : aucune action requise pour ce ticket. Merci de confirmer qu'aucun autre composant ne lit le corps de ces deux routes.

### PERF-002 — Aucun plafond serveur de repli sur la taille du corps (Tomcat) [MINEUR]
- **Localisation** : `application*.properties` (aucune propriété `server.tomcat.*`).
- **Constat** : la protection repose uniquement sur ce filtre applicatif, limité aux 2 routes. Les autres POST/PUT authentifiés (`/teams`, `/players`, etc.) restent sans limite de corps hors défaut Tomcat. Le défaut de `max-http-form-post-size` (2 Mo) ne s'applique qu'aux formulaires, pas au JSON.
- **Chiffrage** : même nature de risque que l'ancien POST live-stats (désérialisation en mémoire) sur des endpoints dont les DTO sont petits. Gravité faible : la décision Q1 du ticket exclut explicitement ces routes, et les corps attendus font quelques centaines d'octets.
- **Preuve** : `grep "server\." src/main/resources/*.properties` ne renvoie que `server.port`.
- **Correction proposée** : hors périmètre de ce ticket. Pour un autre ticket, un filtre générique à 64 Ko ou 1 Mo sur `/api/v1/**` et un relèvement uniquement sur les 2 routes live. Signalé, non exigé. Modifier les properties est interdit sans demande explicite.
- **Question au développeur** : aucune.

## Conclusion
Aucun problème bloquant ni majeur. Le chemin chaud public n'est pas touché (GET, aucune regex). Le buffering est borné à 1 Mo par requête, ce qui améliore le risque mémoire en native. Rien à demander de plus en complexité.
