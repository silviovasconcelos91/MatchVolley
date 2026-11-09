package vasconcelos.volleymatch.dto.live;

import java.time.Instant;

/**
 * Timeout marker for the set progression graph.
 *
 * @param team       team that called the timeout ("mine" / "opp")
 * @param afterPoint number of points already played in the set when called: the marker sits right after
 *                   {@code timeline[afterPoint - 1]} (0 = before the first point)
 * @param myScore    my team's score when called
 * @param oppScore   opponent's score when called
 */
public record LiveTimeoutDto(
        String team,
        int afterPoint,
        int myScore,
        int oppScore,
        Instant occurredAt
) {}
