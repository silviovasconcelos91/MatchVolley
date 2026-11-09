package vasconcelos.volleymatch.dto.live;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param liveSessionId optional public live session to close (status ENDED + matchId) once the match is saved
 */
public record LiveStatsRequest(
        String matchId,
        Long teamId,
        String teamName,
        String title,
        String venue,
        Instant recordedAt,
        SetsWonDto setsWon,
        List<LiveSetDto> sets,
        UUID liveSessionId
) {
    public LiveStatsRequest(String matchId, Long teamId, String teamName, String title, String venue,
                            Instant recordedAt, SetsWonDto setsWon, List<LiveSetDto> sets) {
        this(matchId, teamId, teamName, title, venue, recordedAt, setsWon, sets, null);
    }
}
