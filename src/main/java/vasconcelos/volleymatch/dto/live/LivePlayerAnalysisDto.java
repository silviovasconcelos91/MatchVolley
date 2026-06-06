package vasconcelos.volleymatch.dto.live;

import java.util.List;

public record LivePlayerAnalysisDto(
        Long playerId,
        Integer jersey,
        String name,
        LiveScopeStatsDto matchStats,
        List<LivePlayerSetAnalysisDto> setStats
) {}
