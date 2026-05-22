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