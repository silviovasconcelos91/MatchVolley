package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vasconcelos.volleymatch.dto.live.LiveSetAnalysisDto;
import vasconcelos.volleymatch.dto.live.LiveSetDto;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.livesession.LiveMatchState;
import vasconcelos.volleymatch.dto.livesession.ScoreDto;
import vasconcelos.volleymatch.mapper.LiveSessionMapper;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.model.match.MatchResult;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.LiveSessionRepository;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.LivePublicViewService;
import vasconcelos.volleymatch.service.LiveStatsAnalysisService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.attackPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.malus;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.myFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.neutral;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentFault;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.opponentPoint;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.set;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.snapshot;
import static vasconcelos.volleymatch.LiveSnapshotFixtures.timeout;

/**
 * MV-002 CA-3: the overlay and the analysis replay a set the same way, whatever the stored order.
 */
class LiveScoreReplayConsistencyTest {

    private final LivePublicViewService overlay = new LivePublicViewService(
            mock(LiveSessionRepository.class), Mappers.getMapper(LiveSessionMapper.class), Clock.systemUTC());
    private final MatchLiveSessionRepository matchLiveSessionRepository = mock(MatchLiveSessionRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final LiveStatsAnalysisService analysis =
            new LiveStatsAnalysisService(matchLiveSessionRepository, authService);

    @Test
    void should_give_same_score_progression_in_overlay_and_analysis_when_set_is_stored_unordered() {
        LiveSetDto unorderedSet = set(1, null, List.of(
                opponentPoint(6, 12),
                timeout(4, "mine"),
                malus(2, "opp", 1),
                neutral(5, 4L, 1, "Martin"),
                attackPoint(3, 3L, 7, "Dupont"),
                myFault(8, 8L, 2, "Leroy"),
                malus(1, "mine", 2),
                opponentFault(7, 9),
                timeout(9, "opp")));
        LiveStatsRequest request = snapshot(List.of(unorderedSet));

        LiveMatchState state = overlay.computeState(request, true);
        LiveSetAnalysisDto analysed = analyse(unorderedSet);

        List<ScoreDto> trajectory = state.currentSet().trajectory();
        List<ScoreDto> timelineScores = analysed.timeline().stream()
                .map(entry -> new ScoreDto(entry.myScore(), entry.oppScore()))
                .toList();
        assertThat(trajectory.subList(1, trajectory.size())).isEqualTo(timelineScores);
        assertThat(trajectory.getFirst())
                .isEqualTo(new ScoreDto(analysed.startScore().myScore(), analysed.startScore().oppScore()));
        assertThat(timelineScores).containsExactly(
                new ScoreDto(-1, -1), new ScoreDto(-1, 0), new ScoreDto(0, 0), new ScoreDto(0, 1));
        assertThat(analysed.timeouts()).extracting(t -> t.afterPoint())
                .containsExactly(1, 4);
        assertThat(state.currentSet().activeTimeout()).isEqualTo("opp");
    }

    private LiveSetAnalysisDto analyse(LiveSetDto set) {
        AppUser user = AppUser.builder().email("t@t.com").pseudo("t").password("h").build();
        Match match = Match.builder().id("m-cross").teamId(1L).home(true)
                .date(LocalDate.now()).mySets(0).oppSets(0).result(MatchResult.WON).build();
        MatchLiveSetEvent setEvent = MatchLiveSetEvent.builder()
                .set(set.setNumber()).scoreTeam(0).scoreOpp(1).wonBy(null).events(set.events()).build();
        MatchLiveSession session = MatchLiveSession.builder()
                .teamName("Les Lynx").recordedAt(Instant.now())
                .setsWonMine(0).setsWonOpp(0).sets(List.of(setEvent)).build();
        session.setMatch(match);
        when(authService.getCurrentUser()).thenReturn(user);
        when(matchLiveSessionRepository.findByMatch_IdAndMatch_User("m-cross", user)).thenReturn(Optional.of(session));
        return analysis.getMatchAnalysis("m-cross").sets().getFirst();
    }
}
