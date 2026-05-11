package vasconcelos.silvio.volleymatch.dto.player;

import vasconcelos.silvio.volleymatch.dto.match.StatsDto;

import java.util.Map;

public record PlayerSeasonStatsResponse(
        Long playerId,
        int matchCount,
        int setCount,
        StatsDto totalStats,
        Map<String, PositionStatsDto> statsByPosition
) {}
