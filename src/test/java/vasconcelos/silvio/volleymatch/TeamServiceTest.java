package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.silvio.volleymatch.dto.team.AssignPlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.RemovePlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;
import vasconcelos.silvio.volleymatch.mapper.TeamMapper;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;
import vasconcelos.silvio.volleymatch.service.TeamService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TeamMapper teamMapper;

    @InjectMocks
    private TeamService teamService;

    @Test
    void should_returnAllTeams_when_getAllTeams() {
        Team team = Team.builder().name("Paris Volley").city("Paris").logoColor("blue").build();
        TeamDto dto = new TeamDto(1L, "Paris Volley", "Paris", "blue", List.of());
        when(teamRepository.findAll()).thenReturn(List.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        List<TeamDto> result = teamService.getAllTeams();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Paris Volley");
    }

    @Test
    void should_returnTeam_when_teamExists() {
        Team team = Team.builder().name("Lyon").city("Lyon").logoColor("red").build();
        TeamDto dto = new TeamDto(1L, "Lyon", "Lyon", "red", List.of());
        when(teamRepository.findById(1L)).thenReturn(Optional.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        TeamDto result = teamService.getTeam(1L);

        assertThat(result.name()).isEqualTo("Lyon");
    }

    @Test
    void should_throwNoSuchElementException_when_teamNotFound() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeam(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_returnCreatedTeam_when_createTeam() {
        CreateTeamRequest request = new CreateTeamRequest("Nice Volley", "Nice", "green");
        Team entity = Team.builder().name("Nice Volley").city("Nice").logoColor("green").build();
        TeamDto dto = new TeamDto(1L, "Nice Volley", "Nice", "green", List.of());
        when(teamMapper.toEntity(request)).thenReturn(entity);
        when(teamRepository.save(entity)).thenReturn(entity);
        when(teamMapper.toDto(entity)).thenReturn(dto);

        TeamDto result = teamService.createTeam(request);

        assertThat(result.name()).isEqualTo("Nice Volley");
        verify(teamRepository).save(entity);
    }

    @Test
    void should_addPlayerToTeam_when_assignPlayers() {
        Team team = Team.builder().name("Bordeaux").city("Bordeaux").logoColor("white").build();
        Player player = Player.builder().name("Alice").role("libero").numero(7).age(22).taille("172cm").build();
        TeamDto dto = new TeamDto(2L, "Bordeaux", "Bordeaux", "white", List.of());
        when(teamRepository.findById(2L)).thenReturn(Optional.of(team));
        when(playerRepository.findAllById(List.of(10L))).thenReturn(List.of(player));
        when(teamRepository.findById(2L)).thenReturn(Optional.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        AssignPlayersRequest request = new AssignPlayersRequest(List.of(10L));
        TeamDto result = teamService.assignPlayers(2L, request);

        verify(playerRepository).saveAll(List.of(player));
        assertThat(result).isNotNull();
    }

    @Test
    void should_throwNoSuchElementException_when_assignPlayersTeamNotFound() {
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.assignPlayers(99L, new AssignPlayersRequest(List.of(1L))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_removePlayerFromTeam_when_removePlayers() {
        Team team = Team.builder().name("Toulouse").city("Toulouse").logoColor("violet").build();
        Player player = Player.builder().name("Bob").role("setter").numero(1).age(26).taille("190cm").build();
        player.addTeam(team);
        TeamDto dto = new TeamDto(3L, "Toulouse", "Toulouse", "violet", List.of());
        when(teamRepository.findById(3L)).thenReturn(Optional.of(team));
        when(playerRepository.findAllById(List.of(20L))).thenReturn(List.of(player));
        when(teamRepository.findById(3L)).thenReturn(Optional.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        RemovePlayersRequest request = new RemovePlayersRequest(List.of(20L));
        teamService.removePlayers(3L, request);

        verify(playerRepository).saveAll(List.of(player));
    }
}