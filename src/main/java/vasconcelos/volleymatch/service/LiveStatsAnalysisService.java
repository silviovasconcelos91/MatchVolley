package vasconcelos.volleymatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vasconcelos.volleymatch.dto.live.LiveMatchAnalysisResponse;
import vasconcelos.volleymatch.dto.live.LivePlayerAnalysisDto;
import vasconcelos.volleymatch.dto.live.LivePlayerSetAnalysisDto;
import vasconcelos.volleymatch.dto.live.LiveSetAnalysisDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.match.StatsDto;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveStatsAnalysisService {

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

        StatsDto globalStats = computeStats(allEvents);

        List<LiveSetAnalysisDto> sets = session.getSets().stream()
                .map(se -> new LiveSetAnalysisDto(
                        se.getSetNumber(),
                        se.getScoreTeam(),
                        se.getScoreOpp(),
                        se.getWonBy(),
                        computeStats(se.getEvents() == null ? List.of() : se.getEvents()),
                        buildTimeline(se.getEvents() == null ? List.of() : se.getEvents())
                ))
                .toList();

        List<LivePlayerAnalysisDto> players = computePlayerAnalysis(session.getSets());

        return new LiveMatchAnalysisResponse(session.getMatch().getId(), globalStats, sets, players);
    }

    public StatsDto computeStats(List<LiveEventDto> events) {
        if (events == null || events.isEmpty()) {
            return new StatsDto(0, 0, 0, 0, 0, 0, 0, 0);
        }
        int points = 0, attackPoints = 0, blockPoints = 0, acePoints = 0;
        int attackErrors = 0, serviceErrors = 0, receptions = 0, faults = 0;

        for (LiveEventDto e : events) {
            if (e.action() == null) continue;
            String key = e.action().key();
            String team = e.team();
            String scoredFor = e.scoredFor();

            if ("mine".equals(scoredFor)) points++;
            if ("mine".equals(scoredFor) && "attack_pt".equals(key)) attackPoints++;
            if ("mine".equals(scoredFor) && "block_pt".equals(key))  blockPoints++;
            if ("mine".equals(scoredFor) && "ace".equals(key))       acePoints++;
            if ("mine".equals(team) && "attack_fault".equals(key))   attackErrors++;
            if ("mine".equals(team) && "serve_fault".equals(key))    serviceErrors++;
            if ("mine".equals(team) && "good_recv".equals(key))      receptions++;
            if ("mine".equals(team) && ("fault".equals(key) || "recv_fault".equals(key))) faults++;
        }
        return new StatsDto(points, attackPoints, blockPoints, acePoints,
                attackErrors, serviceErrors, receptions, faults);
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

        // Collect all "mine" players with non-null id, preserving first encounter order
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
                    setStats.add(new LivePlayerSetAnalysisDto(se.getSetNumber(), computeStats(playerSetEvents)));
                }
            }

            result.add(new LivePlayerAnalysisDto(
                    playerId,
                    playerDto.jersey(),
                    playerDto.name(),
                    computeStats(allPlayerEvents),
                    setStats
            ));
        }
        return result;
    }
}
