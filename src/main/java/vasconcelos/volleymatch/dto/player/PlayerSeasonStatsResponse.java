package vasconcelos.volleymatch.dto.player;

import vasconcelos.volleymatch.dto.match.StatsDto;

import java.util.Map;

public record PlayerSeasonStatsResponse(
        Long playerId,
        int matchCount,
        int setCount,
        StatsDto totalStats,
        Map<String, PositionStatsDto> statsByPosition
) {}
