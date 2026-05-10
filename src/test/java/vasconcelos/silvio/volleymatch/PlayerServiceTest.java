package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.silvio.volleymatch.dto.match.StatsDto;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.dto.player.PlayerSeasonStatsResponse;
import vasconcelos.silvio.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.silvio.volleymatch.mapper.PlayerMapper;
import vasconcelos.silvio.volleymatch.model.match.PlayerMatchStat;
import vasconcelos.silvio.volleymatch.model.match.PlayerSetStat;
import vasconcelos.silvio.volleymatch.model.match.VolleyPosition;
import vasconcelos.silvio.volleymatch.model.match.VolleyStats;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.repository.PlayerMatchStatRepository;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;
import vasconcelos.silvio.volleymatch.service.PlayerService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @InjectMocks
    private PlayerService playerService;

    @Test
    void should_returnSeasonStats_when_playerHasMatches() {
        VolleyStats stats = new VolleyStats(10, 5, 2, 3, 1, 1, 8);
        PlayerSetStat setStat = PlayerSetStat.builder()
                .set(1).position(VolleyPosition.Libero).stats(stats).build();
        PlayerMatchStat matchStat = new PlayerMatchStat(1L, null, 42L, 7, "libero", stats, List.of(setStat));
        when(playerRepository.existsById(42L)).thenReturn(true);
        when(teamRepository.existsById(1L)).thenReturn(true);
        when(playerMatchStatRepository.findByPlayerIdAndMatchTeamId(42L, 1L)).thenReturn(List.of(matchStat));

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(42L, 1L);

        assertThat(result.playerId()).isEqualTo(42L);
        assertThat(result.matchCount()).isEqualTo(1);
        assertThat(result.totalStats().points()).isEqualTo(10);
        assertThat(result.statsByPosition()).containsKey("Libero");
        assertThat(result.statsByPosition().get("Libero").points()).isEqualTo(10);
    }

    @Test
    void should_aggregateStatsByPosition_when_playerHasMultiplePositions() {
        VolleyStats liberoStats = new VolleyStats(5, 0, 0, 0, 0, 0, 10);
        VolleyStats r4Stats = new VolleyStats(8, 4, 1, 3, 1, 1, 2);
        PlayerSetStat liberoSet = PlayerSetStat.builder().set(1).position(VolleyPosition.Libero).stats(liberoStats).build();
        PlayerSetStat r4Set = PlayerSetStat.builder().set(2).position(VolleyPosition.R4).stats(r4Stats).build();
        VolleyStats matchTotal = new VolleyStats(13, 4, 1, 3, 1, 1, 12);
        PlayerMatchStat matchStat = new PlayerMatchStat(1L, null, 1L, 7, "libero", matchTotal, List.of(liberoSet, r4Set));
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(teamRepository.existsById(2L)).thenReturn(true);
        when(playerMatchStatRepository.findByPlayerIdAndMatchTeamId(1L, 2L)).thenReturn(List.of(matchStat));

        PlayerSeasonStatsResponse result = playerService.getPlayerSeasonStats(1L, 2L);

        assertThat(result.statsByPosition()).hasSize(2);
        assertThat(result.statsByPosition().get("Libero").receptions()).isEqualTo(10);
        assertThat(result.statsByPosition().get("R4").attackPoints()).isEqualTo(4);
    }

    @Test
    void should_throwNoSuchElementException_when_getSeasonStatsPlayerNotFound() {
        when(playerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> playerService.getPlayerSeasonStats(99L, 1L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_throwNoSuchElementException_when_getSeasonStatsTeamNotFound() {
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(teamRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> playerService.getPlayerSeasonStats(1L, 99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_returnAllPlayers_when_getAllPlayers() {
        Player player1 = Player.builder().name("Alice").role("libero").numero(7).age(22).taille("172cm").build();
        Player player2 = Player.builder().name("Bob").role("setter").numero(1).age(25).taille("188cm").build();
        PlayerDto dto1 = new PlayerDto(1L, "Alice", "libero", 7, 22, "172cm", List.of());
        PlayerDto dto2 = new PlayerDto(2L, "Bob", "setter", 1, 25, "188cm", List.of());
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(playerMapper.toDto(player1)).thenReturn(dto1);
        when(playerMapper.toDto(player2)).thenReturn(dto2);

        List<PlayerDto> result = playerService.getAllPlayers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlayerDto::name).containsExactly("Alice", "Bob");
    }

    @Test
    void should_returnPlayer_when_playerExists() {
        Player player = Player.builder().name("Alice").role("libero").numero(7).age(22).taille("172cm").build();
        PlayerDto dto = new PlayerDto(1L, "Alice", "libero", 7, 22, "172cm", List.of());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(playerMapper.toDto(player)).thenReturn(dto);

        PlayerDto result = playerService.getPlayer(1L);

        assertThat(result.name()).isEqualTo("Alice");
    }

    @Test
    void should_throwNoSuchElementException_when_playerNotFound() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayer(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_returnCreatedPlayer_when_createPlayerWithNoTeams() {
        CreatePlayerRequest request = new CreatePlayerRequest("Charlie", "opposite", 14, 27, "195cm", List.of());
        Player entity = Player.builder().name("Charlie").role("opposite").numero(14).age(27).taille("195cm").build();
        PlayerDto dto = new PlayerDto(3L, "Charlie", "opposite", 14, 27, "195cm", List.of());
        when(playerMapper.toEntity(request)).thenReturn(entity);
        when(playerRepository.save(entity)).thenReturn(entity);
        when(playerMapper.toDto(entity)).thenReturn(dto);

        PlayerDto result = playerService.createPlayer(request);

        assertThat(result.name()).isEqualTo("Charlie");
        assertThat(result.numero()).isEqualTo(14);
        verify(playerRepository).save(entity);
    }

    @Test
    void should_updatePlayerName_when_nameChanges() {
        Player player = Player.builder().name("Old Name").role("libero").numero(7).age(25).taille("180cm").build();
        PlayerDto dto = new PlayerDto(1L, "New Name", "libero", 7, 25, "180cm", List.of());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));
        when(playerMapper.toDto(player)).thenReturn(dto);

        UpdatePlayerRequest request = new UpdatePlayerRequest("New Name", null, null, null, null, null);
        PlayerDto result = playerService.updatePlayer(1L, request);

        assertThat(player.getName()).isEqualTo("New Name");
        assertThat(result.name()).isEqualTo("New Name");
    }

    @Test
    void should_throwNoSuchElementException_when_updatePlayerNotFound() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.updatePlayer(99L, new UpdatePlayerRequest(null, null, null, null, null, null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_deletePlayer_when_playerExists() {
        when(playerRepository.existsById(1L)).thenReturn(true);

        playerService.deletePlayer(1L);

        verify(playerRepository).deleteById(1L);
    }

    @Test
    void should_throwNoSuchElementException_when_deletePlayerNotFound() {
        when(playerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> playerService.deletePlayer(99L))
                .isInstanceOf(NoSuchElementException.class);
    }
}