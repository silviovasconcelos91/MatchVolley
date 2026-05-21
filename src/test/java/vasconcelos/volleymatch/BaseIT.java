package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import vasconcelos.volleymatch.dto.auth.AuthResponse;
import vasconcelos.volleymatch.dto.auth.LoginRequest;
import vasconcelos.volleymatch.dto.auth.RegisterRequest;
import vasconcelos.volleymatch.dto.common.ApiResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
public abstract class BaseIT {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String authToken;

    @BeforeEach
    void setUpAuth() {
        // Register (ignore 409 if already exists from previous test in same context)
        restTemplate.postForEntity("/api/v1/auth:register",
                new RegisterRequest("it_user@test.com", "ituser", "pass123"),
                ApiResponse.class);

        // Always login to get fresh token
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth:login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("it_user@test.com", "pass123")),
                new ParameterizedTypeReference<>() {});
        authToken = resp.getBody().data().accessToken();
    }

    protected HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return headers;
    }

    protected <T> HttpEntity<T> authEntity(T body) {
        return new HttpEntity<>(body, authHeaders());
    }

    protected HttpEntity<Void> authEntity() {
        return new HttpEntity<>(authHeaders());
    }
}
