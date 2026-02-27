package com.watchparty.service;

import com.watchparty.dto.VideoRecommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeServiceTest {

    private final YouTubeService service = new YouTubeService("");

    @Test
    void whenSearchWithNullQueryThenReturnsEmpty() {
        List<VideoRecommendation> results = service.search(null, 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void whenSearchWithBlankQueryThenReturnsEmpty() {
        List<VideoRecommendation> results = service.search("   ", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void whenSearchWithVideoUrlThenReturnsSingleResult() {
        List<VideoRecommendation> results = service.search("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 10);

        assertEquals(1, results.size());
        assertEquals("dQw4w9WgXcQ", results.getFirst().videoId());
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", results.getFirst().videoUrl());
        assertNotNull(results.getFirst().thumbnailUrl());
    }

    @Test
    void whenSearchWithShortVideoUrlThenReturnsSingleResult() {
        List<VideoRecommendation> results = service.search("https://youtu.be/dQw4w9WgXcQ", 10);

        assertEquals(1, results.size());
        assertEquals("dQw4w9WgXcQ", results.getFirst().videoId());
    }

    @Test
    void whenSearchWithTextQueryAndNoApiKeyThenReturnsEmpty() {
        List<VideoRecommendation> results = service.search("lofi beats", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void whenSearchWithPlaylistUrlAndNoApiKeyThenReturnsEmpty() {
        List<VideoRecommendation> results = service.search(
                "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    void extractVideoIdReturnsIdForStandardUrl() {
        var id = service.extractVideoId("https://www.youtube.com/watch?v=abc123");
        assertTrue(id.isPresent());
        assertEquals("abc123", id.get());
    }

    @Test
    void extractVideoIdReturnsIdForShortUrl() {
        var id = service.extractVideoId("https://youtu.be/abc123");
        assertTrue(id.isPresent());
        assertEquals("abc123", id.get());
    }

    @Test
    void extractVideoIdReturnsEmptyForInvalidUrl() {
        var id = service.extractVideoId("not a url");
        assertTrue(id.isEmpty());
    }

    @Test
    void parseDurationHandlesHoursMinutesSeconds() {
        assertEquals(3723, service.parseDuration("PT1H2M3S"));
    }

    @Test
    void parseDurationHandlesMinutesOnly() {
        assertEquals(300, service.parseDuration("PT5M"));
    }

    @Test
    void parseDurationHandlesSecondsOnly() {
        assertEquals(45, service.parseDuration("PT45S"));
    }

    @Test
    void parseDurationReturnsZeroForInvalid() {
        assertEquals(0, service.parseDuration("invalid"));
    }

    @Test
    void whenSuggestWithNullQueryThenReturnsEmpty() {
        assertTrue(service.suggest(null).isEmpty());
    }

    @Test
    void whenSuggestWithBlankQueryThenReturnsEmpty() {
        assertTrue(service.suggest("  ").isEmpty());
    }
}
