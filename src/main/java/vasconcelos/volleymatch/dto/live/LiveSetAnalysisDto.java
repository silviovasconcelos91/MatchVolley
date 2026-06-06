package vasconcelos.volleymatch.dto.live;

import vasconcelos.volleymatch.dto.match.TimelineEntryDto;

import java.util.List;

public record LiveSetAnalysisDto(
        Integer setNumber,
        Integer myScore,
        Integer oppScore,
        String wonBy,
        LiveScopeStatsDto stats,
        List<TimelineEntryDto> timeline
) {}
