package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveMatchAnalysisResponse;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.match.StatsDto;
import vasconcelos.volleymatch.dto.match.TimelineEntryDto;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.model.match.MatchResult;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.LiveStatsAnalysisService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveStatsAnalysisServiceTest {

    @Mock
    private MatchLiveSessionRepository liveSessionRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private LiveStatsAnalysisService service;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder().email("t@t.com").pseudo("t").password("h").build();
        when(authService.getCurrentUser()).thenReturn(testUser);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private LiveEventDto event(int seq, String team, String key, String scoredFor, Long playerId) {
        LivePlayerDto player = new LivePlayerDto(playerId, 7, "Dupont", 4);
        LiveActionDto action = new LiveActionDto(key, key, "point");
        return new LiveEventDto("ev-" + seq, seq, 1000L * seq, team, player, action, null, scoredFor);
    }

    private MatchLiveSetEvent setEvent(int setNum, List<LiveEventDto> events) {
        return MatchLiveSetEvent.builder()
                .setNumber(setNum)
                .scoreTeam(4)
                .scoreOpp(2)
                .wonBy("mine")
                .events(events)
                .build();
    }

    // ── computeStats ─────────────────────────────────────────────────────────

    @Test
    void should_returnZeroStats_when_noEvents() {
        StatsDto stats = service.computeStats(List.of());
        assertThat(stats.points()).isZero();
        assertThat(stats.attackPoints()).isZero();
    }

    @Test
    void should_returnAttackBlockAcePoints_when_correspondingEventsExist() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "attack_pt", "mine", 3L),
                event(2, "mine", "block_pt",  "mine", 3L),
                event(3, "mine", "ace",        "mine", 5L)
        );
        StatsDto stats = service.computeStats(events);
        assertThat(stats.points()).isEqualTo(3);
        assertThat(stats.attackPoints()).isEqualTo(1);
        assertThat(stats.blockPoints()).isEqualTo(1);
        assertThat(stats.acePoints()).isEqualTo(1);
    }

    @Test
    void should_countAttackAndServiceErrors_when_mineTeamFaults() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "attack_fault", "opp", 3L),
                event(2, "mine", "serve_fault",  "opp", 3L)
        );
        StatsDto stats = service.computeStats(events);
        assertThat(stats.attackErrors()).isEqualTo(1);
        assertThat(stats.serviceErrors()).isEqualTo(1);
        assertThat(stats.points()).isZero();
    }

    @Test
    void should_countReceptions_when_goodRecvEventExists() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "good_recv", null, 3L)
        );
        StatsDto stats = service.computeStats(events);
        assertThat(stats.receptions()).isEqualTo(1);
    }

    @Test
    void should_countFaults_when_faultAndRecvFaultEventsExist() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "fault",      "opp", 3L),
                event(2, "mine", "recv_fault", "opp", 3L)
        );
        StatsDto stats = service.computeStats(events);
        assertThat(stats.faults()).isEqualTo(2);
    }

    // ── buildTimeline ─────────────────────────────────────────────────────────

    @Test
    void should_buildTimeline_with_runningScore_per_set() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "attack_pt", "mine", 3L),
                event(2, "opp",  "attack_pt", "opp",  null),
                event(3, "mine", "ace",        "mine", 5L)
        );
        List<TimelineEntryDto> timeline = service.buildTimeline(events);
        assertThat(timeline).hasSize(3);
        assertThat(timeline.get(0).myScore()).isEqualTo(1);
        assertThat(timeline.get(0).oppScore()).isZero();
        assertThat(timeline.get(1).myScore()).isEqualTo(1);
        assertThat(timeline.get(1).oppScore()).isEqualTo(1);
        assertThat(timeline.get(2).myScore()).isEqualTo(2);
    }

    @Test
    void should_setNullPlayerId_in_timeline_when_oppTeamScores() {
        List<LiveEventDto> events = List.of(
                event(1, "opp", "attack_pt", "opp", null)
        );
        List<TimelineEntryDto> timeline = service.buildTimeline(events);
        assertThat(timeline.get(0).playerId()).isNull();
    }

    @Test
    void should_convertTsToInstant_when_buildingTimeline() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "attack_pt", "mine", 3L)
        );
        List<TimelineEntryDto> timeline = service.buildTimeline(events);
        assertThat(timeline.get(0).occurredAt()).isEqualTo(Instant.ofEpochMilli(1000L));
    }

    @Test
    void should_excludeNeutralEvents_from_timeline() {
        List<LiveEventDto> events = List.of(
                event(1, "mine", "good_recv", null, 3L),
                event(2, "mine", "attack_pt", "mine", 3L)
        );
        List<TimelineEntryDto> timeline = service.buildTimeline(events);
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).action()).isEqualTo("attack_pt");
    }

    // ── computePlayerAnalysis ──────────────────────────────────────────────────

    @Test
    void should_excludeOpponentPlayers_from_playerList() {
        MatchLiveSetEvent se = setEvent(1, List.of(
                event(1, "opp", "attack_pt", "opp", null)
        ));
        assertThat(service.computePlayerAnalysis(List.of(se))).isEmpty();
    }

    @Test
    void should_aggregatePlayerStats_across_sets() {
        List<LiveEventDto> set1Events = List.of(
                event(1, "mine", "attack_pt", "mine", 3L)
        );
        List<LiveEventDto> set2Events = List.of(
                event(1, "mine", "attack_pt", "mine", 3L),
                event(2, "mine", "ace",        "mine", 3L)
        );
        MatchLiveSetEvent se1 = setEvent(1, set1Events);
        MatchLiveSetEvent se2 = setEvent(2, set2Events);

        var players = service.computePlayerAnalysis(List.of(se1, se2));

        assertThat(players).hasSize(1);
        assertThat(players.get(0).playerId()).isEqualTo(3L);
        assertThat(players.get(0).matchStats().attackPoints()).isEqualTo(2);
        assertThat(players.get(0).setStats()).hasSize(2);
    }

    // ── getMatchAnalysis ──────────────────────────────────────────────────────

    @Test
    void should_throw404_when_matchNotFound() {
        when(liveSessionRepository.findByMatch_IdAndMatch_User("unknown", testUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMatchAnalysis("unknown"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void should_returnGlobalPoints_when_matchHasMultipleSets() {
        List<LiveEventDto> set1 = List.of(
                event(1, "mine", "attack_pt", "mine", 3L),
                event(2, "mine", "ace",        "mine", 5L)
        );
        List<LiveEventDto> set2 = List.of(
                event(1, "opp", "attack_pt", "opp", null),
                event(2, "mine", "block_pt",  "mine", 3L)
        );
        MatchLiveSetEvent se1 = setEvent(1, set1);
        MatchLiveSetEvent se2 = setEvent(2, set2);

        Match match = Match.builder().id("m-01").teamId(1L).home(true)
                .date(LocalDate.now()).mySets(2).oppSets(1).result(MatchResult.WON).build();
        MatchLiveSession sess = MatchLiveSession.builder()
                .teamName("Les Lynx").recordedAt(Instant.now())
                .setsWonMine(2).setsWonOpp(1)
                .sets(List.of(se1, se2))
                .build();
        sess.setMatch(match);

        when(liveSessionRepository.findByMatch_IdAndMatch_User("m-01", testUser))
                .thenReturn(Optional.of(sess));

        LiveMatchAnalysisResponse result = service.getMatchAnalysis("m-01");

        assertThat(result.matchId()).isEqualTo("m-01");
        assertThat(result.globalStats().points()).isEqualTo(3); // 2 mine + 1 mine
        assertThat(result.sets()).hasSize(2);
        assertThat(result.players()).isNotEmpty();
    }
}
