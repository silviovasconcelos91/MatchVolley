package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.volleymatch.dto.player.PlayerDto;
import vasconcelos.volleymatch.dto.player.PlayerSeasonStatsResponse;
import vasconcelos.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.volleymatch.mapper.PlayerMapper;
import vasconcelos.volleymatch.dto.live.LiveActionDto;
import vasconcelos.volleymatch.dto.live.LiveEventDto;
import vasconcelos.volleymatch.dto.live.LivePlayerDto;
import vasconcelos.volleymatch.model.match.Match;
import vasconcelos.volleymatch.model.match.MatchLiveSession;
import vasconcelos.volleymatch.model.match.MatchLiveSetEvent;
import vasconcelos.volleymatch.model.match.MatchResult;
import vasconcelos.volleymatch.model.match.VolleyPosition;
import vasconcelos.volleymatch.model.player.Player;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.MatchLiveSessionRepository;
import vasconcelos.volleymatch.repository.PlayerRepository;
import vasconcelos.volleymatch.repository.TeamRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.PlayerService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MatchLiveSessionRepository liveSessionRepository;

    @Mock
    private PlayerMapper playerMapper;

    @Mock
    private AuthService authService;

    private AppUser testUser;

    @InjectMocks
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder()
                .email("test@test.com").pseudo("test").password("hashed").build();
        when(authService.getCurrentUser()).thenReturn(testUser);
    }

    // ── season stats (computed from live events) ─────────────────────────────

    private LiveEventDto liveEvent(String team, Long playerId, VolleyPosition poste, String key, String category) {
        return new LiveEventDto("ev", 1, 0L, team,
                new LivePlayerDto(playerId, 7, "Dupont", 4, poste),
                new LiveActionDto(key, key, category), null, null);
    }

    private MatchLiveSession liveSession(String matchId, List<List<LiveEventDto>> eventsPerSet) {
        Match match = Match.builder().id(matchId).teamId(1L).date(LocalDate.now())
                .result(MatchResult.WON).mySets(2).oppSets(0).build();
        List<MatchLiveSetEvent> sets = new java.util.ArrayList<>();
        for (int i = 0; i < eventsPerSet.size(); i++) {
            sets.add(MatchLiveSetEvent.builder().set(i + 1).scoreTeam(25).scoreOpp(20)
                    .wonBy("mine").events(eventsPerSet.get(i)).build());
        }
        MatchLiveSession session = MatchLiveSession.builder().teamName("Les Lynx").recordedAt(Instant.now())
                .setsWonMine(2).setsWonOpp(0).sets(sets).build();
        session.setMatch(match);
        return session;
    }

    @Test
    void should_returnSeasonStats_when_playerHasLiveMatches() {
        MatchLiveSession session = liveSession("m-1", List.of(
                List.of(liveEvent("mine", 42L, VolleyPosition.Libero, "good_recv", "neutral"),
                        liveEvent("mine", 42L, VolleyPosition.Libero, "ace", "point"),
                        liveEvent("mine", 99L, VolleyPosition.R4, "attack_pt", "point"),
                        liveEvent("opp", null, null, "attack_pt", "point")),
                List.of(liveEvent("mine", 99L, VolleyPosition.R4, "attack_pt", "point"))));
        when(playerRepository.existsByIdAndUser(42L, testUser)).thenReturn(true);
        when(teamRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(liveSessionRepository.findByMatch_TeamIdAndMatch_User(1L, testUser)).thenReturn(List.of(session));

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(42L, 1L);

        assertThat(result.playerId()).isEqualTo(42L);
        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.setCount()).isEqualTo(1);
        assertThat(result.totalStats().points()).isEqualTo(1);
        assertThat(result.totalStats().acePoints()).isEqualTo(1);
        assertThat(result.totalStats().receptions()).isEqualTo(1);
        assertThat(result.totalStats().attackPoints()).isZero();
        assertThat(result.statsByPosition()).containsOnlyKeys("Libero");
        assertThat(result.statsByPosition().get("Libero").matchCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").setCount()).isEqualTo(1);
    }

    @Test
    void should_aggregateStatsByPosition_when_playerHasMultiplePositions() {
        MatchLiveSession m1 = liveSession("m-1", List.of(
                List.of(liveEvent("mine", 1L, VolleyPosition.Libero, "good_recv", "neutral"),
                        liveEvent("mine", 1L, VolleyPosition.Libero, "recv_fault", "fault")),
                List.of(liveEvent("mine", 1L, VolleyPosition.R4, "attack_pt", "point"),
                        liveEvent("mine", 1L, VolleyPosition.R4, "attack_fault", "fault"))));
        MatchLiveSession m2 = liveSession("m-2", List.of(
                List.of(liveEvent("mine", 1L, VolleyPosition.R4, "block_pt", "point"),
                        liveEvent("mine", 1L, VolleyPosition.R4, "serve_fault", "fault"))));
        when(playerRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(teamRepository.existsByIdAndUser(2L, testUser)).thenReturn(true);
        when(liveSessionRepository.findByMatch_TeamIdAndMatch_User(2L, testUser)).thenReturn(List.of(m1, m2));

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(1L, 2L);

        assertThat(result.matchCount()).isEqualTo(2);
        assertThat(result.setCount()).isEqualTo(3);
        assertThat(result.totalStats().points()).isEqualTo(2);
        assertThat(result.totalStats().faults()).isEqualTo(3);
        assertThat(result.totalStats().attackErrors()).isEqualTo(1);
        assertThat(result.totalStats().serviceErrors()).isEqualTo(1);
        assertThat(result.totalStats().receptions()).isEqualTo(2);
        assertThat(result.statsByPosition()).containsOnlyKeys("Libero", "R4");
        assertThat(result.statsByPosition().get("Libero").matchCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").setCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").stats().receptions()).isEqualTo(2);
        assertThat(result.statsByPosition().get("R4").matchCount()).isEqualTo(2);
        assertThat(result.statsByPosition().get("R4").setCount()).isEqualTo(2);
        assertThat(result.statsByPosition().get("R4").stats().attackPoints()).isEqualTo(1);
        assertThat(result.statsByPosition().get("R4").stats().blockPoints()).isEqualTo(1);
    }

    @Test
    void should_returnZeroStats_when_playerHasNoLiveEvents() {
        when(playerRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(teamRepository.existsByIdAndUser(2L, testUser)).thenReturn(true);
        when(liveSessionRepository.findByMatch_TeamIdAndMatch_User(2L, testUser)).thenReturn(List.of());

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(1L, 2L);

        assertThat(result.matchCount()).isZero();
        assertThat(result.setCount()).isZero();
        assertThat(result.totalStats().points()).isZero();
        assertThat(result.statsByPosition()).isEmpty();
    }

    @Test
    void should_throwNoSuchElementException_when_getSeasonStatsPlayerNotFound() {
        when(playerRepository.existsByIdAndUser(99L, testUser)).thenReturn(false);

        assertThatThrownBy(() -> playerService.getPlayerSeasonStats(99L, 1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_throwNoSuchElementException_when_getSeasonStatsTeamNotFound() {
        when(playerRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(teamRepository.existsByIdAndUser(99L, testUser)).thenReturn(false);

        assertThatThrownBy(() -> playerService.getPlayerSeasonStats(1L, 99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_returnAllPlayers_when_getAllPlayers() {
        Player player1 = Player.builder().name("Alice").roles(List.of(VolleyPosition.Libero)).numero(7).build();
        Player player2 = Player.builder().name("Bob").roles(List.of(VolleyPosition.Passeur)).numero(1).build();
        PlayerDto dto1 = new PlayerDto(1L, "Alice", List.of(VolleyPosition.Libero), 7, List.of());
        PlayerDto dto2 = new PlayerDto(2L, "Bob", List.of(VolleyPosition.Passeur), 1, List.of());
        when(playerRepository.findAllByUser(testUser)).thenReturn(List.of(player1, player2));
        when(playerMapper.toDto(player1)).thenReturn(dto1);
        when(playerMapper.toDto(player2)).thenReturn(dto2);

        List<PlayerDto> result = playerService.getAllPlayers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlayerDto::name).containsExactly("Alice", "Bob");
    }

    @Test
    void should_returnPlayer_when_playerExists() {
        Player player = Player.builder().name("Alice").roles(List.of(VolleyPosition.Libero)).numero(7).build();
        PlayerDto dto = new PlayerDto(1L, "Alice", List.of(VolleyPosition.Libero), 7, List.of());
        when(playerRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(player));
        when(playerMapper.toDto(player)).thenReturn(dto);

        PlayerDto result = playerService.getPlayer(1L);

        assertThat(result.name()).isEqualTo("Alice");
    }

    @Test
    void should_throwNoSuchElementException_when_playerNotFound() {
        when(playerRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayer(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_returnCreatedPlayer_when_createPlayerWithNoTeams() {
        CreatePlayerRequest request = new CreatePlayerRequest("Charlie", List.of(VolleyPosition.Pointu), 14, List.of());
        Player entity = Player.builder().name("Charlie").roles(List.of(VolleyPosition.Pointu)).numero(14).build();
        PlayerDto dto = new PlayerDto(3L, "Charlie", List.of(VolleyPosition.Pointu), 14, List.of());
        when(playerMapper.toEntity(request)).thenReturn(entity);
        when(playerRepository.save(any())).thenReturn(entity);
        when(playerMapper.toDto(entity)).thenReturn(dto);

        PlayerDto result = playerService.createPlayer(request);

        assertThat(result.name()).isEqualTo("Charlie");
        assertThat(result.numero()).isEqualTo(14);
        verify(playerRepository).save(any());
    }

    @Test
    void should_updatePlayerName_when_nameChanges() {
        Player player = Player.builder().name("Old Name").roles(List.of(VolleyPosition.Libero)).numero(7).build();
        PlayerDto dto = new PlayerDto(1L, "New Name", List.of(VolleyPosition.Libero), 7, List.of());
        when(playerRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(player));
        when(playerMapper.toDto(player)).thenReturn(dto);

        UpdatePlayerRequest request = new UpdatePlayerRequest("New Name", null, null, null);
        PlayerDto result = playerService.updatePlayer(1L, request);

        assertThat(player.getName()).isEqualTo("New Name");
        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void should_throwNoSuchElementException_when_updatePlayerNotFound() {
        when(playerRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.updatePlayer(99L, new UpdatePlayerRequest(null, null, null, null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_deletePlayer_when_playerExists() {
        when(playerRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);

        playerService.deletePlayer(1L);

        verify(playerRepository).deleteById(1L);
    }

    @Test
    void should_throwNoSuchElementException_when_deletePlayerNotFound() {
        when(playerRepository.existsByIdAndUser(99L, testUser)).thenReturn(false);

        assertThatThrownBy(() -> playerService.deletePlayer(99L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
