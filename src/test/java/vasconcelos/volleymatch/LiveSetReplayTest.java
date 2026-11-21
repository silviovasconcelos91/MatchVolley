package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.service.LiveSetReplay;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.malus;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.myFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.neutral;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.timeout;

class LiveSetReplayTest {

    @Test
    void should_return_zero_score_and_nothing_when_set_is_empty() {
        LiveSetReplay replay = LiveSetReplay.of(List.of());

        assertEmptyReplay(replay);
    }

    @Test
    void should_return_zero_score_and_nothing_when_events_are_null() {
        LiveSetReplay replay = LiveSetReplay.of(null);

        assertEmptyReplay(replay);
    }

    @Test
    void should_start_and_end_at_negative_score_without_point_when_malus_alone() {
        LiveSetReplay replay = LiveSetReplay.of(List.of(malus(1, "mine", 2), malus(2, "opp", 1)));

        assertThat(replay.startMine()).isEqualTo(-2);
        assertThat(replay.startOpp()).isEqualTo(-1);
        assertThat(replay.mine()).isEqualTo(-2);
        assertThat(replay.opp()).isEqualTo(-1);
        assertThat(replay.points()).isEmpty();
        assertThat(replay.timeouts()).isEmpty();
        assertThat(replay.lastScorer()).isNull();
    }

    @Test
    void should_count_points_from_malus_score_when_malus_then_points() {
        LiveSetReplay replay = LiveSetReplay.of(List.of(
                malus(1, "mine", 2),
                attackPoint(2, 3L, 7, "Dupont"),
                opponentPoint(3, 12),
                opponentFault(4, 9),
                myFault(5, 8L, 2, "Leroy")));

        assertThat(replay.points()).extracting(p -> p.event().sequence(), LiveSetReplay.Point::mine,
                        LiveSetReplay.Point::opp)
                .containsExactly(tuple(2, -1, 0), tuple(3, -1, 1), tuple(4, 0, 1), tuple(5, 0, 2));
        assertThat(replay.mine()).isZero();
        assertThat(replay.opp()).isEqualTo(2);
        assertThat(replay.lastScorer()).isEqualTo("opp");
    }

    @Test
    void should_keep_trailing_timeout_active_when_last_event_is_timeout() {
        LiveSetReplay replay = LiveSetReplay.of(List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "mine")));

        assertThat(replay.activeTimeout()).isEqualTo("mine");
        assertThat(replay.timeouts()).extracting(t -> t.event().team(), LiveSetReplay.Timeout::afterPoint,
                        LiveSetReplay.Timeout::mine, LiveSetReplay.Timeout::opp)
                .containsExactly(tuple("mine", 1, 1, 0));
    }

    @Test
    void should_keep_timeout_active_when_malus_follows_it_before_first_point() {
        // Valid per the contract: a malus must precede the first point, a timeout may precede the malus.
        LiveSetReplay replay = LiveSetReplay.of(List.of(
                timeout(1, "opp"),
                malus(2, "mine", 1)));

        assertThat(replay.activeTimeout()).isEqualTo("opp");
        assertThat(replay.timeouts()).extracting(t -> t.event().team(), LiveSetReplay.Timeout::afterPoint,
                        LiveSetReplay.Timeout::mine, LiveSetReplay.Timeout::opp)
                .containsExactly(tuple("opp", 0, -1, 0));
    }

    @Test
    void should_end_timeout_and_position_it_when_point_follows() {
        LiveSetReplay replay = LiveSetReplay.of(List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "mine"),
                timeout(3, "opp"),
                opponentPoint(4, 12)));

        assertThat(replay.activeTimeout()).isNull();
        assertThat(replay.timeouts()).extracting(t -> t.event().team(), LiveSetReplay.Timeout::afterPoint,
                        LiveSetReplay.Timeout::mine, LiveSetReplay.Timeout::opp)
                .containsExactly(tuple("mine", 1, 1, 0), tuple("opp", 1, 1, 0));
        assertThat(replay.points()).extracting(LiveSetReplay.Point::mine, LiveSetReplay.Point::opp)
                .containsExactly(tuple(1, 0), tuple(1, 1));
    }

    @Test
    void should_end_timeout_when_neutral_action_follows() {
        LiveSetReplay replay = LiveSetReplay.of(List.of(timeout(1, "opp"), neutral(2, 4L, 1, "Martin")));

        assertThat(replay.activeTimeout()).isNull();
        assertThat(replay.points()).isEmpty();
    }

    @Test
    void should_replay_by_sequence_when_events_are_stored_unordered() {
        LiveSetReplay replay = LiveSetReplay.of(List.of(
                opponentPoint(4, 12),
                timeout(3, "opp"),
                attackPoint(2, 3L, 7, "Dupont"),
                malus(1, "opp", 1)));

        assertThat(replay.events()).extracting(LiveEventDto::sequence).containsExactly(1, 2, 3, 4);
        assertThat(replay.points()).extracting(p -> p.event().sequence(), LiveSetReplay.Point::mine,
                        LiveSetReplay.Point::opp)
                .containsExactly(tuple(2, 1, -1), tuple(4, 1, 0));
        assertThat(replay.timeouts()).extracting(LiveSetReplay.Timeout::afterPoint, LiveSetReplay.Timeout::mine,
                        LiveSetReplay.Timeout::opp)
                .containsExactly(tuple(1, 1, -1));
        assertThat(replay.lastScorer()).isEqualTo("opp");
        assertThat(replay.activeTimeout()).isNull();
    }

    @Test
    void should_replay_null_sequences_last_in_stored_order_and_keep_ties_stable() {
        LiveEventDto legacyA = legacyPoint("legacy-a", "opp");
        LiveEventDto legacyB = legacyPoint("legacy-b", "mine");
        LiveEventDto tieFirst = opponentPoint(1, 12);
        LiveEventDto tieSecond = attackPoint(1, 3L, 7, "Dupont");

        LiveSetReplay replay = LiveSetReplay.of(List.of(legacyA, tieFirst, legacyB, tieSecond));

        assertThat(replay.events()).containsExactly(tieFirst, tieSecond, legacyA, legacyB);
        assertThat(replay.points()).extracting(LiveSetReplay.Point::mine, LiveSetReplay.Point::opp)
                .containsExactly(tuple(0, 1), tuple(1, 1), tuple(1, 2), tuple(2, 2));
    }

    @Test
    void should_not_count_unknown_scoredFor_as_a_point() {
        LiveEventDto unknown = new LiveEventDto("ev-x", 1, 1_000L, "mine", null,
                new LiveActionDto("attack_pt", "Attaque", "point"), null, "both", null);

        LiveSetReplay replay = LiveSetReplay.of(List.of(unknown, attackPoint(2, 3L, 7, "Dupont")));

        assertThat(replay.points()).extracting(LiveSetReplay.Point::mine, LiveSetReplay.Point::opp)
                .containsExactly(tuple(1, 0));
    }

    private static LiveEventDto legacyPoint(String id, String team) {
        return new LiveEventDto(id, null, 1_000L, team, null,
                new LiveActionDto("attack_pt", "Attaque", "point"), null, team, null);
    }

    private static void assertEmptyReplay(LiveSetReplay replay) {
        assertThat(replay.startMine()).isZero();
        assertThat(replay.startOpp()).isZero();
        assertThat(replay.mine()).isZero();
        assertThat(replay.opp()).isZero();
        assertThat(replay.events()).isEmpty();
        assertThat(replay.points()).isEmpty();
        assertThat(replay.timeouts()).isEmpty();
        assertThat(replay.lastScorer()).isNull();
        assertThat(replay.activeTimeout()).isNull();
    }
}
