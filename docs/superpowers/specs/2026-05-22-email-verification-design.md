# Email Verification on Registration

**Date:** 2026-05-22
**Status:** Approved

## Context

Spring Boot 4 REST API with JWT auth. `AppUser` has no `emailVerified` field. No email service exists. Goal: block login until user clicks a verification link sent on registration.

## Decisions

- Provider: Resend (REST API, no SDK — use Spring `RestClient`)
- Token validity: 24 hours
- Token storage: PostgreSQL (consistent with existing `RefreshToken` pattern)
- Resend cooldown: 1 per hour per user
- Expired token + unverified: account persists, resend available
- Login with unverified account: auto-attempt resend (if cooldown passed) + 403 + message

## Data Model

### New entity: `EmailVerificationToken` (table: `email_verification_tokens`)

| Field | Type | Constraints |
|---|---|---|
| id | UUID | PK |
| token | UUID | unique, not null |
| user | AppUser | FK, unique (1 token per user) |
| expiresAt | LocalDateTime | not null |
| lastSentAt | LocalDateTime | not null |
| verified | boolean | default false |

### Modified: `AppUser`

- Add `emailVerified: boolean` (default `false`)

### Flyway migration

`V{n}__add_email_verification.sql`:
- `ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE`
- `CREATE TABLE email_verification_tokens (...)`

## API

### `POST /api/v1/auth:register`
1. Create `AppUser` with `emailVerified=false`
2. Create `EmailVerificationToken` (UUID, `expiresAt=now+24h`, `lastSentAt=now`)
3. Send mail via Resend: `GET /api/v1/auth:verify?token=xxx`
4. Return `UserResponse` (no JWT — account unverified)

### `GET /api/v1/auth:verify?token=xxx` (public)
| Case | Response |
|---|---|
| Token not found | 404 |
| Already verified | 200 (idempotent) |
| Token expired | 410 Gone + hint to use resend |
| Valid | Set `emailVerified=true`, `verified=true` → 200 |

### `POST /api/v1/auth:login`
- If `emailVerified=false`: attempt resend (if `lastSentAt < now-1h`), return 403 + message "Please verify your email"

### `POST /api/v1/auth:resend-verification` (public, body: `{email}`)
| Case | Response |
|---|---|
| User not found | 404 |
| Already verified | 400 |
| `lastSentAt > now-1h` | 429 + `Retry-After` header |
| OK | Update existing token: new UUID, new `expiresAt`, new `lastSentAt` → send mail → 200 |

## Architecture

### New files

| File | Role |
|---|---|
| `model/user/EmailVerificationToken.java` | JPA entity |
| `repository/EmailVerificationTokenRepository.java` | `findByToken`, `findByUser` |
| `service/EmailVerificationService.java` | Verification + resend logic |
| `service/ResendEmailService.java` | HTTP call to Resend API |

### Modified files

| File | Change |
|---|---|
| `model/user/AppUser.java` | Add `emailVerified` field |
| `service/AuthService.java` | `register()` creates token + sends mail; `login()` checks `emailVerified` |
| `controller/AuthController.java` | Add `GET :verify`, `POST :resend-verification` |
| `config/SecurityConfig.java` | Make new endpoints public |
| `application.properties` | Add `resend.api-key`, `app.base-url` |
| Flyway migration | ALTER + CREATE TABLE |

### Resend integration

REST call: `POST https://api.resend.com/emails`
Header: `Authorization: Bearer {resend.api-key}`
Body: `{ from, to, subject, html }`
No SDK needed — Spring `RestClient` sufficient.

## Tests

### Unit tests (mocked `ResendEmailService`)

- `should_returnError_when_emailNotVerified_onLogin`
- `should_sendEmail_when_register`
- `should_verify_when_validToken`
- `should_throwExpired_when_tokenExpired`
- `should_throwRateLimit_when_resendTooSoon`
- `should_resend_when_cooldownPassed`

### Integration tests (`@SpringBootTest`)

- Full flow: register → verify → login
- Login blocked without verification
- Resend rate limit returns 429
