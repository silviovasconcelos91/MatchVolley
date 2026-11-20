package vasconcelos.volleymatch;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vasconcelos.volleymatch.config.LivePayloadSizeFilter;
import vasconcelos.volleymatch.exception.PayloadTooLargeException;

import java.io.BufferedReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LivePayloadSizeFilterTest {

    private static final int ONE_MEGABYTE = 1024 * 1024;

    private final ExposedFilter filter = new ExposedFilter();

    @ParameterizedTest
    @CsvSource({
            "PUT, /api/v1/live-sessions/0b6f6c1e-1a2b-4c3d-9e8f-001122334455/snapshot",
            "POST, /api/v1/matches/1749131100000/live-stats"
    })
    void should_filter_when_request_is_live_payload_upload(String method, String uri) {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest(method, uri))).isFalse();
    }

    /** Percent-encoded variants that Spring MVC decodes and routes to the same handlers (CRIT-001). */
    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/matches/m1/live-st%61ts",
            "POST, /api/v1/matches/m1/%6Cive-stats",
            "POST, /api/v1/matches/m1/%6cive-stats",
            "POST, /api/v%31/%6D%61tches/m1/live-stats",
            "POST, /api/v1/matches/live%20match/live-stats",
            "POST, /api/v1/matches/m1/live-stats;jsessionid=abc",
            "PUT, /api/v1/live-sessions/abc/sn%61pshot",
            "PUT, /api/v1/live-sessions/abc/%73napshot"
    })
    void should_filter_when_live_payload_upload_path_is_percent_encoded(String method, String uri) {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest(method, uri))).isFalse();
    }

    @Test
    void should_filter_when_application_has_context_path() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ctx/api/v1/matches/m1/live-stats");
        request.setContextPath("/ctx");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/matches/1749131100000/live-stats",
            "PUT, /api/v1/matches/1749131100000/live-stats",
            "GET, /api/v1/matches/1749131100000/live-stats/analysis",
            "POST, /api/v1/matches/1749131100000/live-stats/analysis",
            "POST, /api/v1/teams",
            "PUT, /api/v1/teams/1",
            "POST, /api/v1/auth:login",
            "GET, /api/v1/live-sessions/0b6f6c1e-1a2b-4c3d-9e8f-001122334455/snapshot",
            "POST, /api/v1/live-sessions/0b6f6c1e-1a2b-4c3d-9e8f-001122334455/snapshot",
            "POST, /api/v1/matches/a/b/live-stats"
    })
    void should_not_filter_when_request_is_not_live_payload_upload(String method, String uri) {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest(method, uri))).isTrue();
    }

    @Test
    void should_return413_envelope_without_calling_chain_when_content_length_exceeds_one_megabyte() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/matches/m1/live-stats");
        request.setContent(new byte[ONE_MEGABYTE + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"data\":null,\"message\":\"Request body exceeds 1 MB\",\"status\":413}");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_pass_request_through_when_content_length_is_exactly_one_megabyte() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/matches/m1/live-stats");
        request.setContent(new byte[ONE_MEGABYTE]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes()).hasSize(ONE_MEGABYTE);
    }

    @Test
    void should_throw_payload_too_large_when_chunked_body_read_exceeds_one_megabyte() throws Exception {
        MockHttpServletRequest request = new ChunkedRequest("POST", "/api/v1/matches/m1/live-stats");
        request.setContent(new byte[ONE_MEGABYTE + 1]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);
        ServletInputStream body = ((HttpServletRequest) chain.getRequest()).getInputStream();

        assertThatThrownBy(body::readAllBytes)
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("Request body exceeds 1 MB");
    }

    @Test
    void should_throw_payload_too_large_when_chunked_body_read_through_reader_exceeds_one_megabyte() throws Exception {
        MockHttpServletRequest request = new ChunkedRequest("POST", "/api/v1/matches/m1/live-stats");
        request.setContent(new byte[ONE_MEGABYTE + 1]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);
        BufferedReader reader = ((HttpServletRequest) chain.getRequest()).getReader();

        assertThatThrownBy(() -> reader.transferTo(Writer.nullWriter()))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("Request body exceeds 1 MB");
    }

    @Test
    void should_keep_counting_across_calls_when_input_stream_is_requested_twice() throws Exception {
        MockHttpServletRequest request = new ChunkedRequest("POST", "/api/v1/matches/m1/live-stats");
        request.setContent(new byte[ONE_MEGABYTE + 1]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);
        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getInputStream().readNBytes(ONE_MEGABYTE / 2)).hasSize(ONE_MEGABYTE / 2);
        ServletInputStream second = wrapped.getInputStream();

        assertThatThrownBy(second::readAllBytes)
                .isInstanceOf(PayloadTooLargeException.class);
    }

    /** Exposes the protected {@code shouldNotFilter} to the test. */
    private static final class ExposedFilter extends LivePayloadSizeFilter {
        @Override
        public boolean shouldNotFilter(HttpServletRequest request) {
            return super.shouldNotFilter(request);
        }
    }

    /** Request whose Content-Length is unknown, as with Transfer-Encoding: chunked. */
    private static final class ChunkedRequest extends MockHttpServletRequest {
        private ChunkedRequest(String method, String uri) {
            super(method, uri);
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public int getContentLength() {
            return -1;
        }
    }
}
