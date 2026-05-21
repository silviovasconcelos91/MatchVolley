package vasconcelos.volleymatch.dto.match;


import java.util.List;

public record SetStatDto(
        Integer set,
        Integer myScore,
        Integer oppScore,
        StatsDto teamStats,
        List<TimelineEntryDto> timeline
) {}
