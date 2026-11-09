package vasconcelos.volleymatch.dto.livesession;

import vasconcelos.volleymatch.model.live.LiveSessionStatus;

import java.time.Instant;
import java.util.List;

/**
 * Public, unauthenticated view of a live session. Never exposes session id, owner, team id or player ids.
 */
public record PublicLiveViewDto(
        String code,
        String title,
        String teamName,
        String venue,
        LiveSessionStatus status,
        Instant updatedAt,
        ScoreDto setsWon,
        PublicCurrentSetDto currentSet,
        List<PublicSetDto> sets,
        List<PublicPointDto> lastPoints,
        List<PublicTopScorerDto> topScorers
) {}
