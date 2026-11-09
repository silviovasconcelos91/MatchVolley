package vasconcelos.volleymatch.dto.livesession;

/**
 * @param scoredFor team that won the point
 * @param team      team that performed the action (for a fault, the faulty team)
 * @param player    player who performed the action
 */
public record PublicPointDto(
        int sequence,
        int setNumber,
        String scoredFor,
        ScoreDto scoreAfter,
        String team,
        PublicPlayerDto player,
        PublicActionDto action
) {}
