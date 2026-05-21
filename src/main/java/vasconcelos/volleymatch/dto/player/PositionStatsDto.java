package vasconcelos.volleymatch.dto.player;

import vasconcelos.volleymatch.dto.match.StatsDto;

public record PositionStatsDto(
        int matchCount,
        int setCount,
        StatsDto stats
) {}
