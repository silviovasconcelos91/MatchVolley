package vasconcelos.volleymatch.dto.live;

import java.util.List;

public record LiveMatchAnalysisResponse(
        String matchId,
        LiveScopeStatsDto globalStats,
        List<LiveSetAnalysisDto> sets,
        List<LivePlayerAnalysisDto> players
) {}
