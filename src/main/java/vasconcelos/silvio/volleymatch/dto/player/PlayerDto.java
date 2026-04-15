package vasconcelos.silvio.volleymatch.dto.player;

import java.util.List;

public record PlayerDto(
        Long id,
        String name,
        String role,
        Integer numero,
        Integer age,
        String taille,
        List<Long> teamIds
) {}