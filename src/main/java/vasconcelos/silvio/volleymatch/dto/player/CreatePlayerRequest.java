package vasconcelos.silvio.volleymatch.dto.player;

public record CreatePlayerRequest(
        String name,
        String role,
        Integer numero,
        Integer age,
        String taille
) {}
