package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.model.user.RefreshToken;
import vasconcelos.volleymatch.repository.RefreshTokenRepository;
import vasconcelos.volleymatch.service.RefreshTokenService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 2592000000L);
        testUser = AppUser.builder()
                .email("test@test.com")
                .pseudo("testuser")
                .password("hashed")
                .build();
    }

    @Test
    void should_createRefreshToken_when_userProvided() {
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken token = refreshTokenService.create(testUser);

        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getUser()).isEqualTo(testUser);
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void should_revokeOldAndCreateNew_when_rotate() {
        RefreshToken existing = RefreshToken.builder()
                .token("old-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken newToken = refreshTokenService.rotate("old-token");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(newToken.getToken()).isNotEqualTo("old-token");
        assertThat(newToken.getUser()).isEqualTo(testUser);
    }

    @Test
    void should_throwException_when_tokenRevoked() {
        RefreshToken revoked = RefreshToken.builder()
                .token("revoked-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("revoked-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void should_throwException_when_tokenExpired() {
        RefreshToken expired = RefreshToken.builder()
                .token("expired-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void should_throwException_when_tokenNotFound() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
