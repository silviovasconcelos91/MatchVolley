package vasconcelos.silvio.volleymatch.dto.team;

import java.util.List;

public record TeamDto(
        Long id,
        String name,
        String city,
        String logoColor,
        List<TeamPlayerDto> players
) {}