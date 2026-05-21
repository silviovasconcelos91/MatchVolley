package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import vasconcelos.volleymatch.service.JwtService;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "test-jwt-secret-key-for-testing!".getBytes());
        jwtService = new JwtService(secret, 900000L);
    }

    @Test
    void should_generateToken_when_userProvided() {
        UserDetails user = new User("test@example.com", "pass", List.of());
        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
    }

    @Test
    void should_extractUsername_when_tokenIsValid() {
        UserDetails user = new User("test@example.com", "pass", List.of());
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }

    @Test
    void should_returnTrue_when_tokenIsValid() {
        UserDetails user = new User("test@example.com", "pass", List.of());
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void should_returnFalse_when_tokenBelongsToOtherUser() {
        UserDetails user1 = new User("a@test.com", "pass", List.of());
        UserDetails user2 = new User("b@test.com", "pass", List.of());
        String token = jwtService.generateToken(user1);
        assertThat(jwtService.isTokenValid(token, user2)).isFalse();
    }

    @Test
    void should_throwException_when_tokenIsExpired() {
        JwtService shortLived = new JwtService(
                Base64.getEncoder().encodeToString("test-jwt-secret-key-for-testing!".getBytes()),
                -1L);
        UserDetails user = new User("test@example.com", "pass", List.of());
        String token = shortLived.generateToken(user);
        assertThatThrownBy(() -> shortLived.extractUsername(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
