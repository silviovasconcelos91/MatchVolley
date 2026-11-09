package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.exception.BadRequestException;
import vasconcelos.volleymatch.service.LiveStatsPayloadValidator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;

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
        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(6, null, List.of())))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("setNumber");
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
                new LiveActionDto("attack_fault", "Faute", "fault"), null, "mine");

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(fault))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("scoredFor");
    }

    @Test
    void should_reject_when_category_is_unknown() {
        LiveEventDto event = new LiveEventDto("e1", 1, 1L, "mine",
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("x", "X", "bonus"), null, null);

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(event))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("category");
    }

    @Test
    void should_reject_when_team_or_jersey_missing() {
        LiveEventDto noTeam = new LiveEventDto("e1", 1, 1L, null,
                new LivePlayerDto(3L, 7, "Dupont", 4, null),
                new LiveActionDto("attack_pt", "Attaque", "point"), null, null);
        LiveEventDto noJersey = new LiveEventDto("e2", 1, 1L, "mine",
                new LivePlayerDto(3L, null, "Dupont", 4, null),
                new LiveActionDto("attack_pt", "Attaque", "point"), null, "mine");

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noTeam))))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noJersey))))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void should_reject_when_sequence_missing() {
        LiveEventDto event = attackPoint(1, 3L, 7, "Dupont");
        LiveEventDto noSequence = new LiveEventDto(event.id(), null, event.ts(), event.team(),
                event.player(), event.action(), event.trajectory(), event.scoredFor());

        assertThatThrownBy(() -> validator.validate(snapshot(List.of(set(1, null, List.of(noSequence))))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sequence");
    }
}
