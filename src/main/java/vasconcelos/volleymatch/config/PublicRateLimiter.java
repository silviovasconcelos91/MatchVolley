package vasconcelos.volleymatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window (1 minute) rate limiter keyed by client IP, for public endpoints.
 * Single-instance only: counters are not shared across replicas.
 */
@Component
public class PublicRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final Clock clock;
    private final int limitPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public PublicRateLimiter(Clock clock, @Value("${app.live.rate-limit-per-minute:120}") int limitPerMinute) {
        this.clock = clock;
        this.limitPerMinute = limitPerMinute;
    }

    /**
     * @return 0 when the request is allowed, otherwise the number of seconds until the next window
     */
    public long tryAcquire(String clientKey) {
        long now = clock.millis();
        long windowId = now / WINDOW_MILLIS;
        Window window = windows.compute(clientKey, (key, current) ->
                current == null || current.id() != windowId
                        ? new Window(windowId, 1)
                        : new Window(windowId, current.count() + 1));
        if (window.count() <= limitPerMinute) {
            return 0;
        }
        long remainingMillis = WINDOW_MILLIS - (now % WINDOW_MILLIS);
        return Math.max(1, (remainingMillis + 999) / 1000);
    }

    @Scheduled(fixedRate = WINDOW_MILLIS)
    public void evictStaleWindows() {
        long currentWindow = clock.millis() / WINDOW_MILLIS;
        windows.values().removeIf(w -> w.id() < currentWindow);
    }

    private record Window(long id, int count) {}
}
