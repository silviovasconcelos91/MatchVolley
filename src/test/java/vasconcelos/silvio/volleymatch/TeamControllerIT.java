package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import vasconcelos.silvio.volleymatch.dto.common.ApiResponse;
import vasconcelos.silvio.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.silvio.volleymatch.dto.player.PlayerDto;
import vasconcelos.silvio.volleymatch.dto.team.AssignPlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.RemovePlayersRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeamControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return200AndList_when_getAllTeams() {
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity("/api/v1/teams", ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(200);
    }

    @Test
    void should_return201AndCreatedTeam_when_validRequestIsPosted() {
        CreateTeamRequest request = new CreateTeamRequest("Volley Club", "Paris", "blue");

        ResponseEntity<ApiResponse<TeamDto>> response = restTemplate.exchange(
                "/api/v1/teams", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TeamDto team = response.getBody().data();
        assertThat(team.id()).isNotNull();
        assertThat(team.name()).isEqualTo("Volley Club");
        assertThat(team.city()).isEqualTo("Paris");
    }

    @Test
    void should_return200AndTeam_when_teamExists() {
        Long teamId = createTeam("Lyon Volley", "Lyon");

        ResponseEntity<ApiResponse<TeamDto>> response = restTemplate.exchange(
                "/api/v1/teams/" + teamId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isEqualTo(teamId);
        assertThat(response.getBody().data().name()).isEqualTo("Lyon Volley");
    }

    @Test
    void should_return200AndTeamWithPlayer_when_playerIsAssigned() {
        Long teamId = createTeam("Assign Team", "Bordeaux");
        Long playerId = createPlayer("Player A", 11);

        AssignPlayersRequest request = new AssignPlayersRequest(List.of(playerId));
        ResponseEntity<ApiResponse<TeamDto>> response = restTemplate.exchange(
                "/api/v1/teams/" + teamId + ":assignPlayers", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().players()).hasSize(1);
        assertThat(response.getBody().data().players().get(0).name()).isEqualTo("Player A");
    }

    @Test
    void should_return200AndTeamWithoutPlayer_when_playerIsRemoved() {
        Long teamId = createTeam("Remove Team", "Marseille");
        Long playerId = createPlayer("Player B", 22);

        restTemplate.exchange("/api/v1/teams/" + teamId + ":assignPlayers", HttpMethod.POST,
                new HttpEntity<>(new AssignPlayersRequest(List.of(playerId))),
                new ParameterizedTypeReference<ApiResponse<TeamDto>>() {});

        RemovePlayersRequest removeRequest = new RemovePlayersRequest(List.of(playerId));
        ResponseEntity<ApiResponse<TeamDto>> response = restTemplate.exchange(
                "/api/v1/teams/" + teamId + ":removePlayers", HttpMethod.POST, new HttpEntity<>(removeRequest),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().players()).isEmpty();
    }

    private Long createTeam(String name, String city) {
        ResponseEntity<ApiResponse<TeamDto>> response = restTemplate.exchange(
                "/api/v1/teams", HttpMethod.POST, new HttpEntity<>(new CreateTeamRequest(name, city, "red")),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createPlayer(String name, Integer numero) {
        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players", HttpMethod.POST,
                new HttpEntity<>(new CreatePlayerRequest(name, "libero", numero, 24, "180cm", List.of())),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }
}
