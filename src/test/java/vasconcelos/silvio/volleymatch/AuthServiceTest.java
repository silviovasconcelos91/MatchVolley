package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import vasconcelos.silvio.volleymatch.dto.auth.AuthResponse;
import vasconcelos.silvio.volleymatch.dto.auth.LoginRequest;
import vasconcelos.silvio.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.silvio.volleymatch.dto.auth.UserResponse;
import vasconcelos.silvio.volleymatch.mapper.UserMapper;
import vasconcelos.silvio.volleymatch.model.user.AppUser;
import vasconcelos.silvio.volleymatch.model.user.RefreshToken;
import vasconcelos.silvio.volleymatch.repository.AppUserRepository;
import vasconcelos.silvio.volleymatch.service.AuthService;
import vasconcelos.silvio.volleymatch.service.JwtService;
import vasconcelos.silvio.volleymatch.service.RefreshTokenService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private AuthService authService;

    @Test
    void should_registerUser_when_emailAndPseudoAreNew() {
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
    void should_returnTokens_when_loginCredentialsAreValid() {
        AppUser user = AppUser.builder().email("user@test.com").pseudo("user").password("hashed").build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh-uuid")
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        when(appUserRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-jwt");
        when(refreshTokenService.create(user)).thenReturn(refreshToken);

        AuthResponse result = authService.login(new LoginRequest("user@test.com", "pass123"));

        assertThat(result.accessToken()).isEqualTo("access-jwt");
        assertThat(result.refreshToken()).isEqualTo("refresh-uuid");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void should_throwException_when_loginCredentialsAreInvalid() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
