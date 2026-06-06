package vasconcelos.volleymatch.dto.live;

import vasconcelos.volleymatch.dto.match.StatsDto;
import vasconcelos.volleymatch.dto.match.TimelineEntryDto;

import java.util.List;

public record LiveSetAnalysisDto(
        Integer setNumber,
        Integer myScore,
        Integer oppScore,
        String wonBy,
        StatsDto stats,
        List<TimelineEntryDto> timeline
) {}
