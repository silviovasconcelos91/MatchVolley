package vasconcelos.volleymatch.dto.livesession;

public record CreateLiveSessionRequest(
        Long teamId,
        String title,
        String venue,
        Boolean showPlayerNames
) {}
