package vasconcelos.volleymatch.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.exception.EmailNotVerifiedException;
import vasconcelos.volleymatch.exception.RateLimitException;
import vasconcelos.volleymatch.exception.TokenExpiredException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .data(null)
                        .message("Mauvais email ou mot de passe")
                        .status(HttpStatus.BAD_REQUEST.value())
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<Void>builder()
                        .data(null)
                        .message(ex.getMessage())
                        .status(HttpStatus.CONFLICT.value())
                        .build());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.<Void>builder()
                        .data(null)
                        .message(ex.getMessage())
                        .status(HttpStatus.NOT_FOUND.value())
                        .build());
    }

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
}
