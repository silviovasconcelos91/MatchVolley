package vasconcelos.volleymatch.dto.live;


import java.util.List;

public record LiveSetAnalysisDto(
        Integer setNumber,
        Integer myScore,
        Integer oppScore,
        String wonBy,
        LiveScopeStatsDto stats,
        List<TimelineEntryDto> timeline,
        List<LiveTimeoutDto> timeouts
) {}
