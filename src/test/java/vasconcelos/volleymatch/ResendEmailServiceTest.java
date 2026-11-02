package vasconcelos.volleymatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vasconcelos.volleymatch.service.ResendEmailService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class ResendEmailServiceTest {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private ResendEmailService service(boolean enabled, String testRecipient) {
        return new ResendEmailService(builder, enabled, "re_key", "noreply@test.com", testRecipient);
    }

    @Test
    void should_notThrow_when_resendRejectsApiKey() {
        server.expect(requestTo(RESEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());
        ResendEmailService resend = service(true, "");

        assertThatCode(() -> resend.sendVerificationEmail("user@test.com", "http://link"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void should_sendToUser_when_noTestRecipientConfigured() {
        server.expect(requestTo(RESEND_URL))
                .andExpect(jsonPath("$.to[0]").value("user@test.com"))
                .andRespond(withSuccess());

        service(true, "").sendVerificationEmail("user@test.com", "http://link");

        server.verify();
    }

    @Test
    void should_sendToTestRecipient_when_configured() {
        server.expect(requestTo(RESEND_URL))
                .andExpect(jsonPath("$.to[0]").value("dev@test.com"))
                .andRespond(withSuccess());

        service(true, "dev@test.com").sendVerificationEmail("user@test.com", "http://link");

        server.verify();
    }

    @Test
    void should_notCallResend_when_disabled() {
        service(false, "").sendVerificationEmail("user@test.com", "http://link");

        server.verify();
    }
}
