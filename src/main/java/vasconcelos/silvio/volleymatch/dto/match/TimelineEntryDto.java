package vasconcelos.silvio.volleymatch.dto.match;

import java.time.Instant;

public record TimelineEntryDto(
        Integer myScore,
        Integer oppScore,
        Long playerId,
        String action,
        Instant occurredAt
) {
}
