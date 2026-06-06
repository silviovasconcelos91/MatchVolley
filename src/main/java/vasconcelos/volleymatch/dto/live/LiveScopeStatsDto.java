package vasconcelos.volleymatch.dto.live;

import java.util.List;

public record LiveScopeStatsDto(
        LiveActionStatsDto actions,
        List<LiveAceZoneDto> acesByZone,
        List<LiveAttackZoneDto> attacks
) {}
