package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vasconcelos.silvio.volleymatch.dto.match.MatchDto;
import vasconcelos.silvio.volleymatch.dto.team.AssignPlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.RemovePlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;
import vasconcelos.silvio.volleymatch.mapper.MatchStatMapper;
import vasconcelos.silvio.volleymatch.mapper.TeamMapper;
import vasconcelos.silvio.volleymatch.model.match.Match;
import vasconcelos.silvio.volleymatch.model.match.MatchResult;
import vasconcelos.silvio.volleymatch.model.match.VolleyPosition;
import vasconcelos.silvio.volleymatch.model.player.Player;
import vasconcelos.silvio.volleymatch.model.team.Team;
import vasconcelos.silvio.volleymatch.model.user.AppUser;
import vasconcelos.silvio.volleymatch.repository.MatchRepository;
import vasconcelos.silvio.volleymatch.repository.PlayerRepository;
import vasconcelos.silvio.volleymatch.repository.TeamRepository;
import vasconcelos.silvio.volleymatch.service.AuthService;
import vasconcelos.silvio.volleymatch.service.TeamService;

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
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamMapper teamMapper;

    @Mock
    private MatchStatMapper matchStatMapper;

    @Mock
    private AuthService authService;

    private AppUser testUser;

    @InjectMocks
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder()
                .email("test@test.com").pseudo("test").password("hashed").build();
        when(authService.getCurrentUser()).thenReturn(testUser);
    }

    @Test
    void should_returnMatches_when_teamExists() {
        Match match = Match.builder()
                .id("match-1").teamId(1L).date(LocalDate.of(2025, 10, 5))
                .result(MatchResult.WON).mySets(3).oppSets(1).build();
        MatchDto dto = new MatchDto("match-1", 1L, null, "2025/2026", null,
                null, null, LocalDate.of(2025, 10, 5), "WON", 3, 1);
        when(teamRepository.existsByIdAndUser(1L, testUser)).thenReturn(true);
        when(matchRepository.findByTeamIdAndUser(1L, testUser)).thenReturn(List.of(match));
        when(matchStatMapper.toMatchDto(match)).thenReturn(dto);

        List<MatchDto> result = teamService.getMatchesByTeam(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("match-1");
        assertThat(result.getFirst().result()).isEqualTo("WON");
    }

    @Test
    void should_returnEmptyList_when_teamHasNoMatches() {
        when(teamRepository.existsByIdAndUser(2L, testUser)).thenReturn(true);
        when(matchRepository.findByTeamIdAndUser(2L, testUser)).thenReturn(List.of());

        List<MatchDto> result = teamService.getMatchesByTeam(2L);

        assertThat(result).isEmpty();
    }

    @Test
    void should_throwNoSuchElementException_when_getMatchesByTeamNotFound() {
        when(teamRepository.existsByIdAndUser(99L, testUser)).thenReturn(false);

        assertThatThrownBy(() -> teamService.getMatchesByTeam(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void should_returnAllTeams_when_getAllTeams() {
        Team team = Team.builder().name("Paris Volley").city("Paris").logoColor("blue").build();
        TeamDto dto = new TeamDto(1L, "Paris Volley", "Paris", "blue", List.of());
        when(teamRepository.findAllByUser(testUser)).thenReturn(List.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        List<TeamDto> result = teamService.getAllTeams();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Paris Volley");
    }

    @Test
    void should_returnTeam_when_teamExists() {
        Team team = Team.builder().name("Lyon").city("Lyon").logoColor("red").build();
        TeamDto dto = new TeamDto(1L, "Lyon", "Lyon", "red", List.of());
        when(teamRepository.findByIdAndUser(1L, testUser)).thenReturn(Optional.of(team));
        when(teamMapper.toDto(team)).thenReturn(dto);

        TeamDto result = teamService.getTeam(1L);

        assertThat(result.name()).isEqualTo("Lyon");
    }

    @Test
    void should_throwNoSuchElementException_when_teamNotFound() {
        when(teamRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

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
        when(teamRepository.save(any())).thenReturn(entity);
        when(teamMapper.toDto(entity)).thenReturn(dto);

        TeamDto result = teamService.createTeam(request);

        assertThat(result.name()).isEqualTo("Nice Volley");
        verify(teamRepository).save(any());
    }

    @Test
    void should_addPlayerToTeam_when_assignPlayers() {
        Team team = Team.builder().name("Bordeaux").city("Bordeaux").logoColor("white").build();
        Player player = Player.builder().name("Alice").roles(List.of(VolleyPosition.Libero)).numero(7).age(22).taille("172cm").build();
        TeamDto dto = new TeamDto(2L, "Bordeaux", "Bordeaux", "white", List.of());
        when(teamRepository.findByIdAndUser(2L, testUser)).thenReturn(Optional.of(team));
        when(playerRepository.findAllByIdInAndUser(List.of(10L), testUser)).thenReturn(List.of(player));
        when(teamMapper.toDto(team)).thenReturn(dto);

        AssignPlayersRequest request = new AssignPlayersRequest(List.of(10L));
        TeamDto result = teamService.assignPlayers(2L, request);

        verify(playerRepository).saveAll(List.of(player));
        assertThat(result).isNotNull();
    }

    @Test
    void should_throwNoSuchElementException_when_assignPlayersTeamNotFound() {
        when(teamRepository.findByIdAndUser(99L, testUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.assignPlayers(99L, new AssignPlayersRequest(List.of(1L))))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void should_removePlayerFromTeam_when_removePlayers() {
        Team team = Team.builder().name("Toulouse").city("Toulouse").logoColor("violet").build();
        Player player = Player.builder().name("Bob").roles(List.of(VolleyPosition.Passeur)).numero(1).age(26).taille("190cm").build();
        player.addTeam(team);
        TeamDto dto = new TeamDto(3L, "Toulouse", "Toulouse", "violet", List.of());
        when(teamRepository.findByIdAndUser(3L, testUser)).thenReturn(Optional.of(team));
        when(playerRepository.findAllByIdInAndUser(List.of(20L), testUser)).thenReturn(List.of(player));
        when(teamMapper.toDto(team)).thenReturn(dto);

        RemovePlayersRequest request = new RemovePlayersRequest(List.of(20L));
        teamService.removePlayers(3L, request);

        verify(playerRepository).saveAll(List.of(player));
    }
}
