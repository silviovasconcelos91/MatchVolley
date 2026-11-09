package vasconcelos.volleymatch;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import vasconcelos.volleymatch.dto.auth.AuthResponse;
import vasconcelos.volleymatch.dto.auth.LoginRequest;
import vasconcelos.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.volleymatch.dto.common.ApiResponse;
import vasconcelos.volleymatch.dto.live.LiveStatsRequest;
import vasconcelos.volleymatch.dto.livesession.CreateLiveSessionRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSessionDto;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotRequest;
import vasconcelos.volleymatch.dto.livesession.LiveSnapshotResponse;
import vasconcelos.volleymatch.dto.team.CreateTeamRequest;
import vasconcelos.volleymatch.dto.team.TeamDto;

/**
 * Shared HTTP helpers for live session integration tests.
 */
abstract class LiveSessionITSupport extends BaseIT {

    protected Long createTeam(String name, String token) {
        ResponseEntity<ApiResponse<TeamDto>> team = restTemplate.exchange(
                "/api/v1/teams", HttpMethod.POST,
                new HttpEntity<>(new CreateTeamRequest(name, "Lyon", "#000000"), bearer(token)),
                new ParameterizedTypeReference<>() {});
        return team.getBody().data().id();
    }

    protected LiveSessionDto createSession(Long teamId, boolean showPlayerNames) {
        ResponseEntity<ApiResponse<LiveSessionDto>> response = restTemplate.exchange(
                "/api/v1/live-sessions", HttpMethod.POST,
                authEntity(new CreateLiveSessionRequest(teamId, "Les Lynx vs Grenoble", "home", showPlayerNames)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data();
    }

    protected ResponseEntity<ApiResponse<LiveSnapshotResponse>> putSnapshot(LiveSessionDto session, long revision,
                                                                           LiveStatsRequest payload, String token) {
        return restTemplate.exchange(
                "/api/v1/live-sessions/" + session.sessionId() + "/snapshot", HttpMethod.PUT,
                new HttpEntity<>(new LiveSnapshotRequest(revision, payload), bearer(token)),
                new ParameterizedTypeReference<>() {});
    }

    protected String registerAndLogin(String email, String pseudo) {
        restTemplate.postForEntity("/api/v1/auth:register", new RegisterRequest(email, pseudo, "pass123"), ApiResponse.class);
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth:login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, "pass123")),
                new ParameterizedTypeReference<>() {});
        return resp.getBody().data().accessToken();
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
