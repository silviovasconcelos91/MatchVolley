package vasconcelos.volleymatch.dto.live;

import java.util.List;

public record LiveActionStatsDto(
        List<LiveActionCountDto> points,
        List<LiveActionCountDto> faults,
        List<LiveActionCountDto> neutral
) {}
