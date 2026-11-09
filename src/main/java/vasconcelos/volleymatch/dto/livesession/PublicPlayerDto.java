package vasconcelos.volleymatch.dto.livesession;

/**
 * Public player view: jersey only, name is null unless the session shows names (never for opponents).
 */
public record PublicPlayerDto(Integer jersey, String name) {}
