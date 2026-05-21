package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.player.CreatePlayerRequest;
import vasconcelos.volleymatch.dto.player.PlayerDto;
import vasconcelos.volleymatch.dto.player.UpdatePlayerRequest;
import vasconcelos.volleymatch.model.match.VolleyPosition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerControllerIT extends BaseIT {

    @Test
    void should_return200AndList_when_getAllPlayers() {
        ResponseEntity<ApiResponse> response = restTemplate.exchange("/api/v1/players", HttpMethod.GET, authEntity(), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(200);
    }

    @Test
    void should_return201AndCreatedPlayer_when_validRequestIsPosted() {
        CreatePlayerRequest request = new CreatePlayerRequest("Jean Dupont", List.of(VolleyPosition.Libero), 7, 25, "185cm", List.of());

        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players", HttpMethod.POST, authEntity(request),
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
                "/api/v1/players/" + playerId, HttpMethod.GET, authEntity(),
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
                "/api/v1/players/" + playerId, HttpMethod.PUT, authEntity(updateRequest),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().name()).isEqualTo("New Name");
    }

    @Test
    void should_return200_when_playerIsDeleted() {
        Long playerId = createPlayer("Delete Me", VolleyPosition.Pointu, 14);

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/players/" + playerId, HttpMethod.DELETE, authEntity(), ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(200);
    }

    private Long createPlayer(String name, VolleyPosition role, Integer numero) {
        CreatePlayerRequest request = new CreatePlayerRequest(name, List.of(role), numero, 24, "182cm", List.of());
        ResponseEntity<ApiResponse<PlayerDto>> response = restTemplate.exchange(
                "/api/v1/players", HttpMethod.POST, authEntity(request),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }
}
