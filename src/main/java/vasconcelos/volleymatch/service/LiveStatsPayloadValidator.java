package vasconcelos.volleymatch.service;

import org.springframework.stereotype.Component;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.exception.BadRequestException;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Structural validation of a {@link LiveStatsRequest} according to docs/live-stats-api-contract.md.
 * Invalid {@code poste} values are already rejected by JSON deserialization.
 */
@Component
public class LiveStatsPayloadValidator {

    static final String MINE = "mine";
    static final String OPP = "opp";
    private static final Set<String> TEAMS = Set.of(MINE, OPP);
    private static final Set<String> VENUES = Set.of("home", "away");
    private static final int MAX_SETS = 5;
    static final String TIMEOUT_KEY = "timeout";
    private static final String NEUTRAL = "neutral";

    /**
     * A timeout is a neutral event without player: it never scores and is excluded from action stats.
     */
    static boolean isTimeout(LiveEventDto event) {
        return event != null && event.action() != null && TIMEOUT_KEY.equals(event.action().key());
    }

    public void validate(LiveStatsRequest payload) {
        if (payload == null) {
            throw new BadRequestException("payload is required");
        }
        if (payload.venue() != null && !VENUES.contains(payload.venue())) {
            throw new BadRequestException("venue must be 'home' or 'away'");
        }
        if (payload.sets() == null) {
            throw new BadRequestException("sets is required");
        }
        if (payload.sets().size() > MAX_SETS) {
            throw new BadRequestException("a match has at most " + MAX_SETS + " sets");
        }
        Set<Integer> setNumbers = new HashSet<>();
        for (LiveSetDto set : payload.sets()) {
            validateSet(set, setNumbers);
        }
    }

    private void validateSet(LiveSetDto set, Set<Integer> setNumbers) {
        if (set == null) {
            throw new BadRequestException("set must not be null");
        }
        Integer setNumber = set.setNumber();
        if (setNumber == null || setNumber < 1 || setNumber > MAX_SETS) {
            throw new BadRequestException("setNumber must be between 1 and " + MAX_SETS);
        }
        if (!setNumbers.add(setNumber)) {
            throw new BadRequestException("duplicate setNumber " + setNumber);
        }
        if (set.wonBy() != null && !TEAMS.contains(set.wonBy())) {
            throw new BadRequestException("set " + setNumber + ": wonBy must be 'mine', 'opp' or null");
        }
        if (set.events() == null) {
            throw new BadRequestException("set " + setNumber + ": events is required");
        }
        for (LiveEventDto event : set.events()) {
            validateEvent(setNumber, event);
        }
    }

    private void validateEvent(int setNumber, LiveEventDto event) {
        String where = "set " + setNumber + ": ";
        if (event == null) {
            throw new BadRequestException(where + "event must not be null");
        }
        if (event.sequence() == null || event.sequence() < 1) {
            throw new BadRequestException(where + "event sequence must be >= 1");
        }
        where = where + "event " + event.sequence() + ": ";
        if (event.team() == null || !TEAMS.contains(event.team())) {
            throw new BadRequestException(where + "team must be 'mine' or 'opp'");
        }
        LiveActionDto action = event.action();
        if (action == null || action.key() == null || action.category() == null) {
            throw new BadRequestException(where + "action.key and action.category are required");
        }
        if (isTimeout(event)) {
            validateTimeout(where, event);
            return;
        }
        if (event.player() == null || event.player().jersey() == null) {
            throw new BadRequestException(where + "player.jersey is required");
        }
        String expectedScoredFor = switch (action.category()) {
            case "point" -> event.team();
            case "fault" -> MINE.equals(event.team()) ? OPP : MINE;
            case "neutral" -> null;
            default -> throw new BadRequestException(where + "action.category must be 'point', 'fault' or 'neutral'");
        };
        if (!Objects.equals(expectedScoredFor, event.scoredFor())) {
            throw new BadRequestException(where + "scoredFor inconsistent with action category '" + action.category() + "'");
        }
    }

    private void validateTimeout(String where, LiveEventDto event) {
        if (!NEUTRAL.equals(event.action().category()) || event.scoredFor() != null) {
            throw new BadRequestException(where + "timeout must have category 'neutral' and scoredFor null");
        }
        if (event.trajectory() != null) {
            throw new BadRequestException(where + "timeout must have trajectory null");
        }
    }
}
