package vasconcelos.volleymatch.service;

import vasconcelos.volleymatch.dto.live.LiveEventDto;

import java.util.List;

/**
 * Semantics of live stats events shared by validation ({@link LiveStatsPayloadValidator}) and score
 * restitution ({@link LiveSetReplay}, used by {@link LivePublicViewService} and {@link LiveStatsAnalysisService}).
 */
public final class LiveEventRules {

    public static final String MINE = "mine";
    public static final String OPP = "opp";
    public static final String TIMEOUT_KEY = "timeout";
    public static final String MALUS_KEY = "malus";

    private LiveEventRules() {
    }

    /**
     * A timeout is a neutral event without player: it never scores and is excluded from action stats.
     */
    public static boolean isTimeout(LiveEventDto event) {
        return event != null && event.action() != null && TIMEOUT_KEY.equals(event.action().key());
    }

    /**
     * A malus is a start-of-set handicap without player: {@code team} starts the set at {@code -malusAmount}.
     * It is not a point and is excluded from action stats.
     */
    public static boolean isMalus(LiveEventDto event) {
        return event != null && event.action() != null && MALUS_KEY.equals(event.action().key());
    }

    /**
     * Malus points deducted from {@code team} in a set (0 without malus). Both write paths (snapshot PUT and
     * final POST) are validated, so a malus precedes every scoring event: this is the team's start-of-set
     * score (negated), whatever the order of the list.
     */
    public static int malusOf(List<LiveEventDto> events, String team) {
        if (events == null) {
            return 0;
        }
        int total = 0;
        for (LiveEventDto event : events) {
            if (isMalus(event) && team.equals(event.team()) && event.malusAmount() != null) {
                total += event.malusAmount();
            }
        }
        return total;
    }
}
