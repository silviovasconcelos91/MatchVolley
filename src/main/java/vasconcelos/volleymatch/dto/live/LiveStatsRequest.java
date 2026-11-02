package vasconcelos.volleymatch.dto.live;

import java.time.Instant;
import java.util.List;

public record LiveStatsRequest(
        String matchId,
        Long teamId,
        String teamName,
        String title,
        String venue,
        Instant recordedAt,
        SetsWonDto setsWon,
        List<LiveSetDto> sets
) {}
