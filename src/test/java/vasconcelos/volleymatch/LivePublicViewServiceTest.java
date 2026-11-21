package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.livesession.LiveMatchState;
import vasconcelos.volleymatch.dto.livesession.PublicPlayerDto;
import vasconcelos.volleymatch.dto.livesession.PublicPointDto;
import vasconcelos.volleymatch.dto.livesession.ScoreDto;
import vasconcelos.volleymatch.mapper.LiveSessionMapper;
import vasconcelos.volleymatch.model.live.LiveSessionHeader;
import vasconcelos.volleymatch.model.live.LiveSessionStatus;
import vasconcelos.volleymatch.repository.LiveSessionRepository;
import vasconcelos.volleymatch.service.LivePublicViewService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.malus;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.myFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.neutral;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.timeout;

class LivePublicViewServiceTest {

    private final LivePublicViewService service = new LivePublicViewService(
            mock(LiveSessionRepository.class), Mappers.getMapper(LiveSessionMapper.class), Clock.systemUTC());

    private LiveMatchState compute(LiveStatsRequest snapshot, boolean showNames) {
        return service.computeState(snapshot, showNames);
    }

    @Test
    void should_return_empty_state_when_no_snapshot_yet() {
        LiveMatchState state = compute(null, true);

        assertThat(state.setsWon()).isEqualTo(new ScoreDto(0, 0));
        assertThat(state.currentSet()).isNull();
        assertThat(state.sets()).isEmpty();
        assertThat(state.lastPoints()).isEmpty();
        assertThat(state.topScorers()).isEmpty();
    }

    @Test
    void should_build_trajectory_from_zero_ignoring_neutral_events() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                neutral(2, 4L, 1, "Martin"),
                opponentPoint(3, 12),
                opponentFault(4, 9)))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().trajectory()).containsExactly(
                new ScoreDto(0, 0), new ScoreDto(1, 0), new ScoreDto(1, 1), new ScoreDto(2, 1));
        assertThat(state.currentSet().score()).isEqualTo(new ScoreDto(2, 1));
        assertThat(state.lastPoints()).hasSize(3);
    }

    @Test
    void should_count_timeouts_per_team_in_current_set_only() {
        LiveStatsRequest snap = snapshot(List.of(
                set(1, "mine", List.of(timeout(1, "mine"), attackPoint(2, 3L, 7, "Dupont"))),
                set(2, null, List.of(
                        timeout(1, "opp"),
                        attackPoint(2, 3L, 7, "Dupont"),
                        timeout(3, "opp"),
                        opponentPoint(4, 12)))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().timeouts()).isEqualTo(new ScoreDto(0, 2));
        assertThat(state.currentSet().activeTimeout()).isNull();
    }

    @Test
    void should_set_active_timeout_when_last_event_of_current_set_is_timeout() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "mine")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().activeTimeout()).isEqualTo("mine");
        assertThat(state.currentSet().timeouts()).isEqualTo(new ScoreDto(1, 0));
    }

    @Test
    void should_clear_active_timeout_when_any_event_follows_timeout() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                timeout(1, "opp"),
                neutral(2, 4L, 1, "Martin")))));

        assertThat(compute(snap, true).currentSet().activeTimeout()).isNull();
    }

    @Test
    void should_ignore_timeouts_in_score_points_and_top_scorers() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                timeout(2, "mine"),
                timeout(3, "opp")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().score()).isEqualTo(new ScoreDto(1, 0));
        assertThat(state.currentSet().trajectory()).containsExactly(new ScoreDto(0, 0), new ScoreDto(1, 0));
        assertThat(state.lastPoints()).hasSize(1);
        assertThat(state.topScorers()).hasSize(1);
    }

    // ── malus ─────────────────────────────────────────────────────────────────

    @Test
    void should_deduct_malus_from_set_score_when_malus_then_points() {
        List<LiveEventDto> events = new ArrayList<>();
        events.add(malus(1, "mine", 2));
        int seq = 2;
        for (int i = 0; i < 27; i++) {
            events.add(attackPoint(seq++, 3L, 7, "Dupont"));
        }
        for (int i = 0; i < 15; i++) {
            events.add(opponentPoint(seq++, 12));
        }

        LiveMatchState state = compute(snapshot(List.of(set(1, null, events))), true);

        assertThat(state.currentSet().score()).isEqualTo(new ScoreDto(25, 15));
        assertThat(state.sets()).extracting(s -> s.score()).containsExactly(new ScoreDto(25, 15));
    }

    @Test
    void should_start_trajectory_at_negative_score_when_malus_alone() {
        LiveMatchState state = compute(snapshot(List.of(set(1, null, List.of(malus(1, "mine", 2))))), true);

        assertThat(state.currentSet().score()).isEqualTo(new ScoreDto(-2, 0));
        assertThat(state.currentSet().trajectory()).containsExactly(new ScoreDto(-2, 0));
        assertThat(state.currentSet().serving()).isNull();
    }

    @Test
    void should_ignore_malus_in_serving_and_last_points_when_point_follows() {
        LiveMatchState state = compute(snapshot(List.of(set(1, null, List.of(
                malus(1, "mine", 2),
                opponentPoint(2, 12))))), true);

        assertThat(state.currentSet().trajectory()).containsExactly(new ScoreDto(-2, 0), new ScoreDto(-2, 1));
        assertThat(state.currentSet().serving()).isEqualTo("opp");
        assertThat(state.lastPoints()).extracting(PublicPointDto::sequence, PublicPointDto::scoreAfter)
                .containsExactly(tuple(2, new ScoreDto(-2, 1)));
    }

    @Test
    void should_start_trajectory_with_both_malus_when_events_are_unordered() {
        LiveMatchState state = compute(snapshot(List.of(set(1, null, List.of(
                attackPoint(3, 3L, 7, "Dupont"),
                malus(2, "opp", 1),
                malus(1, "mine", 2))))), true);

        assertThat(state.currentSet().trajectory()).containsExactly(new ScoreDto(-2, -1), new ScoreDto(-1, -1));
    }

    @Test
    void should_exclude_malus_from_last_points_top_scorers_and_timeouts() {
        LiveMatchState state = compute(snapshot(List.of(set(1, null, List.of(
                malus(1, "mine", 1),
                attackPoint(2, 3L, 7, "Dupont"))))), true);

        assertThat(state.lastPoints()).extracting(PublicPointDto::sequence).containsExactly(2);
        assertThat(state.topScorers()).hasSize(1);
        assertThat(state.topScorers().get(0).points()).isEqualTo(1);
        assertThat(state.currentSet().timeouts()).isEqualTo(new ScoreDto(0, 0));
    }

    @Test
    void should_keep_active_timeout_when_malus_follows_timeout() {
        LiveMatchState state = compute(snapshot(List.of(set(1, null, List.of(
                timeout(1, "opp"),
                malus(2, "mine", 2))))), true);

        assertThat(state.currentSet().activeTimeout()).isEqualTo("opp");
        assertThat(state.currentSet().timeouts()).isEqualTo(new ScoreDto(0, 1));
    }

    @Test
    void should_restore_score_when_next_snapshot_drops_the_malus() {
        LiveMatchState withMalus = compute(snapshot(List.of(set(1, null, List.of(
                malus(1, "mine", 2),
                attackPoint(2, 3L, 7, "Dupont"))))), true);
        LiveMatchState undone = compute(snapshot(List.of(set(1, null, List.of(
                attackPoint(2, 3L, 7, "Dupont"))))), true);

        assertThat(withMalus.currentSet().score()).isEqualTo(new ScoreDto(-1, 0));
        assertThat(undone.currentSet().score()).isEqualTo(new ScoreDto(1, 0));
        assertThat(undone.currentSet().trajectory()).containsExactly(new ScoreDto(0, 0), new ScoreDto(1, 0));
    }

    @Test
    void should_show_sixth_set_as_current_when_a_set_ended_tied() {
        LiveMatchState state = compute(snapshot(LiveSnapshotFixtures.sixSetsAfterTiedSet()), true);

        assertThat(state.sets()).extracting(s -> s.setNumber()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(state.sets().get(2).wonBy()).isNull();
        assertThat(state.sets().get(2).score()).isEqualTo(new ScoreDto(1, 1));
        assertThat(state.setsWon()).isEqualTo(new ScoreDto(2, 2));
        assertThat(state.currentSet().setNumber()).isEqualTo(6);
        assertThat(state.currentSet().score()).isEqualTo(new ScoreDto(1, -1));
    }

    @Test
    void should_order_events_by_sequence_when_payload_is_unordered() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                opponentPoint(2, 12),
                attackPoint(1, 3L, 7, "Dupont")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().trajectory()).containsExactly(
                new ScoreDto(0, 0), new ScoreDto(1, 0), new ScoreDto(1, 1));
    }

    // ── characterization of the replay order (MV-002) ────────────────────────

    @Test
    void should_replay_null_sequence_events_last_in_stored_order_when_legacy_payload() {
        LiveEventDto legacyOpp = new LiveEventDto("legacy-opp", null, 1_000L, "opp",
                null, new LiveActionDto("attack_pt", "Attaque", "point"),
                null, "opp", null);
        LiveEventDto legacyMine = new LiveEventDto("legacy-mine", null, 1_001L, "mine",
                null, new LiveActionDto("attack_pt", "Attaque", "point"),
                null, "mine", null);
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                legacyOpp,
                attackPoint(2, 3L, 7, "Dupont"),
                legacyMine,
                attackPoint(1, 3L, 7, "Dupont")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().trajectory()).containsExactly(
                new ScoreDto(0, 0), new ScoreDto(1, 0), new ScoreDto(2, 0), new ScoreDto(2, 1), new ScoreDto(3, 1));
        assertThat(state.currentSet().score()).isEqualTo(new ScoreDto(3, 1));
        assertThat(state.currentSet().serving()).isEqualTo("mine");
        // PublicPointDto.sequence is a non-null number in the contract: a point without sequence is not listed.
        assertThat(state.lastPoints()).extracting(PublicPointDto::sequence).containsExactly(2, 1);
    }

    @Test
    void should_keep_stored_relative_order_when_two_events_share_a_sequence() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                opponentPoint(1, 12),
                attackPoint(1, 3L, 7, "Dupont")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().trajectory()).containsExactly(
                new ScoreDto(0, 0), new ScoreDto(0, 1), new ScoreDto(1, 1));
        assertThat(state.currentSet().serving()).isEqualTo("mine");
    }

    @Test
    void should_report_last_points_and_active_timeout_in_sequence_order_when_payload_is_unordered() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                timeout(4, "opp"),
                opponentPoint(3, 12),
                attackPoint(1, 3L, 7, "Dupont"),
                opponentFault(2, 9)))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.lastPoints()).extracting(PublicPointDto::sequence, PublicPointDto::scoreAfter)
                .containsExactly(tuple(3, new ScoreDto(2, 1)), tuple(2, new ScoreDto(2, 0)), tuple(1, new ScoreDto(1, 0)));
        assertThat(state.currentSet().activeTimeout()).isEqualTo("opp");
        assertThat(state.currentSet().timeouts()).isEqualTo(new ScoreDto(0, 1));
        assertThat(state.currentSet().serving()).isEqualTo("opp");
    }

    @Test
    void should_name_top_scorer_from_lowest_sequence_when_payload_is_unordered() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                attackPoint(2, 3L, 7, "Renamed"),
                attackPoint(1, 3L, 7, "Dupont")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.topScorers()).extracting(t -> t.name(), t -> t.points())
                .containsExactly(tuple("Dupont", 2));
    }

    @Test
    void should_set_serving_to_last_rally_winner_and_null_before_first_point() {
        LiveMatchState withPoints = compute(simpleSnapshot(), true);
        LiveMatchState noPoint = compute(snapshot(List.of(set(1, null, List.of(neutral(1, 4L, 1, "Martin"))))), true);

        assertThat(withPoints.currentSet().serving()).isEqualTo("mine");
        assertThat(noPoint.currentSet().serving()).isNull();
        assertThat(noPoint.currentSet().trajectory()).containsExactly(new ScoreDto(0, 0));
    }

    @Test
    void should_use_highest_set_number_as_current_set_and_count_sets_won() {
        LiveStatsRequest snap = snapshot(List.of(
                set(3, null, List.of(attackPoint(1, 3L, 7, "Dupont"))),
                set(1, "mine", List.of(attackPoint(1, 3L, 7, "Dupont"))),
                set(2, "opp", List.of(opponentPoint(1, 12)))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().setNumber()).isEqualTo(3);
        assertThat(state.sets()).extracting(s -> s.setNumber()).containsExactly(1, 2, 3);
        assertThat(state.sets()).extracting(s -> s.wonBy()).containsExactly("mine", "opp", null);
        assertThat(state.setsWon()).isEqualTo(new ScoreDto(1, 1));
    }

    @Test
    void should_return_last_five_points_most_recent_first_across_sets() {
        LiveStatsRequest snap = snapshot(List.of(
                set(1, "mine", List.of(
                        attackPoint(1, 3L, 7, "Dupont"),
                        attackPoint(2, 3L, 7, "Dupont"),
                        opponentPoint(3, 12))),
                set(2, null, List.of(
                        attackPoint(1, 5L, 10, "Bernard"),
                        opponentPoint(2, 12),
                        opponentFault(3, 9)))));

        List<PublicPointDto> last = compute(snap, true).lastPoints();

        assertThat(last).hasSize(5);
        assertThat(last).extracting(PublicPointDto::setNumber).containsExactly(2, 2, 2, 1, 1);
        assertThat(last).extracting(PublicPointDto::sequence).containsExactly(3, 2, 1, 3, 2);
        assertThat(last.getFirst().scoreAfter()).isEqualTo(new ScoreDto(2, 1));
    }

    @Test
    void should_point_to_faulty_opponent_player_when_opponent_fault_gives_point() {
        PublicPointDto point = compute(simpleSnapshot(), true).lastPoints().getFirst();

        assertThat(point.scoredFor()).isEqualTo("mine");
        assertThat(point.team()).isEqualTo("opp");
        assertThat(point.player()).isEqualTo(new PublicPlayerDto(9, null));
        assertThat(point.action().key()).isEqualTo("serve_fault");
        assertThat(point.action().category()).isEqualTo("fault");
    }

    @Test
    void should_rank_top_scorers_by_points_then_jersey_ascending() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 5L, 10, "Bernard"),
                attackPoint(2, 3L, 7, "Dupont"),
                attackPoint(3, 6L, 4, "Petit"),
                attackPoint(4, 5L, 10, "Bernard"),
                attackPoint(5, 3L, 7, "Dupont"),
                attackPoint(6, 8L, 2, "Leroy"),
                myFault(7, 8L, 2, "Leroy"),
                opponentFault(8, 9),
                opponentPoint(9, 12)))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.topScorers()).extracting(t -> t.jersey()).containsExactly(7, 10, 2);
        assertThat(state.topScorers()).extracting(t -> t.points()).containsExactly(2, 2, 1);
        assertThat(state.topScorers().getFirst().name()).isEqualTo("Dupont");
    }

    @Test
    void should_hide_all_names_when_showPlayerNames_is_false() {
        LiveMatchState state = compute(simpleSnapshot(), false);

        assertThat(state.lastPoints()).extracting(p -> p.player().name()).containsOnlyNulls();
        assertThat(state.lastPoints()).extracting(p -> p.player().jersey()).containsExactly(9, 12, 7);
        assertThat(state.topScorers()).extracting(t -> t.name()).containsOnlyNulls();
    }

    @Test
    void should_never_show_opponent_names_when_showPlayerNames_is_true() {
        LiveMatchState state = compute(simpleSnapshot(), true);

        assertThat(state.lastPoints()).extracting(p -> p.player().name()).containsExactly(null, null, "Dupont");
    }

    @Test
    void should_lower_score_when_undo_removes_events() {
        LiveMatchState before = compute(simpleSnapshot(), true);
        LiveMatchState afterUndo = compute(snapshot(List.of(set(1, null, List.of(
                attackPoint(1, 3L, 7, "Dupont"),
                opponentPoint(2, 12))))), true);

        assertThat(before.currentSet().score()).isEqualTo(new ScoreDto(2, 1));
        assertThat(afterUndo.currentSet().score()).isEqualTo(new ScoreDto(1, 1));
        assertThat(afterUndo.currentSet().serving()).isEqualTo("opp");
        assertThat(afterUndo.lastPoints()).hasSize(2);
    }

    @Test
    void should_build_etag_without_session_id_and_change_with_name_visibility() {
        UUID id = UUID.randomUUID();
        String hidden = LivePublicViewService.etag(new LiveSessionHeader(id, 17, LiveSessionStatus.LIVE, false));
        String shown = LivePublicViewService.etag(new LiveSessionHeader(id, 17, LiveSessionStatus.LIVE, true));

        assertThat(hidden).startsWith("\"17-LIVE-0-").endsWith("\"").doesNotContain(id.toString());
        assertThat(shown).isNotEqualTo(hidden);
    }

    @Test
    void should_match_etag_in_list_or_weak_form() {
        String etag = "\"3-LIVE-0-abc\"";

        assertThat(LivePublicViewService.matchesEtag(etag, etag)).isTrue();
        assertThat(LivePublicViewService.matchesEtag("\"x\", W/" + etag, etag)).isTrue();
        assertThat(LivePublicViewService.matchesEtag("\"2-LIVE-0-abc\"", etag)).isFalse();
        assertThat(LivePublicViewService.matchesEtag(null, etag)).isFalse();
    }
}
