package vasconcelos.silvio.volleymatch.dto.match;

import java.util.List;

public record PlayerStatDto(
        Long playerId,
        Integer number,
        String role,
        StatsDto matchStats,
        List<PlayerSetStatDto> setStats
) {}
