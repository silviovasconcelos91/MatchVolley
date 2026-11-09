package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.livesession.LiveMatchState;
import vasconcelos.volleymatch.dto.livesession.PublicCurrentSetDto;
import vasconcelos.volleymatch.dto.livesession.PublicLiveViewDto;
import vasconcelos.volleymatch.dto.livesession.PublicPlayerDto;
import vasconcelos.volleymatch.dto.livesession.PublicPointDto;
import vasconcelos.volleymatch.dto.livesession.PublicSetDto;
import vasconcelos.volleymatch.dto.livesession.PublicTopScorerDto;
import vasconcelos.volleymatch.dto.livesession.ScoreDto;
import vasconcelos.volleymatch.mapper.LiveSessionMapper;
import vasconcelos.volleymatch.model.live.LiveSession;
import vasconcelos.volleymatch.model.live.LiveSessionHeader;
import vasconcelos.volleymatch.repository.LiveSessionRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static vasconcelos.volleymatch.service.LiveStatsPayloadValidator.MINE;
import static vasconcelos.volleymatch.service.LiveStatsPayloadValidator.OPP;

/**
 * Builds the public (unauthenticated) live view from the stored snapshot. All score
 * computations are derived from events, so an undo (fewer events) lowers the score.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LivePublicViewService {

    static final int LAST_POINTS_LIMIT = 5;
    static final int TOP_SCORERS_LIMIT = 3;
    private static final String POINT = "point";

    private final LiveSessionRepository liveSessionRepository;
    private final LiveSessionMapper liveSessionMapper;
    private final Clock clock;

    /**
     * Cheap lookup (no snapshot loaded) used to answer conditional requests.
     */
    public Optional<LiveSessionHeader> findHeader(String code) {
        return liveSessionRepository.findHeaderByCode(code, clock.instant());
    }

    public Optional<VersionedView> findView(String code) {
        return liveSessionRepository.findByCodeAndExpiresAtAfter(code, clock.instant())
                .map(session -> new VersionedView(toView(session), etag(new LiveSessionHeader(
                        session.getId(), session.getRevision(), session.getStatus(), session.isShowPlayerNames()))));
    }

    /**
     * Public view with the ETag of the exact session state it was built from.
     */
    public record VersionedView(PublicLiveViewDto view, String etag) {}

    /**
     * Strong ETag that changes whenever the public view can change (revision, status, name visibility),
     * without exposing the session id.
     */
    public static String etag(LiveSessionHeader header) {
        return "\"" + header.revision()
                + "-" + header.status()
                + "-" + (header.showPlayerNames() ? 1 : 0)
                + "-" + Integer.toHexString(header.id().hashCode())
                + "\"";
    }

    public static boolean matchesEtag(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String value = candidate.trim();
            if (value.startsWith("W/")) {
                value = value.substring(2);
            }
            if (value.equals("*") || value.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    PublicLiveViewDto toView(LiveSession session) {
        LiveMatchState state = computeState(session.getSnapshot(), session.isShowPlayerNames());
        return liveSessionMapper.toPublicView(session, state);
    }

    public LiveMatchState computeState(LiveStatsRequest snapshot, boolean showPlayerNames) {
        if (snapshot == null || snapshot.sets() == null || snapshot.sets().isEmpty()) {
            return new LiveMatchState(new ScoreDto(0, 0), null, List.of(), List.of(), List.of());
        }
        List<LiveSetDto> orderedSets = snapshot.sets().stream()
                .sorted(Comparator.comparing(LiveSetDto::setNumber))
                .toList();

        List<PublicSetDto> sets = new ArrayList<>();
        List<PublicPointDto> allPoints = new ArrayList<>();
        PublicCurrentSetDto currentSet = null;
        int setsWonMine = 0;
        int setsWonOpp = 0;

        for (LiveSetDto set : orderedSets) {
            int mine = 0;
            int opp = 0;
            String lastScorer = null;
            List<ScoreDto> trajectory = new ArrayList<>();
            trajectory.add(new ScoreDto(0, 0));

            for (LiveEventDto event : orderedEvents(set)) {
                if (event.scoredFor() == null) {
                    continue;
                }
                if (MINE.equals(event.scoredFor())) {
                    mine++;
                } else {
                    opp++;
                }
                lastScorer = event.scoredFor();
                ScoreDto scoreAfter = new ScoreDto(mine, opp);
                trajectory.add(scoreAfter);
                allPoints.add(toPoint(set.setNumber(), event, scoreAfter, showPlayerNames));
            }

            ScoreDto score = new ScoreDto(mine, opp);
            sets.add(new PublicSetDto(set.setNumber(), score, set.wonBy()));
            if (MINE.equals(set.wonBy())) {
                setsWonMine++;
            } else if (OPP.equals(set.wonBy())) {
                setsWonOpp++;
            }
            currentSet = new PublicCurrentSetDto(set.setNumber(), score, lastScorer, trajectory);
        }

        return new LiveMatchState(
                new ScoreDto(setsWonMine, setsWonOpp),
                currentSet,
                sets,
                lastPoints(allPoints),
                topScorers(orderedSets, showPlayerNames)
        );
    }

    private static List<LiveEventDto> orderedEvents(LiveSetDto set) {
        if (set.events() == null) {
            return List.of();
        }
        return set.events().stream()
                .sorted(Comparator.comparing(LiveEventDto::sequence, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private PublicPointDto toPoint(int setNumber, LiveEventDto event, ScoreDto scoreAfter, boolean showPlayerNames) {
        PublicPlayerDto player = event.player() == null
                ? null
                : liveSessionMapper.toPublicPlayer(event.player(), showPlayerNames && MINE.equals(event.team()));
        return new PublicPointDto(
                event.sequence(),
                setNumber,
                event.scoredFor(),
                scoreAfter,
                event.team(),
                player,
                event.action() == null ? null : liveSessionMapper.toPublicAction(event.action())
        );
    }

    private static List<PublicPointDto> lastPoints(List<PublicPointDto> chronological) {
        int from = Math.max(0, chronological.size() - LAST_POINTS_LIMIT);
        return chronological.subList(from, chronological.size()).reversed();
    }

    private static List<PublicTopScorerDto> topScorers(List<LiveSetDto> sets, boolean showPlayerNames) {
        Map<Integer, Integer> pointsByJersey = new LinkedHashMap<>();
        Map<Integer, String> nameByJersey = new LinkedHashMap<>();
        for (LiveSetDto set : sets) {
            for (LiveEventDto event : orderedEvents(set)) {
                if (!MINE.equals(event.team()) || event.action() == null || !POINT.equals(event.action().category())
                        || event.player() == null || event.player().jersey() == null) {
                    continue;
                }
                Integer jersey = event.player().jersey();
                pointsByJersey.merge(jersey, 1, Integer::sum);
                if (event.player().name() != null) {
                    nameByJersey.putIfAbsent(jersey, event.player().name());
                }
            }
        }
        return pointsByJersey.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_SCORERS_LIMIT)
                .map(e -> new PublicTopScorerDto(
                        e.getKey(),
                        showPlayerNames ? nameByJersey.get(e.getKey()) : null,
                        e.getValue()))
                .toList();
    }
}
