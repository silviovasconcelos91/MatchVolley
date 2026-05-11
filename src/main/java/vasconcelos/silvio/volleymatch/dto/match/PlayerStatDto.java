package vasconcelos.silvio.volleymatch.dto.match;

import vasconcelos.silvio.volleymatch.model.match.VolleyPosition;

import java.util.List;

public record PlayerStatDto(
        Long playerId,
        Integer number,
        VolleyPosition role,
        StatsDto matchStats,
        List<PlayerSetStatDto> setStats
) {}
