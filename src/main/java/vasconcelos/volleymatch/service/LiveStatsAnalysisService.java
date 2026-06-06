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
import vasconcelos.volleymatch.dto.live.LiveSetAnalysisDto;
import vasconcelos.volleymatch.dto.match.TimelineEntryDto;
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
                    return new LiveSetAnalysisDto(
                            se.getSetNumber(), se.getScoreTeam(), se.getScoreOpp(), se.getWonBy(),
                            computeScopeStats(events),
                            buildTimeline(events)
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
                .filter(e -> "mine".equals(e.team()))
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

    public List<TimelineEntryDto> buildTimeline(List<LiveEventDto> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<TimelineEntryDto> timeline = new ArrayList<>();
        int myScore = 0, oppScore = 0;
        for (LiveEventDto e : events) {
            if (e.scoredFor() == null) continue;
            if ("mine".equals(e.scoredFor())) myScore++;
            else if ("opp".equals(e.scoredFor())) oppScore++;
            Long playerId = ("mine".equals(e.team()) && e.player() != null) ? e.player().id() : null;
            String action = e.action() != null ? e.action().key() : null;
            Instant occurredAt = e.ts() != null ? Instant.ofEpochMilli(e.ts()) : null;
            timeline.add(new TimelineEntryDto(myScore, oppScore, playerId, action, occurredAt));
        }
        return timeline;
    }

    public List<LivePlayerAnalysisDto> computePlayerAnalysis(List<MatchLiveSetEvent> sets) {
        if (sets == null || sets.isEmpty()) return List.of();

        Map<Long, LivePlayerDto> playerMap = new LinkedHashMap<>();
        for (MatchLiveSetEvent se : sets) {
            if (se.getEvents() == null) continue;
            for (LiveEventDto e : se.getEvents()) {
                if ("mine".equals(e.team()) && e.player() != null && e.player().id() != null) {
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
                        .filter(e -> "mine".equals(e.team())
                                && e.player() != null
                                && playerId.equals(e.player().id()))
                        .toList();
                allPlayerEvents.addAll(playerSetEvents);
                if (!playerSetEvents.isEmpty()) {
                    setStats.add(new LivePlayerSetAnalysisDto(se.getSetNumber(), computeScopeStats(playerSetEvents)));
                }
            }

            result.add(new LivePlayerAnalysisDto(
                    playerId, playerDto.jersey(), playerDto.name(),
                    computeScopeStats(allPlayerEvents), setStats
            ));
        }
        return result;
    }
}
