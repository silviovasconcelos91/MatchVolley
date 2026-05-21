package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import vasconcelos.volleymatch.dto.auth.AuthResponse;
import vasconcelos.volleymatch.dto.auth.LoginRequest;
import vasconcelos.volleymatch.dto.auth.RefreshRequest;
import vasconcelos.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.volleymatch.dto.auth.UserResponse;
import vasconcelos.volleymatch.dto.common.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return201AndUser_when_registerWithValidData() {
        RegisterRequest req = new RegisterRequest("auth_it_1@test.com", "authuser1", "pass123");
        ResponseEntity<ApiResponse<UserResponse>> resp = restTemplate.exchange(
                "/api/v1/auth:register", HttpMethod.POST, new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().data().email()).isEqualTo("auth_it_1@test.com");
        assertThat(resp.getBody().data().id()).isNotNull();
    }

    @Test
    void should_return409_when_emailAlreadyExists() {
        RegisterRequest req = new RegisterRequest("dup_email@test.com", "dupuser", "pass123");
        restTemplate.postForEntity("/api/v1/auth:register", req, ApiResponse.class);
        RegisterRequest dup = new RegisterRequest("dup_email@test.com", "otheruser", "pass123");
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity("/api/v1/auth:register", dup, ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void should_return200AndTokens_when_loginWithValidCredentials() {
        restTemplate.postForEntity("/api/v1/auth:register",
                new RegisterRequest("login_it@test.com", "loginuser", "pass123"), ApiResponse.class);
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth:login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("login_it@test.com", "pass123")),
                new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().accessToken()).isNotBlank();
        assertThat(resp.getBody().data().refreshToken()).isNotBlank();
    }

    @Test
    void should_return200AndNewTokens_when_refreshWithValidToken() {
        restTemplate.postForEntity("/api/v1/auth:register",
                new RegisterRequest("refresh_it@test.com", "refreshuser", "pass123"), ApiResponse.class);
        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restTemplate.exchange(
                "/api/v1/auth:login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("refresh_it@test.com", "pass123")),
                new ParameterizedTypeReference<>() {});
        String refreshToken = loginResp.getBody().data().refreshToken();

        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth:refresh", HttpMethod.POST,
                new HttpEntity<>(new RefreshRequest(refreshToken)),
                new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().accessToken()).isNotBlank();
        assertThat(resp.getBody().data().refreshToken()).isNotEqualTo(refreshToken);
    }

    @Test
    void should_return400AndSameMessage_when_loginWithWrongPassword() {
        restTemplate.postForEntity("/api/v1/auth:register",
                new RegisterRequest("badpwd_it@test.com", "badpwduser", "pass123"), ApiResponse.class);
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                "/api/v1/auth:login", new LoginRequest("badpwd_it@test.com", "wrongpassword"), ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("Mauvais email ou mdp");
    }

    @Test
    void should_return400AndSameMessage_when_loginWithUnknownEmail() {
        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                "/api/v1/auth:login", new LoginRequest("nobody@nowhere.com", "pass123"), ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().message()).isEqualTo("Mauvais email ou mdp");
    }

    @Test
    void should_return200_when_logout() {
        restTemplate.postForEntity("/api/v1/auth:register",
                new RegisterRequest("logout_it@test.com", "logoutuser", "pass123"), ApiResponse.class);
        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restTemplate.exchange(
                "/api/v1/auth:login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("logout_it@test.com", "pass123")),
                new ParameterizedTypeReference<>() {});
        String refreshToken = loginResp.getBody().data().refreshToken();

        ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                "/api/v1/auth:logout", new RefreshRequest(refreshToken), ApiResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
