package vasconcelos.volleymatch.model.live;

import java.util.UUID;

/**
 * Lightweight projection of a {@link LiveSession} (no snapshot) used to answer
 * conditional public polls (ETag / 304) without loading or parsing the JSON snapshot.
 */
public record LiveSessionHeader(UUID id, long revision, LiveSessionStatus status, boolean showPlayerNames) {}
