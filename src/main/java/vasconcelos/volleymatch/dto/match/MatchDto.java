package vasconcelos.volleymatch.dto.match;

import java.time.LocalDate;

public record MatchDto(
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
        Integer oppSets
) {}
