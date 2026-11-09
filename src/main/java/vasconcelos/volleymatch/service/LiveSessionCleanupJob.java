package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.repository.LiveSessionRepository;

import java.time.Clock;

/**
 * Hourly purge of expired live sessions (ended + 24h, or idle LIVE + 12h).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionCleanupJob {

    private final LiveSessionRepository liveSessionRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredSessions() {
        int deleted = liveSessionRepository.deleteExpired(clock.instant());
        if (deleted > 0) {
            log.info("Purged {} expired live session(s)", deleted);
        }
    }
}
