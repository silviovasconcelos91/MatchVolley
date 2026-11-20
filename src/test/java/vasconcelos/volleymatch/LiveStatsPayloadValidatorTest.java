package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.live.LiveTrajectoryDto;
import vasconcelos.volleymatch.exception.BadRequestException;
import vasconcelos.volleymatch.service.LiveStatsPayloadValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.malus;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.myFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.neutral;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.numberedSets;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.sixSetsAfterTiedSet;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.timeout;

class LiveStatsPayloadValidatorTest {

    private final LiveStatsPayloadValidator validator = new LiveStatsPayloadValidator();

    @Test
    void should_accept_when_payload_follows_contract() {
        assertThatCode(() -> validator.validate(simpleSnapshot())).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_match_has_no_set_yet() {
        assertThatCode(() -> validator.validate(snapshot(List.of()))).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_payload_is_null() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_reject_when_set_number_out_of_range() {
        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(11, null, List.of())))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("setNumber");
    }

    @Test
    void should_accept_when_match_has_six_sets_after_a_tied_set() {
        assertThatCode(() -> validator.validate(snapshot(sixSetsAfterTiedSet()))).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_match_has_ten_sets() {
        assertThatCode(() -> validator.validate(snapshot(numberedSets(10)))).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_match_has_more_than_ten_sets() {
        assertThatThrownBy(() -> validator.validate(snapshot(numberedSets(11))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most 10 sets");
    }

    @Test
    void should_reject_when_set_number_is_duplicated() {
        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of()), set(1, null, List.of())))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void should_reject_when_wonBy_is_unknown() {
        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, "draw", List.of())))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("wonBy");
    }

    @Test
    void should_reject_when_scoredFor_inconsistent_with_category() {
        LiveEventDto fault = new LiveEventDto("e1", 1, 1L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("attack_fault", "Faute", "fault"), null, "mine", null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(fault))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("scoredFor");
    }

    @Test
    void should_reject_when_category_is_unknown() {
        LiveEventDto event = new LiveEventDto("e1", 1, 1L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("x", "X", "bonus"), null, null, null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(event))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("category");
    }

    @Test
    void should_reject_when_team_or_jersey_missing() {
        LiveEventDto noTeam = new LiveEventDto("e1", 1, 1L, null,
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("attack_pt", "Attaque", "point"), null, null, null);
        LiveEventDto noJersey = new LiveEventDto("e2", 1, 1L, "mine",
                new LivePlayerDto(3L, null, "Dupont", 4, null),
                new LiveActionDto("attack_pt", "Attaque", "point"), null, "mine", null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noTeam))))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noJersey))))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_reject_when_sequence_missing() {
        LiveEventDto event = attackPoint(1, 3L, 7, "Dupont");
        LiveEventDto noSequence = new LiveEventDto(event.id(), null, event.ts(), event.team(),
                event.player(), event.action(), event.trajectory(), event.scoredFor(), null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noSequence))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sequence");
    }

    @Test
    void should_accept_when_timeout_has_no_player() {
        LiveStatsRequest payload = snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "opp"),
                timeout(3, "mine")))));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_non_timeout_event_has_no_player() {
        LiveEventDto noPlayer = new LiveEventDto("e1", 1, 1L, "mine", null,
                new LiveActionDto("good_recv", "Bonne réception", "neutral"), null, null, null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noPlayer))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("player.jersey");
    }

    @Test
    void should_reject_when_timeout_category_is_not_neutral() {
        LiveEventDto pointTimeout = new LiveEventDto("e1", 1, 1L, "mine", null,
                new LiveActionDto("timeout", "Temps mort", "point"), null, "mine", null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(pointTimeout))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void should_reject_when_timeout_has_trajectory() {
        LiveEventDto timeoutWithZone = new LiveEventDto("e1", 1, 1L, "mine", null,
                new LiveActionDto("timeout", "Temps mort", "neutral"), new LiveTrajectoryDto(4, 1), null, null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(timeoutWithZone))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("trajectory");
    }

    // ── malus ─────────────────────────────────────────────────────────────────

    private static LiveStatsRequest oneSet(LiveEventDto... events) {
        return snapshot(List.of(set(1, null, List.of(events))));
    }

    private static LiveEventDto withMalusAmount(LiveEventDto event, Integer amount) {
        return new LiveEventDto(event.id(), event.sequence(), event.ts(), event.team(), event.player(),
                event.action(), event.trajectory(), event.scoredFor(), amount);
    }

    @Test
    void should_accept_when_malus_is_first_event_of_set() {
        LiveStatsRequest payload = oneSet(malus(1, "mine", 2), attackPoint(2, 3L, 7, "Dupont"));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_malus_amount_is_one() {
        assertThatCode(() -> validator.validate(oneSet(malus(1, "opp", 1)))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 3, -1})
    void should_reject_when_malus_amount_is_not_one_or_two(Integer amount) {
        assertThatThrownBy(() -> validator.validate(oneSet(malus(1, "mine", amount))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("malusAmount");
    }

    @Test
    void should_reject_when_non_malus_event_has_malus_amount() {
        List<LiveEventDto> nonMalus = List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                myFault(1, 3L, 7, "Dupont"),
                opponentFault(1, 9),
                neutral(1, 4L, 1, "Martin"),
                timeout(1, "mine"));

        for (LiveEventDto event : nonMalus) {
            assertThatThrownBy(() -> validator.validate(oneSet(withMalusAmount(event, 1))))
                    .as(event.action().key())
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("malusAmount");
        }
    }

    @Test
    void should_reject_when_malus_category_used_with_other_key() {
        LiveEventDto event = new LiveEventDto("e1", 1, 1L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("attack_pt", "Attaque", "malus"), null, null, null);

        assertThatThrownBy(() -> validator.validate(oneSet(event)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("action.category 'malus' requires action.key 'malus'");
    }

    @Test
    void should_reject_when_malus_key_used_with_other_category() {
        LiveEventDto event = new LiveEventDto("e1", 1, 1L, "mine", null,
                new LiveActionDto("malus", "Malus", "neutral"), null, null, 2);

        assertThatThrownBy(() -> validator.validate(oneSet(event)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("category 'malus'");
    }

    @Test
    void should_reject_when_malus_has_player_trajectory_or_scoredFor() {
        LiveEventDto base = malus(1, "mine", 2);
        LiveEventDto withPlayer = new LiveEventDto(base.id(), 1, base.ts(), "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null), base.action(), null, null, 2);
        LiveEventDto withTrajectory = new LiveEventDto(base.id(), 1, base.ts(), "mine",
                null, base.action(), new LiveTrajectoryDto(4, 1), null, 2);
        LiveEventDto withScoredFor = new LiveEventDto(base.id(), 1, base.ts(), "mine",
                null, base.action(), null, "opp", 2);

        assertThatThrownBy(() -> validator.validate(oneSet(withPlayer)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("player");
        assertThatThrownBy(() -> validator.validate(oneSet(withTrajectory)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("trajectory");
        assertThatThrownBy(() -> validator.validate(oneSet(withScoredFor)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("scoredFor");
    }

    @Test
    void should_reject_when_malus_sequence_is_after_a_scoring_event() {
        LiveStatsRequest payload = oneSet(opponentPoint(1, 12), malus(2, "mine", 2));

        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("before the first point");
    }

    @Test
    void should_reject_when_malus_is_listed_first_but_sequence_is_after_a_point() {
        LiveStatsRequest payload = oneSet(malus(5, "mine", 2), opponentPoint(1, 12));

        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("before the first point");
    }

    @Test
    void should_reject_when_malus_sequence_equals_first_point_sequence() {
        LiveStatsRequest payload = oneSet(opponentPoint(2, 12), malus(2, "mine", 2));

        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("before the first point");
    }

    @Test
    void should_accept_when_malus_has_max_sequence_in_set_without_point() {
        LiveStatsRequest payload = oneSet(timeout(1, "opp"), malus(Integer.MAX_VALUE, "mine", 2));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_malus_is_listed_after_a_point_but_sequence_is_before() {
        LiveStatsRequest payload = oneSet(opponentPoint(2, 12), malus(1, "mine", 2));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_malus_is_preceded_only_by_timeout_or_neutral_action() {
        LiveStatsRequest payload = oneSet(
                timeout(1, "opp"),
                neutral(2, 4L, 1, "Martin"),
                malus(3, "mine", 2),
                attackPoint(4, 3L, 7, "Dupont"));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_same_team_has_two_malus_in_a_set() {
        LiveStatsRequest payload = oneSet(malus(1, "mine", 1), malus(2, "mine", 1));

        assertThatThrownBy(() -> validator.validate(payload))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at most one malus");
    }

    @Test
    void should_accept_when_each_team_has_one_malus_in_a_set() {
        LiveStatsRequest payload = oneSet(malus(1, "mine", 2), malus(2, "opp", 1), opponentPoint(3, 12));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_same_team_has_malus_in_two_different_sets() {
        LiveStatsRequest payload = snapshot(List.of(
                set(1, "mine", List.of(malus(1, "mine", 2), attackPoint(2, 3L, 7, "Dupont"))),
                set(2, null, List.of(malus(1, "mine", 1)))));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_malus_of_two_points_in_deciding_set() {
        LiveStatsRequest payload = snapshot(List.of(set(5, null, List.of(malus(1, "mine", 2)))));

        assertThatCode(() -> validator.validate(payload)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_when_no_event_carries_malus_amount() {
        LiveStatsRequest legacy = snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "opp"),
                opponentPoint(3, 12)))));

        assertThatCode(() -> validator.validate(legacy)).doesNotThrowAnyException();
    }
}
