package vasconcelos.silvio.volleymatch.dto.match;

public record StatsDto(
        Integer points,
        Integer attackPoints,
        Integer blockPoints,
        Integer acePoints,
        Integer attackErrors,
        Integer serviceErrors,
        Integer receptions
) {}
