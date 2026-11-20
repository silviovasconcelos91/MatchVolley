package vasconcelos.volleymatch.dto.live;


import java.util.List;

/**
 * @param startScore score at the start of the set: malus deducted ({@code {0, 0}} without malus);
 *                   the set graph starts from it, before {@code timeline[0]}
 */
public record LiveSetAnalysisDto(
        Integer setNumber,
        Integer myScore,
        Integer oppScore,
        String wonBy,
        LiveScopeStatsDto stats,
        List<TimelineEntryDto> timeline,
        List<LiveTimeoutDto> timeouts,
        LiveScoreDto startScore
) {}
