# Email Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Block login until users click an email verification link sent on registration, using Resend API for email delivery.

**Architecture:** Token-based verification using a new `EmailVerificationToken` JPA entity (mirrors existing `RefreshToken` pattern). `EmailVerificationService` owns all verification logic; `ResendEmailService` wraps the Resend REST API via Spring `RestClient`. `AuthService.register()` creates the token and triggers the email; `AuthService.login()` blocks unverified users and auto-resends the link if the hourly cooldown has passed.

**Tech Stack:** Spring Boot 4, JPA/Hibernate, Flyway, Resend REST API, Spring `RestClient`, JUnit 5 + Mockito + AssertJ.

---

### Task 1: Flyway migration V5

**Files:**
- Create: `src/main/resources/db/migration/V5__add_email_verification.sql`

- [ ] **Step 1: Create migration file**

```sql
-- Set existing users as verified so they are not locked out
ALTER TABLE app_users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
UPDATE app_users SET email_verified = TRUE;
ALTER TABLE app_users ALTER COLUMN email_verified SET NOT NULL;

CREATE TABLE email_verification_tokens
(
    id           UUID      PRIMARY KEY,
    token        UUID      NOT NULL UNIQUE,
    user_id      UUID      NOT NULL UNIQUE REFERENCES app_users (id),
    expires_at   TIMESTAMP NOT NULL,
    last_sent_at TIMESTAMP NOT NULL,
    verified     BOOLEAN   NOT NULL DEFAULT FALSE
);
```

- [ ] **Step 2: Run tests to confirm migration applies cleanly**

```bash
./mvnw test --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V5__add_email_verification.sql
git commit -m "feat: add email_verified column and email_verification_tokens table"
```

---

### Task 2: AppUser entity — add emailVerified field

**Files:**
- Modify: `src/main/java/vasconcelos/volleymatch/model/user/AppUser.java`

- [ ] **Step 1: Add emailVerified field**

Add `import lombok.Setter;` to the imports and add the field after `createdAt`:

```java
@Column(name = "email_verified", nullable = false)
@Builder.Default
@Setter
private boolean emailVerified = false;
```

Full updated file:

```java
package vasconcelos.volleymatch.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String pseudo;

    @Column(nullable = false)
    private String password;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    @Setter
    private boolean emailVerified = false;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        return email;
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./mvnw test --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/model/user/AppUser.java
git commit -m "feat: add emailVerified field to AppUser"
```

---

### Task 3: EmailVerificationToken entity + repository

**Files:**
- Create: `src/main/java/vasconcelos/volleymatch/model/user/EmailVerificationToken.java`
- Create: `src/main/java/vasconcelos/volleymatch/repository/EmailVerificationTokenRepository.java`

- [ ] **Step 1: Create EmailVerificationToken entity**

```java
package vasconcelos.volleymatch.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID token;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;
}
```

- [ ] **Step 2: Create EmailVerificationTokenRepository**

```java
package vasconcelos.volleymatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.EmailVerificationToken;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByToken(UUID token);
    Optional<EmailVerificationToken> findByUser(AppUser user);
}
```

- [ ] **Step 3: Run tests**

```bash
./mvnw test --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/model/user/EmailVerificationToken.java \
        src/main/java/vasconcelos/volleymatch/repository/EmailVerificationTokenRepository.java
git commit -m "feat: add EmailVerificationToken entity and repository"
```

---

### Task 4: Custom exceptions + GlobalExceptionHandler

**Files:**
- Create: `src/main/java/vasconcelos/volleymatch/exception/EmailNotVerifiedException.java`
- Create: `src/main/java/vasconcelos/volleymatch/exception/TokenExpiredException.java`
- Create: `src/main/java/vasconcelos/volleymatch/exception/RateLimitException.java`
- Modify: `src/main/java/vasconcelos/volleymatch/config/GlobalExceptionHandler.java`

- [ ] **Step 1: Create EmailNotVerifiedException**

```java
package vasconcelos.volleymatch.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create TokenExpiredException**

```java
package vasconcelos.volleymatch.exception;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Create RateLimitException**

```java
package vasconcelos.volleymatch.exception;

public class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
```

- [ ] **Step 4: Add three handlers to GlobalExceptionHandler**

Add the following imports and methods to `GlobalExceptionHandler.java`:

```java
import vasconcelos.volleymatch.exception.EmailNotVerifiedException;
import vasconcelos.volleymatch.exception.RateLimitException;
import vasconcelos.volleymatch.exception.TokenExpiredException;
```

```java
@ExceptionHandler(EmailNotVerifiedException.class)
public ResponseEntity<ApiResponse<Void>> handleEmailNotVerified(EmailNotVerifiedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.<Void>builder()
                    .data(null)
                    .message(ex.getMessage())
                    .status(HttpStatus.FORBIDDEN.value())
                    .build());
}

@ExceptionHandler(TokenExpiredException.class)
public ResponseEntity<ApiResponse<Void>> handleTokenExpired(TokenExpiredException ex) {
    return ResponseEntity.status(HttpStatus.GONE)
            .body(ApiResponse.<Void>builder()
                    .data(null)
                    .message(ex.getMessage())
                    .status(HttpStatus.GONE.value())
                    .build());
}

@ExceptionHandler(RateLimitException.class)
public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(ApiResponse.<Void>builder()
                    .data(null)
                    .message(ex.getMessage())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .build());
}
```

- [ ] **Step 5: Run tests**

```bash
./mvnw test --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/exception/ \
        src/main/java/vasconcelos/volleymatch/config/GlobalExceptionHandler.java
git commit -m "feat: add custom exceptions and HTTP handlers (403, 410, 429)"
```

---

### Task 5: ResendEmailService + ResendVerificationRequest DTO

**Files:**
- Create: `src/main/java/vasconcelos/volleymatch/dto/auth/ResendVerificationRequest.java`
- Create: `src/main/java/vasconcelos/volleymatch/service/ResendEmailService.java`

- [ ] **Step 1: Create ResendVerificationRequest DTO**

```java
package vasconcelos.volleymatch.dto.auth;

public record ResendVerificationRequest(String email) {}
```

- [ ] **Step 2: Create ResendEmailService**

```java
package vasconcelos.volleymatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;

    public ResendEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    public void sendVerificationEmail(String to, String verifyUrl) {
        Map<String, Object> body = Map.of(
                "from", fromEmail,
                "to", List.of(to),
                "subject", "Verify your MatchVolley account",
                "html", "<p>Click <a href='" + verifyUrl + "'>here</a> to verify your account.</p>"
                        + "<p>This link expires in 24 hours.</p>"
        );
        restClient.post()
                .uri("https://api.resend.com/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
```

- [ ] **Step 3: Run build**

```bash
./mvnw compile --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/service/ResendEmailService.java \
        src/main/java/vasconcelos/volleymatch/dto/auth/ResendVerificationRequest.java
git commit -m "feat: add ResendEmailService and ResendVerificationRequest"
```

---

### Task 6: EmailVerificationService (TDD)

**Files:**
- Create: `src/test/java/vasconcelos/volleymatch/EmailVerificationServiceTest.java`
- Create: `src/main/java/vasconcelos/volleymatch/service/EmailVerificationService.java`

- [ ] **Step 1: Write failing tests**

```java
package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.volleymatch.exception.EmailNotVerifiedException;
import vasconcelos.volleymatch.exception.RateLimitException;
import vasconcelos.volleymatch.exception.TokenExpiredException;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.EmailVerificationToken;
import vasconcelos.volleymatch.repository.AppUserRepository;
import vasconcelos.volleymatch.repository.EmailVerificationTokenRepository;
import vasconcelos.volleymatch.service.EmailVerificationService;
import vasconcelos.volleymatch.service.ResendEmailService;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ResendEmailService resendEmailService;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(
                tokenRepository, appUserRepository, resendEmailService, "http://localhost:8080");
    }

    @Test
    void should_createTokenAndSendEmail_when_sendVerification() {
        AppUser user = AppUser.builder().email("test@test.com").pseudo("test").password("pass").build();
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        emailVerificationService.sendVerification(user);

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(resendEmailService).sendVerificationEmail(
                eq("test@test.com"),
                contains("auth:verify?token="));
    }

    @Test
    void should_throwNotFound_when_verifyWithUnknownToken() {
        UUID token = UUID.randomUUID();
        when(tokenRepository.findByToken(token)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_doNothing_when_verifyAlreadyVerifiedToken() {
        UUID tokenValue = UUID.randomUUID();
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenValue).user(user)
                .expiresAt(LocalDateTime.now().plusHours(10))
                .lastSentAt(LocalDateTime.now())
                .verified(true)
                .build();
        when(tokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));

        emailVerificationService.verify(tokenValue);

        verify(tokenRepository, never()).save(any());
    }

    @Test
    void should_throwTokenExpired_when_verifyExpiredToken() {
        UUID tokenValue = UUID.randomUUID();
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenValue).user(user)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .lastSentAt(LocalDateTime.now().minusHours(25))
                .verified(false)
                .build();
        when(tokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.verify(tokenValue))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void should_markUserAndTokenVerified_when_validToken() {
        UUID tokenValue = UUID.randomUUID();
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenValue).user(user)
                .expiresAt(LocalDateTime.now().plusHours(10))
                .lastSentAt(LocalDateTime.now().minusHours(1))
                .verified(false)
                .build();
        when(tokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));

        emailVerificationService.verify(tokenValue);

        assertThat(token.isVerified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(tokenRepository).save(token);
        verify(appUserRepository).save(user);
    }

    @Test
    void should_throwNotFound_when_resendForUnknownEmail() {
        when(appUserRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.resendVerification("unknown@test.com"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_throwAlreadyVerified_when_resendForVerifiedUser() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p")
                .emailVerified(true).build();
        when(appUserRepository.findByEmail("t@t.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> emailVerificationService.resendVerification("t@t.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already verified");
    }

    @Test
    void should_throwRateLimit_when_resendTooSoon() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(UUID.randomUUID()).user(user)
                .expiresAt(LocalDateTime.now().plusHours(10))
                .lastSentAt(LocalDateTime.now().minusMinutes(30))
                .verified(false)
                .build();
        when(appUserRepository.findByEmail("t@t.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUser(user)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.resendVerification("t@t.com"))
                .isInstanceOf(RateLimitException.class);
    }

    @Test
    void should_resendEmail_when_cooldownPassed() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(UUID.randomUUID()).user(user)
                .expiresAt(LocalDateTime.now().plusHours(10))
                .lastSentAt(LocalDateTime.now().minusHours(2))
                .verified(false)
                .build();
        when(appUserRepository.findByEmail("t@t.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUser(user)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenReturn(token);

        emailVerificationService.resendVerification("t@t.com");

        verify(resendEmailService).sendVerificationEmail(
                eq("t@t.com"),
                contains("auth:verify?token="));
    }

    @Test
    void should_throwEmailNotVerified_when_attemptResendOnLoginWithNoToken() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        when(tokenRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.attemptResendOnLogin(user))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void should_resendAndThrow_when_attemptResendOnLoginWithExpiredCooldown() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(UUID.randomUUID()).user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .lastSentAt(LocalDateTime.now().minusHours(2))
                .verified(false)
                .build();
        when(tokenRepository.findByUser(user)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenReturn(token);

        assertThatThrownBy(() -> emailVerificationService.attemptResendOnLogin(user))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(resendEmailService).sendVerificationEmail(
                eq("t@t.com"),
                contains("auth:verify?token="));
    }
}
```

- [ ] **Step 2: Run tests to confirm compilation failure**

```bash
./mvnw test -Dtest=EmailVerificationServiceTest --no-transfer-progress
```
Expected: `COMPILATION ERROR` — `EmailVerificationService` does not exist yet

- [ ] **Step 3: Create EmailVerificationService**

```java
package vasconcelos.volleymatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.exception.EmailNotVerifiedException;
import vasconcelos.volleymatch.exception.RateLimitException;
import vasconcelos.volleymatch.exception.TokenExpiredException;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.EmailVerificationToken;
import vasconcelos.volleymatch.repository.AppUserRepository;
import vasconcelos.volleymatch.repository.EmailVerificationTokenRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final AppUserRepository appUserRepository;
    private final ResendEmailService resendEmailService;
    private final String baseUrl;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            AppUserRepository appUserRepository,
            ResendEmailService resendEmailService,
            @Value("${app.base-url}") String baseUrl) {
        this.tokenRepository = tokenRepository;
        this.appUserRepository = appUserRepository;
        this.resendEmailService = resendEmailService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public void sendVerification(AppUser user) {
        UUID tokenValue = UUID.randomUUID();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .lastSentAt(LocalDateTime.now())
                .build();
        tokenRepository.save(token);
        resendEmailService.sendVerificationEmail(user.getEmail(), buildLink(tokenValue));
    }

    @Transactional
    public void verify(UUID tokenValue) {
        EmailVerificationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new NoSuchElementException("Verification token not found"));
        if (token.isVerified()) return;
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new TokenExpiredException("Verification link expired. Request a new one.");
        token.setVerified(true);
        tokenRepository.save(token);
        AppUser user = token.getUser();
        user.setEmailVerified(true);
        appUserRepository.save(user);
    }

    @Transactional
    public void resendVerification(String email) {
        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        if (user.isEmailVerified())
            throw new IllegalArgumentException("Email already verified");
        Optional<EmailVerificationToken> existing = tokenRepository.findByUser(user);
        if (existing.isPresent() && isInCooldown(existing.get()))
            throw new RateLimitException(
                    "Please wait before requesting another verification email",
                    cooldownSecondsRemaining(existing.get()));
        if (existing.isEmpty()) {
            sendVerification(user);
        } else {
            EmailVerificationToken token = existing.get();
            token.setToken(UUID.randomUUID());
            token.setExpiresAt(LocalDateTime.now().plusHours(24));
            token.setLastSentAt(LocalDateTime.now());
            tokenRepository.save(token);
            resendEmailService.sendVerificationEmail(user.getEmail(), buildLink(token.getToken()));
        }
    }

    @Transactional
    public void attemptResendOnLogin(AppUser user) {
        tokenRepository.findByUser(user).ifPresent(token -> {
            if (!isInCooldown(token)) {
                token.setToken(UUID.randomUUID());
                token.setExpiresAt(LocalDateTime.now().plusHours(24));
                token.setLastSentAt(LocalDateTime.now());
                tokenRepository.save(token);
                resendEmailService.sendVerificationEmail(user.getEmail(), buildLink(token.getToken()));
            }
        });
        throw new EmailNotVerifiedException("Please verify your email before logging in");
    }

    private String buildLink(UUID tokenValue) {
        return baseUrl + "/api/v1/auth:verify?token=" + tokenValue;
    }

    private boolean isInCooldown(EmailVerificationToken token) {
        return token.getLastSentAt().isAfter(LocalDateTime.now().minusHours(1));
    }

    private long cooldownSecondsRemaining(EmailVerificationToken token) {
        return ChronoUnit.SECONDS.between(LocalDateTime.now(), token.getLastSentAt().plusHours(1));
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=EmailVerificationServiceTest --no-transfer-progress
```
Expected: `BUILD SUCCESS`, all 11 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/service/EmailVerificationService.java \
        src/test/java/vasconcelos/volleymatch/EmailVerificationServiceTest.java
git commit -m "feat: add EmailVerificationService with full test coverage"
```

---

### Task 7: AuthService updates (TDD)

**Files:**
- Modify: `src/test/java/vasconcelos/volleymatch/AuthServiceTest.java`
- Modify: `src/main/java/vasconcelos/volleymatch/service/AuthService.java`

- [ ] **Step 1: Replace AuthServiceTest.java with updated tests**

```java
package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import vasconcelos.volleymatch.dto.auth.AuthResponse;
import vasconcelos.volleymatch.dto.auth.LoginRequest;
import vasconcelos.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.volleymatch.dto.auth.UserResponse;
import vasconcelos.volleymatch.exception.EmailNotVerifiedException;
import vasconcelos.volleymatch.mapper.UserMapper;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.RefreshToken;
import vasconcelos.volleymatch.repository.AppUserRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.EmailVerificationService;
import vasconcelos.volleymatch.service.JwtService;
import vasconcelos.volleymatch.service.RefreshTokenService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserMapper userMapper;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    @Test
    void should_registerUserAndSendVerificationEmail_when_emailAndPseudoAreNew() {
        RegisterRequest req = new RegisterRequest("new@test.com", "newuser", "pass123");
        AppUser saved = AppUser.builder().email("new@test.com").pseudo("newuser").password("hashed").build();
        UserResponse expected = new UserResponse(UUID.randomUUID(), "new@test.com", "newuser");
        when(appUserRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(appUserRepository.existsByPseudo("newuser")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(appUserRepository.save(any())).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(expected);

        UserResponse result = authService.register(req);

        assertThat(result.email()).isEqualTo("new@test.com");
        verify(appUserRepository).save(any());
        verify(emailVerificationService).sendVerification(saved);
    }

    @Test
    void should_throwIllegalArgument_when_emailAlreadyExists() {
        when(appUserRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("existing@test.com", "user", "pass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void should_throwIllegalArgument_when_pseudoAlreadyExists() {
        when(appUserRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(appUserRepository.existsByPseudo("taken")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("new@test.com", "taken", "pass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pseudo");
    }

    @Test
    void should_returnTokens_when_loginWithVerifiedEmail() {
        AppUser user = AppUser.builder()
                .email("user@test.com").pseudo("user").password("hashed")
                .emailVerified(true)
                .build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-uuid").user(user).expiresAt(LocalDateTime.now().plusDays(30)).build();
        when(appUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-jwt");
        when(refreshTokenService.create(user)).thenReturn(refreshToken);

        AuthResponse result = authService.login(new LoginRequest("user@test.com", "pass123"));

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-uuid");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void should_throwEmailNotVerified_when_loginWithUnverifiedEmail() {
        AppUser user = AppUser.builder()
                .email("user@test.com").pseudo("user").password("hashed")
                .emailVerified(false)
                .build();
        when(appUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new EmailNotVerifiedException("Please verify your email before logging in"))
                .when(emailVerificationService).attemptResendOnLogin(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "pass123")))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("verify your email");
    }

    @Test
    void should_throwException_when_loginCredentialsAreInvalid() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
```

- [ ] **Step 2: Run tests to confirm failures on new tests**

```bash
./mvnw test -Dtest=AuthServiceTest --no-transfer-progress
```
Expected: failures — `AuthService` still missing `EmailVerificationService` dependency

- [ ] **Step 3: Replace AuthService.java**

```java
package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.auth.AuthResponse;
import vasconcelos.volleymatch.dto.auth.LoginRequest;
import vasconcelos.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.volleymatch.dto.auth.UserResponse;
import vasconcelos.volleymatch.mapper.UserMapper;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.RefreshToken;
import vasconcelos.volleymatch.repository.AppUserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email()))
            throw new IllegalArgumentException("Email already in use");
        if (appUserRepository.existsByPseudo(request.pseudo()))
            throw new IllegalArgumentException("Pseudo already in use");
        AppUser user = AppUser.builder()
                .email(request.email())
                .pseudo(request.pseudo())
                .password(passwordEncoder.encode(request.password()))
                .build();
        AppUser saved = appUserRepository.save(user);
        emailVerificationService.sendVerification(saved);
        return userMapper.toResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        AppUser user = appUserRepository.findByEmail(request.email()).orElseThrow();
        if (!user.isEmailVerified()) {
            emailVerificationService.attemptResendOnLogin(user);
        }
        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.create(user);
        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken newToken = refreshTokenService.rotate(refreshTokenValue);
        String accessToken = jwtService.generateToken(newToken.getUser());
        return new AuthResponse(accessToken, newToken.getToken());
    }

    public void logout(String refreshTokenValue) {
        refreshTokenService.revoke(refreshTokenValue);
    }

    public AppUser getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
```

- [ ] **Step 4: Run all tests**

```bash
./mvnw test --no-transfer-progress
```
Expected: `BUILD SUCCESS`, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/service/AuthService.java \
        src/test/java/vasconcelos/volleymatch/AuthServiceTest.java
git commit -m "feat: send verification on register, block login for unverified users"
```

---

### Task 8: AuthController + SecurityConfig + application.properties

**Files:**
- Modify: `src/main/java/vasconcelos/volleymatch/controller/AuthController.java`
- Modify: `src/main/java/vasconcelos/volleymatch/config/SecurityConfig.java`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Replace AuthController.java**

```java
package vasconcelos.volleymatch.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vasconcelos.volleymatch.dto.auth.AuthResponse;
import vasconcelos.volleymatch.dto.auth.LoginRequest;
import vasconcelos.volleymatch.dto.auth.RefreshRequest;
import vasconcelos.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.volleymatch.dto.auth.ResendVerificationRequest;
import vasconcelos.volleymatch.dto.auth.UserResponse;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.EmailVerificationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/auth:register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<UserResponse>builder()
                        .data(user)
                        .message("User registered. Check your email to verify your account.")
                        .status(201).build());
    }

    @PostMapping("/auth:login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .data(auth).message("Login successful").status(200).build());
    }

    @PostMapping("/auth:refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody RefreshRequest request) {
        AuthResponse auth = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .data(auth).message("Token refreshed").status(200).build());
    }

    @PostMapping("/auth:logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .data(null).message("Logged out").status(200).build());
    }

    @GetMapping("/auth:verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam UUID token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .data(null).message("Email verified successfully").status(200).build());
    }

    @PostMapping("/auth:resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .data(null).message("Verification email sent").status(200).build());
    }
}
```

- [ ] **Step 2: Update SecurityConfig — add new public endpoints**

In `SecurityConfig.java`, replace the `requestMatchers(...)` block with:

```java
.requestMatchers(
        "/api/v1/auth:register",
        "/api/v1/auth:login",
        "/api/v1/auth:refresh",
        "/api/v1/auth:logout",
        "/api/v1/auth:verify",
        "/api/v1/auth:resend-verification",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
).permitAll()
```

- [ ] **Step 3: Add configuration properties to application.properties**

Append to the end of `application.properties`:

```properties
resend.api-key=${RESEND_API_KEY:re_placeholder}
resend.from-email=${RESEND_FROM_EMAIL:noreply@matchvolley.app}
app.base-url=${APP_BASE_URL:http://localhost:8080}
```

- [ ] **Step 4: Run all tests**

```bash
./mvnw test --no-transfer-progress
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/vasconcelos/volleymatch/controller/AuthController.java \
        src/main/java/vasconcelos/volleymatch/config/SecurityConfig.java \
        src/main/resources/application.properties
git commit -m "feat: add verify and resend-verification endpoints, update security config"
```

---

## Environment Variables

Before running the application, set these in your environment or `.env`:

| Variable | Description |
|---|---|
| `RESEND_API_KEY` | Your Resend API key from resend.com |
| `RESEND_FROM_EMAIL` | Sender address verified in Resend |
| `APP_BASE_URL` | Base URL of the API (e.g. `https://api.matchvolley.app`) |