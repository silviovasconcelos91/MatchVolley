package vasconcelos.volleymatch.dto.live;

/**
 * @param team        team performing the action; for a timeout the team that called it, for a malus the penalized team
 * @param malusAmount points removed from {@code team} at the start of the set (1 or 2), malus events only, else null
 */
public record LiveEventDto(
        String id,
        Integer sequence,
        Long ts,
        String team,
        LivePlayerDto player,
        LiveActionDto action,
        LiveTrajectoryDto trajectory,
        String scoredFor,
        Integer malusAmount
) {}
