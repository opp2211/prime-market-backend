package ru.maltsev.primemarketbackend.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long start = System.nanoTime();
        Throwable error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            error = ex;
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String path = buildPath(request);
            int status = response.getStatus();
            if (error == null) {
                log.info("HTTP {} {} -> {} ({} ms)", request.getMethod(), path, status, durationMs);
            } else {
                log.warn(
                    "HTTP {} {} -> {} ({} ms) error={}",
                    request.getMethod(),
                    path,
                    status,
                    durationMs,
                    error.getClass().getSimpleName()
                );
            }
        }
    }

    private String buildPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return uri;
        }
        return uri + "?" + query;
    }
}
