package vasconcelos.volleymatch.dto.live;

import vasconcelos.volleymatch.dto.match.StatsDto;

public record LivePlayerSetAnalysisDto(
        Integer setNumber,
        StatsDto stats
) {}
