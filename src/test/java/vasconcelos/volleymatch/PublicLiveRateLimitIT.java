package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.live.rate-limit-per-minute=3")
class PublicLiveRateLimitIT extends BaseIT {

    private ResponseEntity<String> get(String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "198.51.100.1, " + ip);
        return restTemplate.exchange("/api/v1/public/live/ZZZZZZ", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void should_return429_when_ip_exceeds_limit() {
        get("192.0.2.10");
        get("192.0.2.10");
        get("192.0.2.10");

        ResponseEntity<String> blocked = get("192.0.2.10");
        ResponseEntity<String> otherIp = get("192.0.2.11");

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(blocked.getBody()).contains("\"status\":429");
        assertThat(otherIp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
