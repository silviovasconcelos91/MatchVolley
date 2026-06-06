package vasconcelos.volleymatch.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;
    private final String fromEmail;
    private final String testRecipient;

    public ResendEmailService(
            RestClient.Builder restClientBuilder,
            @Value("${resend.enabled:true}") boolean enabled,
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from-email}") String fromEmail,
            @Value("${resend.test-recipient:}") String testRecipient) {
        this.restClient = restClientBuilder.build();
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.testRecipient = testRecipient;
    }

    public void sendVerificationEmail(String to, String verifyUrl) {
        if (!enabled) {
            log.info("Email disabled — skipping verification email to {}", to);
            return;
        }
        String recipient = testRecipient.isBlank() ? to : testRecipient;
        Map<String, Object> body = Map.of(
                "from", fromEmail,
                "to", List.of(recipient),
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

    public boolean isEnabled() {
        return enabled;
    }
}