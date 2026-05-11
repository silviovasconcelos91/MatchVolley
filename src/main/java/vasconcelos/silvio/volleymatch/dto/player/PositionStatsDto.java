package vasconcelos.silvio.volleymatch.dto.player;

import vasconcelos.silvio.volleymatch.dto.match.StatsDto;

public record PositionStatsDto(
        int matchCount,
        int setCount,
        StatsDto stats
) {}
