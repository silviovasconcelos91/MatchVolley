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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vasconcelos.volleymatch.exception.PayloadTooLargeException;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Caps live snapshot bodies at 1 MB (413). Rejects early on Content-Length and
 * guards chunked bodies by counting bytes while they are read.
 */
@Component
public class SnapshotBodySizeFilter extends OncePerRequestFilter {

    static final long MAX_BYTES = 1024L * 1024L;
    private static final String TOO_LARGE_MESSAGE = "Snapshot exceeds 1 MB";
    private static final Pattern SNAPSHOT_PATH = Pattern.compile("^/api/v1/live-sessions/[^/]+/snapshot$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.PUT.matches(request.getMethod())
                || !SNAPSHOT_PATH.matcher(request.getRequestURI()).matches();
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

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedInputStream(super.getInputStream());
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
