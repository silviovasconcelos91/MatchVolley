package vasconcelos.volleymatch.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-IP rate limit on unauthenticated public endpoints ({@code /api/v1/public/**}) returning 429.
 */
@Component
@RequiredArgsConstructor
public class PublicRateLimitFilter extends OncePerRequestFilter {

    static final String PUBLIC_PREFIX = "/api/v1/public/";
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final PublicRateLimiter rateLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLIC_PREFIX)
                || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long retryAfter = rateLimiter.tryAcquire(clientIp(request));
        if (retryAfter > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"data\":null,\"message\":\"Too many requests\",\"status\":429}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Behind the hosting reverse proxy, the client IP is the last X-Forwarded-For entry
     * (appended by the proxy itself; earlier entries are client-controlled and spoofable).
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}
