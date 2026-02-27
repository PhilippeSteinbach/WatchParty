package com.watchparty.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple sliding-window rate limiter for API requests.
 * Limits each IP to {@value MAX_REQUESTS_PER_WINDOW} requests per {@value WINDOW_SECONDS}s window.
 * <p>
 * Uses {@code request.getRemoteAddr()} which is correctly resolved by Tomcat's
 * {@code ForwardedHeaderFilter} when {@code server.forward-headers-strategy=native} is set.
 */
@Component
public class RateLimitingFilter implements Filter {

    private static final int MAX_REQUESTS_PER_WINDOW = 100;
    private static final int WINDOW_SECONDS = 60;

    private final Map<String, RateWindow> clients = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Only rate-limit API endpoints
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = httpRequest.getRemoteAddr();
        RateWindow window = clients.computeIfAbsent(clientIp, k -> new RateWindow());

        if (window.isAllowed()) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/problem+json");
            httpResponse.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded. Try again later."}""");
        }
    }

    /**
     * Evicts expired rate-limiting entries every 5 minutes to prevent unbounded memory growth.
     */
    @Scheduled(fixedRate = 300_000)
    void evictExpiredEntries() {
        long now = Instant.now().getEpochSecond();
        clients.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= WINDOW_SECONDS * 2L);
    }

    private static class RateWindow {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = Instant.now().getEpochSecond();

        boolean isAllowed() {
            long now = Instant.now().getEpochSecond();
            if (now - windowStart >= WINDOW_SECONDS) {
                synchronized (this) {
                    if (now - windowStart >= WINDOW_SECONDS) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;
        }
    }
}
