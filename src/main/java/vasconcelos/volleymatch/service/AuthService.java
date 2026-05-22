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