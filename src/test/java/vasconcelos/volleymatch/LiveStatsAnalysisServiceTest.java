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
import vasconcelos.volleymatch.dto.live.LiveAttackZoneDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LiveMatchAnalysisResponse;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.dto.live.LiveScopeStatsDto;
import vasconcelos.volleymatch.dto.live.LiveTrajectoryDto;
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

    @Mock private MatchLiveSessionRepository liveSessionRepository;
    @Mock private AuthService authService;
    @InjectMocks private LiveStatsAnalysisService service;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder().email("t@t.com").pseudo("t").password("h").build();
        when(authService.getCurrentUser()).thenReturn(testUser);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private LiveEventDto event(int seq, String team, String key, String category, String label,
                               String scoredFor, Long playerId, Integer playerPos,
                               Integer trajFrom, Integer trajTo) {
        LivePlayerDto player = playerId != null
                ? new LivePlayerDto(playerId, 7, "Dupont", playerPos)
                : new LivePlayerDto(null, 5, "Adv #5", playerPos);
        LiveActionDto action = new LiveActionDto(key, label, category);
        LiveTrajectoryDto traj = (trajFrom != null || trajTo != null) ? new LiveTrajectoryDto(trajFrom, trajTo) : null;
        return new LiveEventDto("ev-" + seq, seq, 1000L * seq, team, player, action, traj, scoredFor);
    }

    private MatchLiveSetEvent setEvent(int setNum, List<LiveEventDto> events) {
        return MatchLiveSetEvent.builder()
                .setNumber(setNum).scoreTeam(4).scoreOpp(2).wonBy("mine").events(events).build();
    }

    private MatchLiveSession sessionWith(String matchId, List<MatchLiveSetEvent> sets) {
        Match match = Match.builder().id(matchId).teamId(1L).home(true)
                .date(LocalDate.now()).mySets(2).oppSets(1).result(MatchResult.WON).build();
        MatchLiveSession sess = MatchLiveSession.builder()
                .teamName("Les Lynx").recordedAt(Instant.now())
                .setsWonMine(2).setsWonOpp(1).sets(sets).build();
        sess.setMatch(match);
        return sess;
    }

    // ── computeActions ────────────────────────────────────────────────────────

    @Test
    void should_countPointActions_when_mineTeamScores() {
        var events = List.of(
                event(1, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 1),
                event(2, "mine", "ace",       "point", "Ace",     "mine", 5L, 1, null, 5)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.actions().points()).hasSize(2);
        assertThat(stats.actions().points()).extracting("key").containsExactlyInAnyOrder("attack_pt", "ace");
        assertThat(stats.actions().points()).extracting("count").containsExactlyInAnyOrder(1, 1);
    }

    @Test
    void should_countFaultActions_when_mineTeamFaults() {
        var events = List.of(
                event(1, "mine", "attack_fault", "fault", "Faute attaque", "opp", 3L, 4, null, null),
                event(2, "mine", "serve_fault",  "fault", "Faute service", "opp", 5L, 1, null, null)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.actions().faults()).hasSize(2);
        assertThat(stats.actions().points()).isEmpty();
    }

    @Test
    void should_countNeutralActions_when_noScoring() {
        var events = List.of(
                event(1, "mine", "good_recv", "neutral", "Bonne réception", null, 3L, 6, null, null)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.actions().neutral()).hasSize(1);
        assertThat(stats.actions().neutral().get(0).key()).isEqualTo("good_recv");
        assertThat(stats.actions().neutral().get(0).count()).isEqualTo(1);
    }

    @Test
    void should_aggregateSameAction_when_actionAppearsMultipleTimes() {
        var events = List.of(
                event(1, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 1),
                event(2, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 6)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.actions().points()).hasSize(1);
        assertThat(stats.actions().points().get(0).count()).isEqualTo(2);
    }

    @Test
    void should_excludeOppTeamActions_from_scope() {
        var events = List.of(
                event(1, "opp", "attack_pt", "point", "Attaque", "opp", null, 4, 4, 1)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.actions().points()).isEmpty();
    }

    // ── computeAcesByZone ──────────────────────────────────────────────────────

    @Test
    void should_countAcesByLandingZone_when_aceEventsExist() {
        var events = List.of(
                event(1, "mine", "ace", "point", "Ace", "mine", 5L, 1, null, 5),
                event(2, "mine", "ace", "point", "Ace", "mine", 5L, 1, null, 5),
                event(3, "mine", "ace", "point", "Ace", "mine", 11L, 1, null, 2)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.acesByZone()).hasSize(2);
        var zone5 = stats.acesByZone().stream().filter(z -> z.zone() == 5).findFirst().orElseThrow();
        var zone2 = stats.acesByZone().stream().filter(z -> z.zone() == 2).findFirst().orElseThrow();
        assertThat(zone5.count()).isEqualTo(2);
        assertThat(zone2.count()).isEqualTo(1);
    }

    @Test
    void should_returnEmptyAcesByZone_when_noAceEvents() {
        var events = List.of(
                event(1, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 1)
        );
        assertThat(service.computeScopeStats(events).acesByZone()).isEmpty();
    }

    // ── computeAttacks ─────────────────────────────────────────────────────────

    @Test
    void should_groupAttacksByPositionAndZone_when_attackEventsExist() {
        var events = List.of(
                event(1, "mine", "attack_pt",    "point",   "Attaque",      "mine", 3L, 4, 4, 1),
                event(2, "mine", "attack_pt",    "point",   "Attaque",      "mine", 3L, 4, 4, 1),
                event(3, "mine", "attack_no_pt", "neutral", "Attaque(spt)", null,   3L, 4, 4, 6)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.attacks()).hasSize(2);
        var hit = stats.attacks().stream()
                .filter(a -> "attack_pt".equals(a.result()) && a.to() == 1).findFirst().orElseThrow();
        assertThat(hit.count()).isEqualTo(2);
        assertThat(hit.playerPosition()).isEqualTo(4);
        assertThat(hit.from()).isEqualTo(4);
    }

    @Test
    void should_includeAttackFault_in_attacks_with_nullTrajectory() {
        var events = List.of(
                event(1, "mine", "attack_fault", "fault", "Faute attaque", "opp", 3L, 4, null, null)
        );
        var stats = service.computeScopeStats(events);
        assertThat(stats.attacks()).hasSize(1);
        assertThat(stats.attacks().get(0).result()).isEqualTo("attack_fault");
        assertThat(stats.attacks().get(0).from()).isNull();
        assertThat(stats.attacks().get(0).to()).isNull();
    }

    // ── buildTimeline ──────────────────────────────────────────────────────────

    @Test
    void should_buildTimeline_with_runningScore() {
        var events = List.of(
                event(1, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 1),
                event(2, "opp",  "attack_pt", "point", "Attaque", "opp",  null, 4, 4, 6)
        );
        var timeline = service.buildTimeline(events);
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).myScore()).isEqualTo(1);
        assertThat(timeline.get(0).oppScore()).isZero();
        assertThat(timeline.get(1).myScore()).isEqualTo(1);
        assertThat(timeline.get(1).oppScore()).isEqualTo(1);
    }

    @Test
    void should_setNullPlayerId_in_timeline_when_oppScores() {
        var events = List.of(
                event(1, "opp", "attack_pt", "point", "Attaque", "opp", null, 4, 4, 6)
        );
        assertThat(service.buildTimeline(events).get(0).playerId()).isNull();
    }

    // ── computePlayerAnalysis ──────────────────────────────────────────────────

    @Test
    void should_excludeOpponentPlayers_from_playerList() {
        var se = setEvent(1, List.of(
                event(1, "opp", "attack_pt", "point", "Attaque", "opp", null, 4, 4, 6)
        ));
        assertThat(service.computePlayerAnalysis(List.of(se))).isEmpty();
    }

    @Test
    void should_aggregatePlayerScopeStats_across_sets() {
        var se1 = setEvent(1, List.of(
                event(1, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 1)
        ));
        var se2 = setEvent(2, List.of(
                event(1, "mine", "ace", "point", "Ace", "mine", 3L, 1, null, 5)
        ));
        var players = service.computePlayerAnalysis(List.of(se1, se2));
        assertThat(players).hasSize(1);
        assertThat(players.get(0).matchStats().actions().points()).hasSize(2); // attack_pt + ace
        assertThat(players.get(0).setStats()).hasSize(2);
    }

    // ── getMatchAnalysis ───────────────────────────────────────────────────────

    @Test
    void should_throw404_when_matchNotFound() {
        when(liveSessionRepository.findByMatch_IdAndMatch_User("x", testUser)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMatchAnalysis("x"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_returnFullAnalysis_when_matchExists() {
        var events = List.of(
                event(1, "mine", "attack_pt", "point", "Attaque", "mine", 3L, 4, 4, 1),
                event(2, "mine", "ace",       "point", "Ace",     "mine", 5L, 1, null, 5)
        );
        var sess = sessionWith("m-01", List.of(setEvent(1, events)));
        when(liveSessionRepository.findByMatch_IdAndMatch_User("m-01", testUser))
                .thenReturn(Optional.of(sess));

        LiveMatchAnalysisResponse result = service.getMatchAnalysis("m-01");

        assertThat(result.matchId()).isEqualTo("m-01");
        assertThat(result.globalStats().actions().points()).hasSize(2);
        assertThat(result.sets()).hasSize(1);
        assertThat(result.sets().get(0).timeline()).hasSize(2);
        assertThat(result.players()).hasSize(2);
    }
}
