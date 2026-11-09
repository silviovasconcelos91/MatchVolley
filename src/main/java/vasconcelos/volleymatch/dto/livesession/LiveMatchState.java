package vasconcelos.volleymatch.dto.livesession;

import java.util.List;

/**
 * Aggregates computed from a live snapshot, merged with session metadata into {@link PublicLiveViewDto}.
 */
public record LiveMatchState(
        ScoreDto setsWon,
        PublicCurrentSetDto currentSet,
        List<PublicSetDto> sets,
        List<PublicPointDto> lastPoints,
        List<PublicTopScorerDto> topScorers
) {}
