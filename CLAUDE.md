# CLAUDE.md

## Projet
- API REST Backend
- Lang : Java
- Framework : Spring Boot 4.0.4
- Build : Maven
- DB : PostgreSQL
- ORM : JPA/Hibernate
- Auth : _(ex: JWT)_

## Stack librairies
- **MapStruct** → mapping entités/DTOs
- **Lombok** → réduction boilerplate (getters, builders, etc.)
- **AssertJ** → assertions dans les tests

## Structure
```
controller/   → endpoints HTTP
service/      → logique métier
repository/   → accès données
model/        → entités, DTOs
mapper/       → interfaces MapStruct
config/       → config Spring
```

## Commandes
```bash
./mvnw spring-boot:run    # lancer
./mvnw test               # tests
./mvnw package            # build
./mvnw verify             # build + tests
```

## Conventions

**Code**
- Data classes pour DTOs
- Pas de `!!` — gérer nullabilité explicitement
- Extensions > classes utilitaires statiques
- Lombok : `@Builder`, `@RequiredArgsConstructor` privilégiés

**Mapping**
- Toujours passer par MapStruct — pas de mapping manuel
- Interfaces mapper dans `mapper/`, suffixe `Mapper` (ex: `UserMapper`)
- Annoter avec `@Mapper(componentModel = "spring")`

**Nommage**
- Classes : `PascalCase`
- Fonctions/vars : `camelCase`
- Constantes : `UPPER_SNAKE_CASE`
- Packages : `lowercase`

**API**
- Nommage endpoints : style Google `ressource:action` → `/api/v1/orders:cancel`, `/api/v1/users:search`
- CRUD standard : REST classique → `GET /orders`, `POST /orders`, `DELETE /orders/{id}`
- Codes HTTP corrects : 200, 201, 400, 404, 500
- Réponse structurée : `{ data, message, status }`

**Tests**
- JUnit 5 + AssertJ pour assertions
- `@SpringBootTest` pour intégration
- Nommage : `should_[résultat]_when_[condition]`
- Pas de `assertTrue` / `assertEquals` — utiliser AssertJ (`assertThat(...)`)

## Interdits
- Pas d'import *
- Ne pas modifier `application.yml` / `pom.xml` sans demande
- Ne pas ajouter de dépendances sans les signaler
- Ne pas supprimer de tests
- Ne pas faire de mapping manuel (toujours MapStruct)

## Notes
> Service CRUD simple pour récupérer les joueurs et sauvegarder les stats de match
