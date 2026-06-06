package vasconcelos.volleymatch.dto.live;

public record LiveAttackZoneDto(
        Integer playerPosition,
        Integer from,
        Integer to,
        String result,
        Integer count
) {}
