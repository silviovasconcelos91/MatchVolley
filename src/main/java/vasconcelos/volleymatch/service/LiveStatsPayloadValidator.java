package vasconcelos.volleymatch.service;

import org.springframework.stereotype.Component;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.exception.BadRequestException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static vasconcelos.volleymatch.service.LiveEventRules.MINE;
import static vasconcelos.volleymatch.service.LiveEventRules.OPP;
import static vasconcelos.volleymatch.service.LiveEventRules.isMalus;
import static vasconcelos.volleymatch.service.LiveEventRules.isTimeout;

/**
 * Structural validation of a {@link LiveStatsRequest} according to docs/live-stats-api-contract.md.
 * Invalid {@code poste} values are already rejected by JSON deserialization.
 */
@Component
public class LiveStatsPayloadValidator {

    private static final Set<String> TEAMS = Set.of(MINE, OPP);
    private static final Set<String> VENUES = Set.of("home", "away");
    /**
     * Sanity bound, not a volleyball rule: a set ended tied (time-limited tournament) counts for nobody, so a
     * match can go past 5 sets (front: no cap on NEXT_SET). 10 = 5 decisive sets + 5 tied sets.
     */
    private static final int MAX_SETS = 10;
    private static final String NEUTRAL = "neutral";
    private static final String MALUS_CATEGORY = "malus";
    private static final Set<Integer> MALUS_AMOUNTS = Set.of(1, 2);

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
        validateMalusPlacement(setNumber, set.events());
    }

    /**
     * At most one malus per team, each one strictly before the first scoring event of the set. Order is
     * evaluated on {@code sequence}, not on the position in the list. Events are already individually valid.
     */
    private void validateMalusPlacement(int setNumber, List<LiveEventDto> events) {
        Integer firstScoringSequence = null;
        for (LiveEventDto event : events) {
            if (event.scoredFor() != null
                    && (firstScoringSequence == null || event.sequence() < firstScoringSequence)) {
                firstScoringSequence = event.sequence();
            }
        }
        Set<String> penalizedTeams = new HashSet<>();
        for (LiveEventDto event : events) {
            if (!isMalus(event)) {
                continue;
            }
            String where = "set " + setNumber + ": event " + event.sequence() + ": ";
            if (!penalizedTeams.add(event.team())) {
                throw new BadRequestException(where + "at most one malus per team and per set");
            }
            if (firstScoringSequence != null && event.sequence() >= firstScoringSequence) {
                throw new BadRequestException(where + "malus must come before the first point of the set");
            }
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
        if (isMalus(event)) {
            validateMalus(where, event);
            return;
        }
        if (event.malusAmount() != null) {
            throw new BadRequestException(where + "malusAmount is only allowed on a malus event");
        }
        if (MALUS_CATEGORY.equals(action.category())) {
            throw new BadRequestException(where + "action.category 'malus' requires action.key 'malus'");
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

    private void validateMalus(String where, LiveEventDto event) {
        if (!MALUS_CATEGORY.equals(event.action().category())) {
            throw new BadRequestException(where + "action.key 'malus' requires action.category 'malus'");
        }
        if (event.player() != null) {
            throw new BadRequestException(where + "malus must have player null");
        }
        if (event.trajectory() != null) {
            throw new BadRequestException(where + "malus must have trajectory null");
        }
        if (event.scoredFor() != null) {
            throw new BadRequestException(where + "malus must have scoredFor null");
        }
        if (event.malusAmount() == null || !MALUS_AMOUNTS.contains(event.malusAmount())) {
            throw new BadRequestException(where + "malusAmount must be 1 or 2");
        }
    }
}
