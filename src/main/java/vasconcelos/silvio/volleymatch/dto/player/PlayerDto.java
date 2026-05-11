package vasconcelos.silvio.volleymatch.dto.player;

import vasconcelos.silvio.volleymatch.model.match.VolleyPosition;

import java.util.List;

public record PlayerDto(
        Long id,
        String name,
        List<VolleyPosition> roles,
        Integer numero,
        Integer age,
        String taille,
        List<Long> teamIds
) {}