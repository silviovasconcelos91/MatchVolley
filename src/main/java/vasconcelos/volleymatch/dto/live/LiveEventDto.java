package vasconcelos.volleymatch.dto.live;

public record LiveEventDto(
        String id,
        Integer sequence,
        Long ts,
        String team,
        LivePlayerDto player,
        LiveActionDto action,
        LiveTrajectoryDto trajectory,
        String scoredFor
) {}
