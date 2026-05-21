# User Authentication & Multi-Tenancy — Design Spec

**Date:** 2026-05-20  
**Status:** Approved  
**Stack:** Spring Boot 4.0.4 · Spring Security · JWT (jjwt) · PostgreSQL · Flyway

---

## Context

MatchVolley is a REST backend for volleyball stats. Currently no authentication exists. Clients are React Native (Android/iOS) and a future web SPA. All data (players, teams, matches) must be isolated per user (multi-tenant).

---

## 1. Data Model

### `app_users`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK, auto-generated |
| email | VARCHAR | UNIQUE, NOT NULL |
| pseudo | VARCHAR | UNIQUE, NOT NULL |
| password | VARCHAR | NOT NULL (BCrypt) |
| created_at | TIMESTAMP | NOT NULL |

### `refresh_tokens`
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| token | VARCHAR | UNIQUE, NOT NULL |
| user_id | UUID | FK → app_users, NOT NULL |
| expires_at | TIMESTAMP | NOT NULL |
| revoked | BOOLEAN | DEFAULT false |

### Modified Existing Tables
Add `user_id UUID NOT NULL FK → app_users` to:
- `teams`
- `players`
- `matches`

### Flyway Migration Strategy
1. Create `app_users` table
2. Insert default admin user (fixed UUID `00000000-0000-0000-0000-000000000001`, email `admin@matchvolley.local`)
3. Add `user_id` column (nullable) to `teams`, `players`, `matches`
4. Set all existing records `user_id = <admin UUID>`
5. Alter columns to NOT NULL

---

## 2. Endpoints

All auth endpoints under `/api/v1/auth`.  
Naming: Google style `resource:action`.

| Method | Path | Body | Response | Auth required |
|--------|------|------|----------|---------------|
| POST | `/api/v1/auth:register` | `{ email, pseudo, password }` | `201 { data: { id, email, pseudo } }` · `409` if email/pseudo already taken | No |
| POST | `/api/v1/auth:login` | `{ email, password }` | `200 { data: { accessToken, refreshToken } }` | No |
| POST | `/api/v1/auth:refresh` | `{ refreshToken }` | `200 { data: { accessToken, refreshToken } }` | No |
| POST | `/api/v1/auth:logout` | `{ refreshToken }` | `200 { message: "logged out" }` | No |

### Token Strategy
- **Access token:** JWT signed HS256, expiry 15 minutes
- **Refresh token:** UUID string stored in `refresh_tokens`, expiry 30 days
- **Rotation:** each `/auth:refresh` call revokes old refresh token and issues a new one

---

## 3. Security Architecture

### Public Routes (no auth required)
- `POST /api/v1/auth:register`
- `POST /api/v1/auth:login`
- `POST /api/v1/auth:refresh`
- `POST /api/v1/auth:logout`
- `GET /v3/api-docs/**`
- `GET /swagger-ui/**`

### Protected Routes
All other `/api/v1/**` routes require `Authorization: Bearer <accessToken>`.

### Components
- **`JwtAuthFilter`** (`OncePerRequestFilter`): extracts Bearer token, validates signature and expiry, loads user, injects into `SecurityContextHolder`
- **`JwtService`**: generate/validate/parse JWT
- **`RefreshTokenService`**: create, rotate, revoke refresh tokens
- **`AuthService`**: register, login, refresh, logout logic
- **`UserDetailsServiceImpl`**: loads `AppUser` by email for Spring Security
- **`SecurityConfig`**: `SecurityFilterChain` — stateless session, no CSRF, public/protected route config
- **`BCryptPasswordEncoder`**: password hashing bean

### Data Isolation
Every service method resolves the current user from `SecurityContextHolder` and passes it as a filter to all repository queries. No cross-user data access is possible.

Example pattern:
```java
AppUser currentUser = authService.getCurrentUser();
return teamRepository.findAllByUser(currentUser);
```

---

## 4. New Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

JWT secret and expiry configured via `application.properties` (env var override for prod):
```
app.jwt.secret=<256-bit base64 secret>
app.jwt.access-token-expiration=900000
app.jwt.refresh-token-expiration=2592000000
```

---

## 5. Package Structure

```
model/
  user/
    AppUser.java
    RefreshToken.java
dto/
  auth/
    RegisterRequest.java
    LoginRequest.java
    RefreshRequest.java
    AuthResponse.java
    UserResponse.java
repository/
  AppUserRepository.java
  RefreshTokenRepository.java
service/
  AuthService.java
  JwtService.java
  RefreshTokenService.java
  UserDetailsServiceImpl.java
mapper/
  UserMapper.java
controller/
  AuthController.java
config/
  SecurityConfig.java
  JwtAuthFilter.java
```

---

## 6. Tests

### Unit Tests
- **`AuthServiceTest`**: register (duplicate email, duplicate pseudo, success), login (wrong password, unknown user, success)
- **`JwtServiceTest`**: token generation, valid token, expired token
- **`RefreshTokenServiceTest`**: rotation, revoked token rejection, expired token rejection

### Integration Tests (`@SpringBootTest`)
- **`AuthControllerTest`**: full flow — register → login → call protected route → refresh → logout
- **Data isolation test**: user A cannot access user B's teams/players/matches

### Naming convention
`should_[result]_when_[condition]` — consistent with existing tests.