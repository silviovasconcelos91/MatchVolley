package vasconcelos.volleymatch.dto.live;

import java.time.Instant;

public record TimelineEntryDto(
        Integer myScore,
        Integer oppScore,
        Long playerId,
        String action,
        Instant occurredAt
) {
}
