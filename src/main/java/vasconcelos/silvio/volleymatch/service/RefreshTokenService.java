package vasconcelos.silvio.volleymatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.silvio.volleymatch.model.user.AppUser;
import vasconcelos.silvio.volleymatch.model.user.RefreshToken;
import vasconcelos.silvio.volleymatch.repository.RefreshTokenRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiration}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public RefreshToken create(AppUser user) {
        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .build();
        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken rotate(String tokenValue) {
        RefreshToken existing = findValid(tokenValue);
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return create(existing.getUser());
    }

    @Transactional
    public void revoke(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    private RefreshToken findValid(String tokenValue) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
        if (token.isRevoked()) throw new IllegalArgumentException("Refresh token revoked");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Refresh token expired");
        return token;
    }
}
