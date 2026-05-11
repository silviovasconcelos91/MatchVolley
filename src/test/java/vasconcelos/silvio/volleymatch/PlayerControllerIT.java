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
import vasconcelos.silvio.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.silvio.volleymatch.model.match.VolleyPosition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlayerControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return200AndList_when_getAllPlayers() {
        ResponseEntity<ApiResponse> response = restTemplate.getForEntity("/api/v1/players", ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(200);
    }

    @Test
    void should_return201AndCreatedPlayer_when_validRequestIsPosted() {
        CreatePlayerRequest request = new CreatePlayerRequest("Jean Dupont", List.of(VolleyPosition.Libero), 7, 25, "185cm", List.of());

        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PlayerDto player = response.getBody().data();
        assertThat(player.id()).isNotNull();
        assertThat(player.name()).isEqualTo("Jean Dupont");
        assertThat(player.roles()).containsExactly(VolleyPosition.Libero);
        assertThat(player.numero()).isEqualTo(7);
    }

    @Test
    void should_return200AndPlayer_when_playerExists() {
        Long playerId = createPlayer("Marc Martin", VolleyPosition.Passeur, 1);

        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players/" + playerId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isEqualTo(playerId);
        assertThat(response.getBody().data().name()).isEqualTo("Marc Martin");
    }

    @Test
    void should_return200AndUpdatedName_when_validUpdateIsPosted() {
        Long playerId = createPlayer("Old Name", VolleyPosition.Libero, 9);

        UpdatePlayerRequest updateRequest = new UpdatePlayerRequest("New Name", null, null, null, null, null);
        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players/" + playerId, HttpMethod.PUT, new HttpEntity<>(updateRequest),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().name()).isEqualTo("New Name");
    }

    @Test
    void should_return200_when_playerIsDeleted() {
        Long playerId = createPlayer("Delete Me", VolleyPosition.Pointu, 14);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/players/" + playerId, HttpMethod.DELETE, null, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(200);
    }

    private Long createPlayer(String name, VolleyPosition role, Integer numero) {
        CreatePlayerRequest request = new CreatePlayerRequest(name, List.of(role), numero, 24, "182cm", List.of());
        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }
}
