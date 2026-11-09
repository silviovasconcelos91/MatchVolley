package vasconcelos.volleymatch.dto.livesession;

import vasconcelos.volleymatch.model.live.LiveSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record LiveSessionDto(
        UUID sessionId,
        String code,
        String publicUrl,
        String title,
        String venue,
        LiveSessionStatus status,
        boolean showPlayerNames,
        long revision,
        String matchId,
        Instant createdAt,
        Instant updatedAt,
        Instant endedAt,
        Instant expiresAt
) {}
