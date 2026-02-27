package com.watchparty.controller;

import com.watchparty.dto.VideoRecommendation;
import com.watchparty.service.JwtService;
import com.watchparty.service.YouTubeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VideoController.class)
@SuppressWarnings("null")
class VideoControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private YouTubeService youTubeService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void whenSearchWithQueryThenReturnsResults() throws Exception {
        var results = List.of(
                new VideoRecommendation("abc123", "https://www.youtube.com/watch?v=abc123",
                        "Test Video", "https://img.youtube.com/vi/abc123/mqdefault.jpg", "TestChannel", 120)
        );
        when(youTubeService.search("lofi beats", 10)).thenReturn(results);

        mockMvc.perform(get("/api/videos/search").param("q", "lofi beats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].videoId").value("abc123"))
                .andExpect(jsonPath("$[0].title").value("Test Video"))
                .andExpect(jsonPath("$[0].durationSeconds").value(120));
    }

    @Test
    void whenSearchWithCustomLimitThenClampsToMax() throws Exception {
        when(youTubeService.search("test", 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/videos/search").param("q", "test").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(youTubeService).search("test", 20);
    }

    @Test
    void whenSearchWithoutQueryThenReturnsError() throws Exception {
        mockMvc.perform(get("/api/videos/search"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void whenSearchWithEmptyQueryThenReturnsEmptyList() throws Exception {
        when(youTubeService.search("", 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/videos/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void whenGetRecommendationsThenReturnsResults() throws Exception {
        var results = List.of(
                new VideoRecommendation("rec1", "https://www.youtube.com/watch?v=rec1",
                        "Recommended Video", "https://img.youtube.com/vi/rec1/mqdefault.jpg", "Channel")
        );
        when(youTubeService.searchRelated("abc123", 6)).thenReturn(results);

        mockMvc.perform(get("/api/videos/abc123/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].videoId").value("rec1"))
                .andExpect(jsonPath("$[0].title").value("Recommended Video"));
    }

    @Test
    void whenSuggestWithQueryThenReturnsSuggestions() throws Exception {
        when(youTubeService.suggest("lofi")).thenReturn(List.of("lofi beats", "lofi hip hop", "lofi girl"));

        mockMvc.perform(get("/api/videos/suggest").param("q", "lofi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("lofi beats"))
                .andExpect(jsonPath("$[1]").value("lofi hip hop"))
                .andExpect(jsonPath("$[2]").value("lofi girl"));
    }

    @Test
    void whenSuggestWithEmptyQueryThenReturnsEmptyList() throws Exception {
        when(youTubeService.suggest("")).thenReturn(List.of());

        mockMvc.perform(get("/api/videos/suggest").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
