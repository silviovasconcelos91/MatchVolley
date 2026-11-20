package vasconcelos.volleymatch.dto.live;

/**
 * Score of a set at a given moment, in the analysis naming ({@code myScore} / {@code oppScore}).
 */
public record LiveScoreDto(
        int myScore,
        int oppScore
) {}
