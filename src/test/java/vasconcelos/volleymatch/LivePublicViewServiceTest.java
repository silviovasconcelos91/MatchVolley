package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.myFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.neutral;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.simpleSnapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;

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
    void should_order_events_by_sequence_when_payload_is_unordered() {
        LiveStatsRequest snap = snapshot(List.of(set(1, null, List.of(
                opponentPoint(2, 12),
                attackPoint(1, 3L, 7, "Dupont")))));

        LiveMatchState state = compute(snap, true);

        assertThat(state.currentSet().trajectory()).containsExactly(
                new ScoreDto(0, 0), new ScoreDto(1, 0), new ScoreDto(1, 1));
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
