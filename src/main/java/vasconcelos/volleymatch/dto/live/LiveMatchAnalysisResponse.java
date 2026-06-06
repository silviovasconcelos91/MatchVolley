package vasconcelos.volleymatch.dto.live;

import vasconcelos.volleymatch.dto.match.StatsDto;

import java.util.List;

public record LiveMatchAnalysisResponse(
        String matchId,
        StatsDto globalStats,
        List<LiveSetAnalysisDto> sets,
        List<LivePlayerAnalysisDto> players
) {}
