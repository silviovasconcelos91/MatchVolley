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