package vasconcelos.silvio.volleymatch.dto.match;

public record PlayerSetStatDto(
        Integer set,
        String position,
        Integer points,
        Integer attackPoints,
        Integer blockPoints,
        Integer acePoints,
        Integer attackErrors,
        Integer serviceErrors,
        Integer receptions
) {}
