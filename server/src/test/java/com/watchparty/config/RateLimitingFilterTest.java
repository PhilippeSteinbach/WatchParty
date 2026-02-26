package com.watchparty.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        chain = mock(FilterChain.class);
    }

    @Test
    void whenUnderLimitThenRequestPassesThrough() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/rooms/abc");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void whenNonApiPathThenAlwaysPassesThrough() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/ws/info");
        var response = new MockHttpServletResponse();

        // Even after many requests, non-API paths should pass through
        for (int i = 0; i < 200; i++) {
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
        }

        assertEquals(200, response.getStatus());
    }

    @Test
    void whenOverLimitThenReturns429() throws ServletException, IOException {
        // Exhaust the rate limit
        for (int i = 0; i < 100; i++) {
            var request = new MockHttpServletRequest("GET", "/api/rooms");
            request.setRemoteAddr("192.168.1.1");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
        }

        // Next request should be rate limited
        var request = new MockHttpServletRequest("GET", "/api/rooms");
        request.setRemoteAddr("192.168.1.1");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
    }

    @Test
    void whenDifferentIpsThenIndependentLimits() throws ServletException, IOException {
        // Exhaust limit for IP 1
        for (int i = 0; i < 100; i++) {
            var request = new MockHttpServletRequest("GET", "/api/rooms");
            request.setRemoteAddr("10.0.0.1");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        // IP 2 should still be allowed
        var request = new MockHttpServletRequest("GET", "/api/rooms");
        request.setRemoteAddr("10.0.0.2");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verify(chain, atLeast(101)).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void whenXForwardedForHeaderThenUsesFirstIp() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/rooms");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
