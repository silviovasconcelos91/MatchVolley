package vasconcelos.volleymatch.dto.live;

import vasconcelos.volleymatch.dto.match.StatsDto;

import java.util.List;

public record LivePlayerAnalysisDto(
        Long playerId,
        Integer jersey,
        String name,
        StatsDto matchStats,
        List<LivePlayerSetAnalysisDto> setStats
) {}
