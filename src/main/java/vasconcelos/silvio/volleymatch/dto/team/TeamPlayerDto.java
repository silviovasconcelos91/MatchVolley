package vasconcelos.silvio.volleymatch.dto.team;

import vasconcelos.silvio.volleymatch.model.match.VolleyPosition;

import java.util.List;

public record TeamPlayerDto(
        Long id,
        String name,
        List<VolleyPosition> roles,
        Integer numero,
        Integer age,
        String taille
) {}