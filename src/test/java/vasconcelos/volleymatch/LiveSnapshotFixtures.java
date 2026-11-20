package vasconcelos.volleymatch;

import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveTrajectoryDto;
import vasconcelos.volleymatch.dto.live.SetsWonDto;
import vasconcelos.volleymatch.model.match.VolleyPosition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builders for live snapshot payloads used by live session tests.
 */
final class LiveSnapshotFixtures {

    private LiveSnapshotFixtures() {
    }

    static LiveEventDto attackPoint(int sequence, long playerId, int jersey, String name) {
        return new LiveEventDto("ev-" + sequence + "-" + jersey, sequence, 1_000L + sequence, "mine",
                new LivePlayerDto(playerId, jersey, name, 4, VolleyPosition.R4),
                new LiveActionDto("attack_pt", "Attaque", "point"),
                new LiveTrajectoryDto(4, 1), "mine", null);
    }

    static LiveEventDto opponentPoint(int sequence, int jersey) {
        return new LiveEventDto("ev-opp-" + sequence, sequence, 1_000L + sequence, "opp",
                new LivePlayerDto(null, jersey, "Adv #" + jersey, null, null),
                new LiveActionDto("attack_pt", "Attaque", "point"),
                null, "opp", null);
    }

    /**
     * Opponent fault: the opponent performs the action, my team takes the point.
     */
    static LiveEventDto opponentFault(int sequence, int jersey) {
        return new LiveEventDto("ev-oppf-" + sequence, sequence, 1_000L + sequence, "opp",
                new LivePlayerDto(null, jersey, "Adv #" + jersey, null, null),
                new LiveActionDto("serve_fault", "Faute de service", "fault"),
                null, "mine", null);
    }

    static LiveEventDto myFault(int sequence, long playerId, int jersey, String name) {
        return new LiveEventDto("ev-myf-" + sequence, sequence, 1_000L + sequence, "mine",
                new LivePlayerDto(playerId, jersey, name, 2, VolleyPosition.Central),
                new LiveActionDto("attack_fault", "Faute d'attaque", "fault"),
                null, "opp", null);
    }

    static LiveEventDto neutral(int sequence, long playerId, int jersey, String name) {
        return new LiveEventDto("ev-n-" + sequence, sequence, 1_000L + sequence, "mine",
                new LivePlayerDto(playerId, jersey, name, 6, VolleyPosition.Libero),
                new LiveActionDto("good_recv", "Bonne réception", "neutral"),
                null, null, null);
    }

    /**
     * Timeout called by {@code team}: no player, no trajectory, no point.
     */
    static LiveEventDto timeout(int sequence, String team) {
        return new LiveEventDto("ev-to-" + sequence, sequence, 1_000L + sequence, team,
                null,
                new LiveActionDto("timeout", "Temps mort", "neutral"),
                null, null, null);
    }

    /**
     * Malus of {@code amount} points against {@code team} (the penalized team): no player, no trajectory, no point.
     */
    static LiveEventDto malus(int sequence, String team, Integer amount) {
        return new LiveEventDto("ev-ma-" + sequence, sequence, 1_000L + sequence, team,
                null,
                new LiveActionDto("malus", "Malus", "malus"),
                null, null, amount);
    }

    static LiveSetDto set(int setNumber, String wonBy, List<LiveEventDto> events) {
        return new LiveSetDto(setNumber, 0, 0, wonBy, events);
    }

    /**
     * Time-limited tournament: set 3 ends tied (wonBy null, counts for nobody), so after 5 sets it is 2-2
     * and a 6th set is played (front: FORCE_END_SET then NEXT_SET, no set cap).
     */
    static List<LiveSetDto> sixSetsAfterTiedSet() {
        return List.of(
                set(1, "mine", List.of(attackPoint(1, 3L, 7, "Dupont"))),
                set(2, "opp", List.of(opponentPoint(1, 12))),
                set(3, null, List.of(attackPoint(1, 3L, 7, "Dupont"), opponentPoint(2, 12))),
                set(4, "mine", List.of(attackPoint(1, 3L, 7, "Dupont"))),
                set(5, "opp", List.of(opponentPoint(1, 12))),
                set(6, null, List.of(malus(1, "opp", 1), attackPoint(2, 3L, 7, "Dupont"))));
    }

    /** Sets numbered 1..count, each with one point of mine. */
    static List<LiveSetDto> numberedSets(int count) {
        List<LiveSetDto> sets = new ArrayList<>();
        for (int n = 1; n <= count; n++) {
            sets.add(set(n, null, List.of(attackPoint(1, 3L, 7, "Dupont"))));
        }
        return sets;
    }

    /**
     * Match shaped like the app's payload (~317 bytes per event once serialized): per set, alternating my points
     * (named player, trajectory), opponent points and neutral actions, with real-looking ids and epoch-ms timestamps.
     * Valid and persistable: passes {@code LiveStatsPayloadValidator}, all strings fit their columns.
     */
    static LiveStatsRequest realisticMatch(String matchId, int setCount, int eventsPerSet) {
        long ts = 1_749_131_100_000L;
        List<LiveSetDto> sets = new ArrayList<>();
        for (int setNumber = 1; setNumber <= setCount; setNumber++) {
            List<LiveEventDto> events = new ArrayList<>();
            for (int seq = 1; seq <= eventsPerSet; seq++) {
                ts += 7_000L;
                events.add(realisticEvent(seq, ts));
            }
            sets.add(set(setNumber, "mine", events));
        }
        return new LiveStatsRequest(matchId, 42L, "Les Lynx de Villeurbanne", "VSL vs Grenoble Volley Université",
                "home", Instant.parse("2026-06-05T14:45:00.000Z"), new SetsWonDto(3, 2), sets);
    }

    /** Valid, persistable match whose JSON exceeds 1 MiB (10 sets × 360 events ≈ 1.14 MB): only the size is wrong. */
    static LiveStatsRequest oversizedMatch(String matchId) {
        return realisticMatch(matchId, 10, 360);
    }

    private static LiveEventDto realisticEvent(int seq, long ts) {
        String id = "ev-" + ts + "-" + Long.toString(ts * 31 + seq, 36);
        return switch (seq % 3) {
            case 0 -> new LiveEventDto(id, seq, ts, "mine",
                    new LivePlayerDto(1_000L + seq % 12, 10 + seq % 12, "Jean-Baptiste Dupont-Moreau", 1 + seq % 6,
                            VolleyPosition.R4),
                    new LiveActionDto("attack_pt", "Attaque gagnante", "point"),
                    new LiveTrajectoryDto(1 + seq % 6, 1 + (seq + 2) % 6), "mine", null);
            case 1 -> new LiveEventDto(id, seq, ts, "opp",
                    new LivePlayerDto(null, 10 + seq % 12, "Adv #" + (10 + seq % 12), null, null),
                    new LiveActionDto("attack_pt", "Attaque gagnante", "point"),
                    new LiveTrajectoryDto(1 + seq % 6, 1 + (seq + 3) % 6), "opp", null);
            default -> new LiveEventDto(id, seq, ts, "mine",
                    new LivePlayerDto(2_000L + seq % 12, 1 + seq % 12, "Marie-Charlotte Lefebvre", 1 + seq % 6,
                            VolleyPosition.Libero),
                    new LiveActionDto("good_recv", "Bonne réception", "neutral"),
                    new LiveTrajectoryDto(1 + seq % 6, 1 + (seq + 1) % 6), null, null);
        };
    }

    static LiveStatsRequest snapshot(List<LiveSetDto> sets) {
        return new LiveStatsRequest("live-snap", 1L, "Les Lynx", "Les Lynx vs Grenoble", "home",
                Instant.parse("2026-11-05T19:00:00Z"), new SetsWonDto(0, 0), sets);
    }

    static LiveStatsRequest simpleSnapshot() {
        return snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                opponentPoint(2, 12),
                opponentFault(3, 9)))));
    }
}
