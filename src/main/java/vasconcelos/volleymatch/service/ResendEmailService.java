package vasconcelos.volleymatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;

    public ResendEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    public void sendVerificationEmail(String to, String verifyUrl) {
        Map<String, Object> body = Map.of(
                "from", fromEmail,
                "to", List.of(to),
                "subject", "Verify your MatchVolley account",
                "html", "<p>Click <a href='" + verifyUrl + "'>here</a> to verify your account.</p>"
                        + "<p>This link expires in 24 hours.</p>"
        );
        restClient.post()
                .uri("https://api.resend.com/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}