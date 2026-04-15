package vasconcelos.silvio.volleymatch.dto.match;

import java.util.List;

public record MatchStatRequest(
        MatchDto match,
        List<SetStatDto> sets,
        List<PlayerStatDto> players,
        StatsDto teamMatchStats,
        MetaDto meta
) {}
