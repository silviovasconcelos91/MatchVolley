package vasconcelos.volleymatch.dto.player;

import vasconcelos.volleymatch.model.match.VolleyPosition;

import java.util.List;

public record PlayerDto(
        Long id,
        String name,
        List<VolleyPosition> roles,
        Integer numero,
        List<Long> teamIds
) {}