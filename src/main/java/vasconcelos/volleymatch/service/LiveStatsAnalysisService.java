package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.live.LiveActionCountDto;
import vasconcelos.volleymatch.dto.live.LiveActionStatsDto;
import vasconcelos.volleymatch.dto.live.LiveAceZoneDto;
import vasconcelos.volleymatch.dto.live.LiveAttackZoneDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveMatchAnalysisResponse;
import vasconcelos.volleymatch.dto.live.LivePlayerAnalysisDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LivePlayerSetAnalysisDto;
import vasconcelos.volleymatch.dto.live.LiveScopeStatsDto;
import vasconcelos.volleymatch.dto.live.LiveScoreDto;
import vasconcelos.volleymatch.dto.live.LiveSetAnalysisDto;
import vasconcelos.volleymatch.dto.live.LiveTimeoutDto;
import vasconcelos.volleymatch.dto.live.TimelineEntryDto;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static vasconcelos.volleymatch.service.LiveEventRules.MINE;
import static vasconcelos.volleymatch.service.LiveEventRules.OPP;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStatsAnalysisService {

    private static final Set<String> ATTACK_KEYS = Set.of("attack_pt", "attack_no_pt", "attack_fault");

    private final MatchLiveSessionRepository liveSessionRepository;
    private final AuthService authService;

    public LiveMatchAnalysisResponse getMatchAnalysis(String matchId) {
        var user = authService.getCurrentUser();
        MatchLiveSession session = liveSessionRepository
                .findByMatch_IdAndMatch_User(matchId, user)
                .orElseThrow(() -> new NoSuchElementException("Match not found: " + matchId));

        List<LiveEventDto> allEvents = session.getSets().stream()
                .flatMap(s -> s.getEvents() == null ? java.util.stream.Stream.empty() : s.getEvents().stream())
                .toList();

        List<LiveSetAnalysisDto> sets = session.getSets().stream()
                .map(se -> {
                    List<LiveEventDto> events = se.getEvents() == null ? List.of() : se.getEvents();
                    // One replay (one sort) per set feeds timeline, timeouts and start score.
                    LiveSetReplay replay = LiveSetReplay.of(events);
                    return new LiveSetAnalysisDto(
                            se.getSet(), se.getScoreTeam(), se.getScoreOpp(), se.getWonBy(),
                            computeScopeStats(events),
                            timelineOf(replay),
                            timeoutsOf(replay),
                            new LiveScoreDto(replay.startMine(), replay.startOpp())
                    );
                })
                .toList();

        return new LiveMatchAnalysisResponse(
                session.getMatch().getId(),
                computeScopeStats(allEvents),
                sets,
                computePlayerAnalysis(session.getSets())
        );
    }

    public LiveScopeStatsDto computeScopeStats(List<LiveEventDto> events) {
        List<LiveEventDto> mine = events.stream()
                .filter(e -> MINE.equals(e.team())
                        && !LiveEventRules.isTimeout(e)
                        && !LiveEventRules.isMalus(e))
                .toList();
        return new LiveScopeStatsDto(
                computeActions(mine),
                computeAcesByZone(mine),
                computeAttacks(mine)
        );
    }

    public LiveActionStatsDto computeActions(List<LiveEventDto> mineEvents) {
        // First pass: count occurrences of each key and track label + category per key
        Map<String, Integer> countByKey = new LinkedHashMap<>();
        Map<String, String> labelByKey = new LinkedHashMap<>();
        Map<String, String> categoryByKey = new LinkedHashMap<>();

        for (LiveEventDto e : mineEvents) {
            if (e.action() == null) continue;
            String key = e.action().key();
            countByKey.merge(key, 1, Integer::sum);
            labelByKey.putIfAbsent(key, e.action().label());
            categoryByKey.putIfAbsent(key, e.action().category());
        }

        List<LiveActionCountDto> points  = new ArrayList<>();
        List<LiveActionCountDto> faults  = new ArrayList<>();
        List<LiveActionCountDto> neutral = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : countByKey.entrySet()) {
            String key = entry.getKey();
            String category = categoryByKey.getOrDefault(key, "neutral");
            LiveActionCountDto dto = new LiveActionCountDto(key, labelByKey.get(key), entry.getValue());
            switch (category) {
                case "point" -> points.add(dto);
                case "fault" -> faults.add(dto);
                default      -> neutral.add(dto);
            }
        }
        return new LiveActionStatsDto(points, faults, neutral);
    }

    public List<LiveAceZoneDto> computeAcesByZone(List<LiveEventDto> mineEvents) {
        Map<Integer, Integer> countByZone = new LinkedHashMap<>();
        for (LiveEventDto e : mineEvents) {
            if (e.action() == null || !"ace".equals(e.action().key())) continue;
            if (e.trajectory() == null || e.trajectory().to() == null) continue;
            countByZone.merge(e.trajectory().to(), 1, Integer::sum);
        }
        return countByZone.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(en -> new LiveAceZoneDto(en.getKey(), en.getValue()))
                .toList();
    }

    public List<LiveAttackZoneDto> computeAttacks(List<LiveEventDto> mineEvents) {
        record AttackKey(Integer playerPosition, Integer from, Integer to, String result) {}
        Map<AttackKey, Integer> counts = new LinkedHashMap<>();

        for (LiveEventDto e : mineEvents) {
            if (e.action() == null || !ATTACK_KEYS.contains(e.action().key())) continue;
            Integer playerPos = e.player() != null ? e.player().position() : null;
            Integer from = e.trajectory() != null ? e.trajectory().from() : null;
            Integer to   = e.trajectory() != null ? e.trajectory().to()   : null;
            counts.merge(new AttackKey(playerPos, from, to, e.action().key()), 1, Integer::sum);
        }

        return counts.entrySet().stream()
                .map(en -> new LiveAttackZoneDto(
                        en.getKey().playerPosition(),
                        en.getKey().from(),
                        en.getKey().to(),
                        en.getKey().result(),
                        en.getValue()
                ))
                .toList();
    }

    /**
     * One entry per scoring event, in {@code sequence} order, cumulative score starting from the replay's start
     * score ({@link LiveSetReplay#startMine()}, {@link LiveSetReplay#startOpp()}: malus deducted; malus have no
     * entry). Single-set entry point kept for unit tests: production goes through {@link #getMatchAnalysis}.
     */
    public List<TimelineEntryDto> buildTimeline(List<LiveEventDto> events) {
        return timelineOf(LiveSetReplay.of(events));
    }

    /**
     * Timeout markers positioned against {@link #buildTimeline}, in {@code sequence} order: {@code afterPoint} is
     * the number of scoring events before the timeout (not the sum of the scores, which malus make lower); the
     * scores start from the replay's start score ({@link LiveSetReplay}). Single-set entry point kept for unit
     * tests: production goes through {@link #getMatchAnalysis}.
     */
    public List<LiveTimeoutDto> buildTimeouts(List<LiveEventDto> events) {
        return timeoutsOf(LiveSetReplay.of(events));
    }

    private static List<TimelineEntryDto> timelineOf(LiveSetReplay replay) {
        List<TimelineEntryDto> timeline = new ArrayList<>(replay.points().size());
        for (LiveSetReplay.Point point : replay.points()) {
            LiveEventDto e = point.event();
            Long playerId = (MINE.equals(e.team()) && e.player() != null) ? e.player().id() : null;
            String action = e.action() != null ? e.action().key() : null;
            timeline.add(new TimelineEntryDto(point.mine(), point.opp(), playerId, action, instantOf(e)));
        }
        return timeline;
    }

    private static List<LiveTimeoutDto> timeoutsOf(LiveSetReplay replay) {
        List<LiveTimeoutDto> timeouts = new ArrayList<>(replay.timeouts().size());
        for (LiveSetReplay.Timeout timeout : replay.timeouts()) {
            LiveEventDto e = timeout.event();
            timeouts.add(new LiveTimeoutDto(e.team(), timeout.afterPoint(), timeout.mine(), timeout.opp(),
                    instantOf(e)));
        }
        return timeouts;
    }

    private static Instant instantOf(LiveEventDto event) {
        return event.ts() != null ? Instant.ofEpochMilli(event.ts()) : null;
    }

    public List<LivePlayerAnalysisDto> computePlayerAnalysis(List<MatchLiveSetEvent> sets) {
        if (sets == null || sets.isEmpty()) return List.of();

        Map<Long, LivePlayerDto> playerMap = new LinkedHashMap<>();
        for (MatchLiveSetEvent se : sets) {
            if (se.getEvents() == null) continue;
            for (LiveEventDto e : se.getEvents()) {
                if (MINE.equals(e.team()) && e.player() != null && e.player().id() != null) {
                    playerMap.putIfAbsent(e.player().id(), e.player());
                }
            }
        }

        List<LivePlayerAnalysisDto> result = new ArrayList<>();
        for (Map.Entry<Long, LivePlayerDto> entry : playerMap.entrySet()) {
            Long playerId = entry.getKey();
            LivePlayerDto playerDto = entry.getValue();
            List<LivePlayerSetAnalysisDto> setStats = new ArrayList<>();
            List<LiveEventDto> allPlayerEvents = new ArrayList<>();

            for (MatchLiveSetEvent se : sets) {
                if (se.getEvents() == null) continue;
                List<LiveEventDto> playerSetEvents = se.getEvents().stream()
                        .filter(e -> MINE.equals(e.team())
                                && e.player() != null
                                && playerId.equals(e.player().id()))
                        .toList();
                allPlayerEvents.addAll(playerSetEvents);
                if (!playerSetEvents.isEmpty()) {
                    String poste = posteOf(playerSetEvents);
                    setStats.add(new LivePlayerSetAnalysisDto(se.getSet(), poste, computeScopeStats(playerSetEvents)));
                }
            }

            result.add(new LivePlayerAnalysisDto(
                    playerId, playerDto.jersey(), playerDto.name(),
                    computeScopeStats(allPlayerEvents), setStats
            ));
        }
        return result;
    }

    /** Poste of a player within a set: first non-null poste found in their events, or null. */
    public static String posteOf(List<LiveEventDto> playerSetEvents) {
        return playerSetEvents.stream()
                .map(LiveEventDto::player)
                .filter(p -> p != null && p.poste() != null)
                .map(p -> p.poste().name())
                .findFirst()
                .orElse(null);
    }
}
