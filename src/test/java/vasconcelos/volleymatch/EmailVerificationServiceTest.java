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
    void should_throwEmailNotVerified_when_attemptResendOnLoginWithTokenInCooldown() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(UUID.randomUUID()).user(user)
                .expiresAt(LocalDateTime.now().plusHours(10))
                .lastSentAt(LocalDateTime.now().minusMinutes(15))
                .verified(false)
                .build();
        when(tokenRepository.findByUser(user)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.attemptResendOnLogin(user))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(resendEmailService, never()).sendVerificationEmail(any(), any());
    }

    @Test
    void should_createAndSendEmail_when_resendWithNoExistingToken() {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("p").build();
        when(appUserRepository.findByEmail("t@t.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUser(user)).thenReturn(Optional.empty());
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        emailVerificationService.resendVerification("t@t.com");

        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(resendEmailService).sendVerificationEmail(
                eq("t@t.com"),
                contains("auth:verify?token="));
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