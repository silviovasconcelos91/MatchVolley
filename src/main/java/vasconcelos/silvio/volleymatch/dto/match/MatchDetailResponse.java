package vasconcelos.silvio.volleymatch.dto.match;

import java.time.LocalDate;
import java.util.List;

public record MatchDetailResponse(
        String id,
        Long teamId,
        Long opponentId,
        String seasonId,
        Long competitionId,
        LocalDate date,
        String result,
        Integer mySets,
        Integer oppSets,
        StatsDto teamMatchStats,
        List<SetStatDto> sets,
        List<PlayerStatDto> players
) {}