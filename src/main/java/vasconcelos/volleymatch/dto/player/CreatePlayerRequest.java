package vasconcelos.volleymatch.dto.player;

import vasconcelos.volleymatch.model.match.VolleyPosition;

import java.util.List;

public record CreatePlayerRequest(
        String name,
        List<VolleyPosition> roles,
        Integer numero,
        Integer age,
        String taille,
        List<Long> teamIds
) {}
