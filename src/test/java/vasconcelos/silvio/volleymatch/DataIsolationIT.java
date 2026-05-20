package vasconcelos.silvio.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vasconcelos.silvio.volleymatch.dto.auth.AuthResponse;
import vasconcelos.silvio.volleymatch.dto.auth.LoginRequest;
import vasconcelos.silvio.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.silvio.volleymatch.dto.common.ApiResponse;
import vasconcelos.silvio.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.silvio.volleymatch.dto.team.TeamDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataIsolationIT extends BaseIT {

    @Test
    void should_notSeeOtherUsersTeams_when_queryingTeams() {
        String tokenA = registerAndLogin("iso_user_a@test.com", "isousera", "pass123");
        String tokenB = registerAndLogin("iso_user_b@test.com", "isouserb", "pass123");

        restTemplate.exchange("/api/v1/teams", HttpMethod.POST,
                authEntity(new CreateTeamRequest("Team A Only", "Paris", "blue"), tokenA),
                new ParameterizedTypeReference<ApiResponse<TeamDto>>() {});

        ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = restTemplate.exchange(
                "/api/v1/teams", HttpMethod.GET,
                new HttpEntity<>(buildAuthHeaders(tokenB)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> teams = response.getBody().data();
        assertThat(teams).noneMatch(t -> "Team A Only".equals(t.get("name")));
    }

    @Test
    void should_return404_when_accessingOtherUsersTeam() {
        String tokenA = registerAndLogin("iso_owner_a@test.com", "isoownera", "pass123");
        String tokenB = registerAndLogin("iso_owner_b@test.com", "isoownerb", "pass123");

        ResponseEntity<ApiResponse<TeamDto>> createResponse = restTemplate.exchange(
                "/api/v1/teams", HttpMethod.POST,
                authEntity(new CreateTeamRequest("Private Team", "Lyon", "red"), tokenA),
                new ParameterizedTypeReference<>() {});

        Long teamId = createResponse.getBody().data().id();

        ResponseEntity<ApiResponse<TeamDto>> response = restTemplate.exchange(
                "/api/v1/teams/" + teamId, HttpMethod.GET,
                new HttpEntity<>(buildAuthHeaders(tokenB)),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String registerAndLogin(String email, String pseudo, String password) {
        restTemplate.postForEntity("/api/v1/auth:register",
                new RegisterRequest(email, pseudo, password),
                ApiResponse.class);

        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth:login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, password)),
                new ParameterizedTypeReference<>() {});

        return resp.getBody().data().accessToken();
    }

    private HttpHeaders buildAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private <T> HttpEntity<T> authEntity(T body, String token) {
        return new HttpEntity<>(body, buildAuthHeaders(token));
    }
}
