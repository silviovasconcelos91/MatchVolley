package vasconcelos.silvio.volleymatch.dto.match;

public record PlayerSetStatDto(
        Integer set,
        Integer points,
        Integer attackPoints,
        Integer blockPoints,
        Integer acePoints,
        Integer attackErrors,
        Integer serviceErrors,
        Integer receptions
) {}
