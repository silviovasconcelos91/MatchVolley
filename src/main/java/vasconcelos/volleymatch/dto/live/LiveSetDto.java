package vasconcelos.volleymatch.dto.live;

import java.util.List;

public record LiveSetDto(
        Integer setNumber,
        Integer scoreTeam,
        Integer scoreOpp,
        String wonBy,
        List<LiveEventDto> events
) {}
