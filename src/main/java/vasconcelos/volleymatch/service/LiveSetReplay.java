package vasconcelos.volleymatch.service;

import vasconcelos.volleymatch.dto.live.LiveEventDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static vasconcelos.volleymatch.service.LiveEventRules.MINE;
import static vasconcelos.volleymatch.service.LiveEventRules.OPP;

/**
 * Single replay of a set's score from its events, shared by the public overlay ({@link LivePublicViewService})
 * and the match analysis ({@link LiveStatsAnalysisService}).
 * <ul>
 *   <li>Order: by {@code sequence} (contract), events without sequence last; ties and nulls keep their stored
 *       relative order (stable sort). The events are sorted once, here.</li>
 *   <li>Start: {@code {-malusMine, -malusOpp}} ({@link LiveEventRules#malusOf}); a malus is never a point and does
 *       not end a running timeout.</li>
 *   <li>A timeout never scores; any other event ends it, so only a trailing timeout stays active.</li>
 *   <li>A point is an event whose {@code scoredFor} is {@code mine} or {@code opp}.</li>
 * </ul>
 * Plain value object built by a static factory: no reflection, nothing to declare for the native image.
 */
public final class LiveSetReplay {

    private static final Comparator<LiveEventDto> BY_SEQUENCE =
            Comparator.comparing(LiveEventDto::sequence, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * A scoring event and the cumulative score right after it.
     */
    public record Point(LiveEventDto event, int mine, int opp) {}

    /**
     * A timeout, the number of points played before it and the score when it was called.
     */
    public record Timeout(LiveEventDto event, int afterPoint, int mine, int opp) {}

    private final List<LiveEventDto> events;
    private final int startMine;
    private final int startOpp;
    private final List<Point> points;
    private final List<Timeout> timeouts;
    private final String activeTimeout;

    private LiveSetReplay(List<LiveEventDto> events, int startMine, int startOpp, List<Point> points,
                          List<Timeout> timeouts, String activeTimeout) {
        this.events = Collections.unmodifiableList(events);
        this.startMine = startMine;
        this.startOpp = startOpp;
        this.points = Collections.unmodifiableList(points);
        this.timeouts = Collections.unmodifiableList(timeouts);
        this.activeTimeout = activeTimeout;
    }

    public static LiveSetReplay of(List<LiveEventDto> storedEvents) {
        if (storedEvents == null || storedEvents.isEmpty()) {
            return new LiveSetReplay(List.of(), 0, 0, List.of(), List.of(), null);
        }
        List<LiveEventDto> ordered = new ArrayList<>(storedEvents);
        ordered.sort(BY_SEQUENCE);

        int startMine = -LiveEventRules.malusOf(ordered, MINE);
        int startOpp = -LiveEventRules.malusOf(ordered, OPP);
        int mine = startMine;
        int opp = startOpp;
        List<Point> points = new ArrayList<>();
        List<Timeout> timeouts = new ArrayList<>();
        String activeTimeout = null;

        for (LiveEventDto event : ordered) {
            if (LiveEventRules.isMalus(event)) {
                // Already applied to the start score; not a game action, so a running timeout stays active.
                continue;
            }
            activeTimeout = null;
            if (LiveEventRules.isTimeout(event)) {
                timeouts.add(new Timeout(event, points.size(), mine, opp));
                activeTimeout = event.team();
            } else if (MINE.equals(event.scoredFor())) {
                points.add(new Point(event, ++mine, opp));
            } else if (OPP.equals(event.scoredFor())) {
                points.add(new Point(event, mine, ++opp));
            }
        }
        return new LiveSetReplay(ordered, startMine, startOpp, points, timeouts, activeTimeout);
    }

    /**
     * Events in replay order (sequence, then stored order).
     */
    public List<LiveEventDto> events() {
        return events;
    }

    public int startMine() {
        return startMine;
    }

    public int startOpp() {
        return startOpp;
    }

    /**
     * My team's score at the end of the replay (start score when no point).
     */
    public int mine() {
        return points.isEmpty() ? startMine : points.getLast().mine();
    }

    /**
     * Opponent's score at the end of the replay (start score when no point).
     */
    public int opp() {
        return points.isEmpty() ? startOpp : points.getLast().opp();
    }

    /**
     * Scoring events in replay order, with the cumulative score after each one (malus have no entry).
     */
    public List<Point> points() {
        return points;
    }

    /**
     * Timeouts in replay order, positioned against {@link #points()}.
     */
    public List<Timeout> timeouts() {
        return timeouts;
    }

    /**
     * Team that won the last point, null before the first point.
     */
    public String lastScorer() {
        return points.isEmpty() ? null : points.getLast().event().scoredFor();
    }

    /**
     * Team whose timeout is in progress (last non-malus event is a timeout), else null.
     */
    public String activeTimeout() {
        return activeTimeout;
    }
}
