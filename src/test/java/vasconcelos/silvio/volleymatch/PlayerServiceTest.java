package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.silvio.volleymatch.mapper.PlayerMapper;
import vasconcelos.silvio.volleymatch.model.player.Player;
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
    private PlayerMapper playerMapper;

    @InjectMocks
    private PlayerService playerService;

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