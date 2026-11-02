package vasconcelos.volleymatch.dto.live;

public record LivePlayerSetAnalysisDto(
        Integer setNumber,
        String poste,
        LiveScopeStatsDto stats
) {}
