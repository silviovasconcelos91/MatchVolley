package vasconcelos.volleymatch.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.exception.BadRequestException;
import vasconcelos.volleymatch.exception.EmailNotVerifiedException;
import vasconcelos.volleymatch.exception.ForbiddenException;
import vasconcelos.volleymatch.exception.LiveSessionEndedException;
import vasconcelos.volleymatch.exception.PayloadTooLargeException;
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

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(LiveSessionEndedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLiveSessionEnded(LiveSessionEndedException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiResponse<Void>> handlePayloadTooLarge(PayloadTooLargeException ex) {
        return error(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<Void>builder()
                        .data(null)
                        .message(message)
                        .status(status.value())
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
