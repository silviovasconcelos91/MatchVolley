package vasconcelos.silvio.volleymatch.dto.team;

public record TeamPlayerDto(
        Long id,
        String name,
        String role,
        Integer numero,
        Integer age,
        String taille
) {}