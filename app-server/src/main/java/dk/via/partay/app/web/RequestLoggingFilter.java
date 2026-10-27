package dk.via.partay.app.web;

import dk.via.partay.app.trace.TraceRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Logs every API call (REST in, REST out with status and time) and starts/finishes its trace.
 * Swagger, static files and /api/debug are skipped. Requests with X-Partay-Poll: 1 are logged
 * but not traced, so page refreshes do not flood the demo log.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final TraceRecorder traces;

    public RequestLoggingFilter(TraceRecorder traces) {
        this.traces = traces;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/debug");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        boolean traced = !"1".equals(request.getHeader("X-Partay-Poll"));
        long start = System.nanoTime();
        log.info("[T2] REST <- {} {}", method, path);
        if (traced) {
            traces.start(method, path, request.getHeader("X-Guest-Id"));
        }
        // Hold the body back until the trace is stored, so a client that reads /api/debug/traces
        // right after the response always finds it.
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("[T2] REST -> {} {} {}  ({} ms)", wrapped.getStatus(), method, path, ms);
            if (traced) {
                traces.finish(wrapped.getStatus());
            }
            traces.clearCurrent();
            wrapped.copyBodyToResponse();
        }
    }
}
