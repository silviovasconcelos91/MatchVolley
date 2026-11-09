package vasconcelos.volleymatch.dto.livesession;

import vasconcelos.volleymatch.dto.live.LiveStatsRequest;

/**
 * Full match state pushed by the app. Replaces the stored snapshot when {@code revision}
 * is strictly greater than the stored one.
 */
public record LiveSnapshotRequest(Long revision, LiveStatsRequest payload) {}
