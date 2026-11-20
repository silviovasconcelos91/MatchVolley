package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import vasconcelos.volleymatch.service.LiveEventRules;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.malus;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.timeout;

class LiveEventRulesTest {

    @Test
    void should_identify_timeout_and_malus_by_action_key() {
        assertThat(LiveEventRules.isTimeout(timeout(1, "mine"))).isTrue();
        assertThat(LiveEventRules.isMalus(malus(1, "mine", 2))).isTrue();
        assertThat(LiveEventRules.isMalus(timeout(1, "mine"))).isFalse();
        assertThat(LiveEventRules.isTimeout(attackPoint(1, 3L, 7, "Dupont"))).isFalse();
        assertThat(LiveEventRules.isMalus(null)).isFalse();
    }

    @Test
    void should_sum_malus_of_requested_team_only() {
        var events = List.of(malus(1, "mine", 2), malus(2, "opp", 1), attackPoint(3, 3L, 7, "Dupont"));

        assertThat(LiveEventRules.malusOf(events, "mine")).isEqualTo(2);
        assertThat(LiveEventRules.malusOf(events, "opp")).isEqualTo(1);
    }

    @Test
    void should_return_zero_malus_when_no_events_or_no_malus() {
        assertThat(LiveEventRules.malusOf(null, "mine")).isZero();
        assertThat(LiveEventRules.malusOf(List.of(attackPoint(1, 3L, 7, "Dupont")), "mine")).isZero();
    }
}
