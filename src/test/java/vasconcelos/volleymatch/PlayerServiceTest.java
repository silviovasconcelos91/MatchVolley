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
import vasconcelos.volleymatch.model.match.PlayerMatchStat;
import vasconcelos.volleymatch.model.match.PlayerSetStat;
import vasconcelos.volleymatch.model.match.VolleyPosition;
import vasconcelos.volleymatch.model.match.VolleyStats;
import vasconcelos.volleymatch.model.player.Player;
import vasconcelos.volleymatch.model.user.AppUser;
import vasconcelos.volleymatch.repository.PlayerMatchStatRepository;
import vasconcelos.volleymatch.repository.PlayerRepository;
import vasconcelos.volleymatch.repository.TeamRepository;
import vasconcelos.volleymatch.service.AuthService;
import vasconcelos.volleymatch.service.PlayerService;

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
    private PlayerMatchStatRepository playerMatchStatRepository;

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

    @Test
    void should_returnSeasonStats_when_playerHasMatches() {
        VolleyStats stats = new VolleyStats(10, 5, 2, 3, 1, 1, 8, 2);
        PlayerSetStat setStat = PlayerSetStat.builder()
                .set(1).position(VolleyPosition.Libero).stats(stats).build();
        PlayerMatchStat matchStat = new PlayerMatchStat(1L, null, 42L, 7, VolleyPosition.Libero, stats, List.of(setStat));
        when(playerRepository.existsByIdAndUser(42L, testUser)).thenReturn(true);
        when(teamRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(playerMatchStatRepository.findByPlayerIdAndMatchTeamId(42L, 1L)).thenReturn(List.of(matchStat));

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(42L, 1L);

        assertThat(result.playerId()).isEqualTo(42L);
        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.setCount()).isEqualTo(1);
        assertThat(result.totalStats().points()).isEqualTo(10);
        assertThat(result.statsByPosition()).containsKey("Libero");
        assertThat(result.statsByPosition().get("Libero").matchCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").setCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").stats().points()).isEqualTo(10);
    }

    @Test
    void should_aggregateStatsByPosition_when_playerHasMultiplePositions() {
        VolleyStats liberoStats = new VolleyStats(5, 0, 0, 0, 0, 0, 10, 1);
        VolleyStats r4Stats = new VolleyStats(8, 4, 1, 3, 1, 1, 2, 3);
        PlayerSetStat liberoSet = PlayerSetStat.builder().set(1).position(VolleyPosition.Libero).stats(liberoStats).build();
        PlayerSetStat r4Set = PlayerSetStat.builder().set(2).position(VolleyPosition.R4).stats(r4Stats).build();
        VolleyStats matchTotal = new VolleyStats(13, 4, 1, 3, 1, 1, 12, 4);
        PlayerMatchStat matchStat = new PlayerMatchStat(1L, null, 1L, 7, VolleyPosition.Libero, matchTotal, List.of(liberoSet, r4Set));
        when(playerRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(teamRepository.existsByIdAndUser(2L, testUser)).thenReturn(true);
        when(playerMatchStatRepository.findByPlayerIdAndMatchTeamId(1L, 2L)).thenReturn(List.of(matchStat));

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(1L, 2L);

        assertThat(result.setCount()).isEqualTo(2);
        assertThat(result.statsByPosition()).hasSize(2);
        assertThat(result.statsByPosition().get("Libero").matchCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").setCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("Libero").stats().receptions()).isEqualTo(10);
        assertThat(result.statsByPosition().get("R4").matchCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("R4").setCount()).isEqualTo(1);
        assertThat(result.statsByPosition().get("R4").stats().attackPoints()).isEqualTo(4);
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
        Player player1 = Player.builder().name("Alice").roles(List.of(VolleyPosition.Libero)).numero(7).age(22).taille("172cm").build();
        Player player2 = Player.builder().name("Bob").roles(List.of(VolleyPosition.Passeur)).numero(1).age(25).taille("188cm").build();
        PlayerDto dto1 = new PlayerDto(1L, "Alice", List.of(VolleyPosition.Libero), 7, 22, "172cm", List.of());
        PlayerDto dto2 = new PlayerDto(2L, "Bob", List.of(VolleyPosition.Passeur), 1, 25, "188cm", List.of());
        when(playerRepository.findAllByUser(testUser)).thenReturn(List.of(player1, player2));
        when(playerMapper.toDto(player1)).thenReturn(dto1);
        when(playerMapper.toDto(player2)).thenReturn(dto2);

        List<PlayerDto> result = playerService.getAllPlayers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlayerDto::name).containsExactly("Alice", "Bob");
    }

    @Test
    void should_returnPlayer_when_playerExists() {
        Player player = Player.builder().name("Alice").roles(List.of(VolleyPosition.Libero)).numero(7).age(22).taille("172cm").build();
        PlayerDto dto = new PlayerDto(1L, "Alice", List.of(VolleyPosition.Libero), 7, 22, "172cm", List.of());
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
        CreatePlayerRequest request = new CreatePlayerRequest("Charlie", List.of(VolleyPosition.Pointu), 14, 27, "195cm", List.of());
        Player entity = Player.builder().name("Charlie").roles(List.of(VolleyPosition.Pointu)).numero(14).age(27).taille("195cm").build();
        PlayerDto dto = new PlayerDto(3L, "Charlie", List.of(VolleyPosition.Pointu), 14, 27, "195cm", List.of());
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
        Player player = Player.builder().name("Old Name").roles(List.of(VolleyPosition.Libero)).numero(7).age(25).taille("180cm").build();
        PlayerDto dto = new PlayerDto(1L, "New Name", List.of(VolleyPosition.Libero), 7, 25, "180cm", List.of());
        when(playerRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(player));
        when(playerMapper.toDto(player)).thenReturn(dto);

        UpdatePlayerRequest request = new UpdatePlayerRequest("New Name", null, null, null, null, null);
        PlayerDto result = playerService.updatePlayer(1L, request);

        assertThat(player.getName()).isEqualTo("New Name");
        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void should_throwNoSuchElementException_when_updatePlayerNotFound() {
        when(playerRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.updatePlayer(99L, new UpdatePlayerRequest(null, null, null, null, null, null)))
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
