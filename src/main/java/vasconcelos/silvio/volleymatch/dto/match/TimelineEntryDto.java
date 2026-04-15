package vasconcelos.silvio.volleymatch.dto.match;


public record TimelineEntryDto(
        Integer myScore,
        Integer oppScore,
        Long playerId,
        String action
) {
}
