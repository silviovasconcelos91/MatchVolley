package vasconcelos.volleymatch.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import vasconcelos.volleymatch.exception.PayloadTooLargeException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Caps the bodies of the two live payload uploads at 1 MB (413): the live snapshot
 * ({@code PUT /live-sessions/{id}/snapshot}) and the final match submission
 * ({@code POST /matches/{matchId}/live-stats}). Both carry the same payload contract.
 * Rejects early on Content-Length and guards chunked bodies by counting bytes while they are read.
 * <p>
 * Routes are matched like Spring MVC does ({@link PathPattern} on the percent-decoded path within the
 * application), so an encoded variant such as {@code /live-st%61ts}, which MVC routes to the same handler,
 * cannot bypass the limit.
 */
@Component
public class LivePayloadSizeFilter extends OncePerRequestFilter {

    static final long MAX_BYTES = 1024L * 1024L;
    private static final String TOO_LARGE_MESSAGE = "Request body exceeds 1 MB";
    private static final PathPattern SNAPSHOT_PATH =
            PathPatternParser.defaultInstance.parse("/api/v1/live-sessions/{sessionId}/snapshot");
    private static final PathPattern LIVE_STATS_PATH =
            PathPatternParser.defaultInstance.parse("/api/v1/matches/{matchId}/live-stats");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        PathPattern upload;
        if (HttpMethod.PUT.matches(method)) {
            upload = SNAPSHOT_PATH;
        } else if (HttpMethod.POST.matches(method)) {
            upload = LIVE_STATS_PATH;
        } else {
            return true;
        }
        PathContainer path = RequestPath.parse(request.getRequestURI(), request.getContextPath())
                .pathWithinApplication();
        return !upload.matches(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"data\":null,\"message\":\"" + TOO_LARGE_MESSAGE + "\",\"status\":413}");
            return;
        }
        filterChain.doFilter(new LimitedRequest(request), response);
    }

    /**
     * Exposes a single byte counter for the whole request: the stream is created once, and the reader
     * decodes that same stream, so neither a second {@code getInputStream()} nor {@code getReader()} resets it.
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private LimitedInputStream stream;
        private BufferedReader reader;

        private LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream());
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long total;

        private LimitedInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = delegate.read(buf, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) {
            total += n;
            if (total > MAX_BYTES) {
                throw new PayloadTooLargeException(TOO_LARGE_MESSAGE);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
