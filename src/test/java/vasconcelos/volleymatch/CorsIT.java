package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class CorsIT {

    @Autowired
    private TestRestTemplate restTemplate;

    private ResponseEntity<String> preflight(String path, String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(origin);
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        headers.setAccessControlRequestHeaders(List.of("Authorization", "Content-Type"));
        return restTemplate.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    }

    @Test
    void should_allowPreflight_when_originIsLocalhost() {
        ResponseEntity<String> resp = preflight("/api/v1/players", "http://localhost:5173");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(resp.getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    void should_rejectPreflight_when_originIsNotAllowed() {
        ResponseEntity<String> resp = preflight("/api/v1/players", "http://evil.example.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
}
