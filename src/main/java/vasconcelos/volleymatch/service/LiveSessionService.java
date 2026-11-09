package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.livesession.CreateLiveSessionRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotResponse;
import vasconcelos.volleymatch.dto.livesession.UpdateLiveSessionRequest;
import vasconcelos.volleymatch.exception.BadRequestException;
import vasconcelos.volleymatch.exception.ForbiddenException;
import vasconcelos.volleymatch.exception.LiveSessionEndedException;
import vasconcelos.volleymatch.mapper.LiveSessionMapper;
import vasconcelos.volleymatch.model.live.LiveSession;
import vasconcelos.volleymatch.model.live.LiveSessionStatus;
import vasconcelos.volleymatch.model.team.Team;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.LiveSessionRepository;
import vasconcelos.volleymatch.repository.TeamRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LiveSessionService {

    static final Duration LIVE_TTL = Duration.ofHours(12);
    static final Duration ENDED_TTL = Duration.ofHours(24);
    static final int MAX_CODE_ATTEMPTS = 10;
    private static final Set<String> VENUES = Set.of("home", "away");
    private static final int MAX_TITLE_LENGTH = 255;

    private final LiveSessionRepository liveSessionRepository;
    private final TeamRepository teamRepository;
    private final LiveSessionMapper liveSessionMapper;
    private final LiveSessionCodeGenerator codeGenerator;
    private final LiveStatsPayloadValidator payloadValidator;
    private final AuthService authService;
    private final Clock clock;

    @Value("${app.live.public-web-url:http://localhost:8081}")
    private String publicWebUrl;

    public LiveSessionDto create(CreateLiveSessionRequest request) {
        validateCreate(request);
        AppUser user = authService.getCurrentUser();
        Team team = teamRepository.findByIdAndUser(request.teamId(), user)
                .orElseThrow(() -> new ForbiddenException("Team does not belong to the current user"));

        Instant now = now();
        LiveSession session = LiveSession.builder()
                .code(generateUniqueCode())
                .owner(user)
                .team(team)
                .title(request.title().trim())
                .venue(request.venue())
                .showPlayerNames(Boolean.TRUE.equals(request.showPlayerNames()))
                .status(LiveSessionStatus.LIVE)
                .revision(0L)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(now.plus(LIVE_TTL))
                .build();
        liveSessionRepository.save(session);
        return toDto(session);
    }

    public LiveSnapshotResponse updateSnapshot(UUID sessionId, LiveSnapshotRequest request) {
        if (request == null || request.revision() == null || request.revision() < 1) {
            throw new BadRequestException("revision is required and must be >= 1");
        }
        AppUser user = authService.getCurrentUser();
        LiveSession session = liveSessionRepository.findByIdForUpdate(sessionId)
                .filter(this::isNotExpired)
                .orElseThrow(() -> notFound(sessionId));
        checkOwner(session, user);
        if (session.getStatus() == LiveSessionStatus.ENDED) {
            throw new LiveSessionEndedException("Live session has ended");
        }
        if (request.revision() <= session.getRevision()) {
            return liveSessionMapper.toSnapshotResponse(session, false);
        }
        payloadValidator.validate(request.payload());

        Instant now = now();
        session.setSnapshot(request.payload());
        session.setRevision(request.revision());
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(LIVE_TTL));
        return liveSessionMapper.toSnapshotResponse(session, true);
    }

    public LiveSessionDto end(UUID sessionId) {
        AppUser user = authService.getCurrentUser();
        LiveSession session = loadOwned(sessionId, user);
        markEnded(session);
        return toDto(session);
    }

    public LiveSessionDto update(UUID sessionId, UpdateLiveSessionRequest request) {
        if (request == null || request.showPlayerNames() == null) {
            throw new BadRequestException("showPlayerNames is required");
        }
        AppUser user = authService.getCurrentUser();
        LiveSession session = loadOwned(sessionId, user);
        session.setShowPlayerNames(request.showPlayerNames());
        session.setUpdatedAt(now());
        return toDto(session);
    }

    /**
     * Links the saved match to the live session and ends it. Never fails the match save:
     * an unknown, expired or foreign session is ignored.
     */
    public void attachMatch(UUID sessionId, String matchId, AppUser user) {
        Optional<LiveSession> found = liveSessionRepository.findById(sessionId)
                .filter(this::isNotExpired)
                .filter(s -> s.getOwner().getId().equals(user.getId()));
        if (found.isEmpty()) {
            log.warn("Ignoring liveSessionId {} on match {}: unknown, expired or not owned", sessionId, matchId);
            return;
        }
        LiveSession session = found.get();
        session.setMatchId(matchId);
        markEnded(session);
    }

    private void markEnded(LiveSession session) {
        if (session.getStatus() == LiveSessionStatus.ENDED) {
            return;
        }
        Instant now = now();
        session.setStatus(LiveSessionStatus.ENDED);
        session.setEndedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(ENDED_TTL));
    }

    private LiveSession loadOwned(UUID sessionId, AppUser user) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .filter(this::isNotExpired)
                .orElseThrow(() -> notFound(sessionId));
        checkOwner(session, user);
        return session;
    }

    private void checkOwner(LiveSession session, AppUser user) {
        if (!session.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException("Live session does not belong to the current user");
        }
    }

    private boolean isNotExpired(LiveSession session) {
        return session.getExpiresAt().isAfter(now());
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (!liveSessionRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to generate a unique live code");
    }

    private void validateCreate(CreateLiveSessionRequest request) {
        if (request == null || request.teamId() == null) {
            throw new BadRequestException("teamId is required");
        }
        if (request.title() == null || request.title().isBlank() || request.title().length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("title is required (max " + MAX_TITLE_LENGTH + " chars)");
        }
        if (request.venue() == null || !VENUES.contains(request.venue())) {
            throw new BadRequestException("venue must be 'home' or 'away'");
        }
    }

    private LiveSessionDto toDto(LiveSession session) {
        return liveSessionMapper.toDto(session, publicUrl(session.getCode()));
    }

    private String publicUrl(String code) {
        String base = publicWebUrl.endsWith("/") ? publicWebUrl.substring(0, publicWebUrl.length() - 1) : publicWebUrl;
        return base + "/live/" + code;
    }

    /**
     * Millisecond precision so in-memory values equal what the database returns.
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static NoSuchElementException notFound(UUID sessionId) {
        return new NoSuchElementException("Live session not found: " + sessionId);
    }
}
