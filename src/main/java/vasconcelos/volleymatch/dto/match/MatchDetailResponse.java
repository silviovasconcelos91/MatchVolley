package vasconcelos.volleymatch.dto.match;

import java.time.LocalDate;
import java.util.List;

public record MatchDetailResponse(
        String id,
        Long teamId,
        Long opponentId,
        String seasonId,
        Long competitionId,
        String title,
        Boolean home,
        LocalDate date,
        String result,
        Integer mySets,
        Integer oppSets,
        StatsDto teamMatchStats,
        List<SetStatDto> sets,
        List<PlayerStatDto> players
) {}