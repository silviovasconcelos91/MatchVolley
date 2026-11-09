package vasconcelos.volleymatch.dto.livesession;

/**
 * Only {@code showPlayerNames} is editable on a live session.
 */
public record UpdateLiveSessionRequest(Boolean showPlayerNames) {}
