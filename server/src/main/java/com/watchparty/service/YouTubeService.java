package com.watchparty.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeService.class);
    private static final Pattern SHORT_URL = Pattern.compile("youtu\\.be/([^?&]+)");
    private static final Pattern LONG_URL = Pattern.compile("[?&]v=([^&]+)");
    private static final Pattern ISO_DURATION = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeService(@Value("${youtube.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create("https://www.googleapis.com/youtube/v3");
    }

    public record VideoMetadata(String title, String thumbnailUrl, int durationSeconds) {}

    public VideoMetadata fetchMetadata(String videoUrl) {
        String videoId = extractVideoId(videoUrl);
        if (videoId == null) {
            return null;
        }

        String thumbnail = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";

        if (apiKey == null || apiKey.isBlank()) {
            return new VideoMetadata(null, thumbnail, 0);
        }

        try {
            JsonNode response = restClient.get()
                    .uri("/videos?part=snippet,contentDetails&id={id}&key={key}", videoId, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode items = response != null ? response.get("items") : null;
            if (items == null || items.isEmpty()) {
                return new VideoMetadata(null, thumbnail, 0);
            }

            JsonNode item = items.get(0);
            String title = item.path("snippet").path("title").asText(null);
            int duration = parseDuration(item.path("contentDetails").path("duration").asText(""));

            return new VideoMetadata(title, thumbnail, duration);
        } catch (Exception e) {
            log.warn("Failed to fetch YouTube metadata for {}: {}", videoId, e.getMessage());
            return new VideoMetadata(null, thumbnail, 0);
        }
    }

    String extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher shortMatcher = SHORT_URL.matcher(url);
        if (shortMatcher.find()) {
            return shortMatcher.group(1);
        }
        Matcher longMatcher = LONG_URL.matcher(url);
        return longMatcher.find() ? longMatcher.group(1) : null;
    }

    int parseDuration(String iso) {
        Matcher m = ISO_DURATION.matcher(iso);
        if (!m.matches()) {
            return 0;
        }
        int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
        int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        int s = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return h * 3600 + min * 60 + s;
    }

    public List<com.watchparty.dto.VideoRecommendation> searchRelated(String videoId, int maxResults) {
        if (apiKey == null || apiKey.isBlank() || videoId == null || videoId.isBlank()) {
            return List.of();
        }

        try {
            VideoMetadata metadata = fetchMetadata("https://www.youtube.com/watch?v=" + videoId);
            String query = (metadata != null && metadata.title() != null) ? metadata.title() : videoId;

            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("maxResults", maxResults + 1)
                            .queryParam("q", query)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode items = response != null ? response.get("items") : null;
            if (items == null || items.isEmpty()) {
                return List.of();
            }

            List<com.watchparty.dto.VideoRecommendation> results = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("id").path("videoId").asText(null);
                if (id == null || id.equals(videoId)) continue;

                JsonNode snippet = item.path("snippet");
                String title = snippet.path("title").asText("Untitled");
                String channel = snippet.path("channelTitle").asText("");
                String thumbnail = "https://img.youtube.com/vi/" + id + "/mqdefault.jpg";

                results.add(new com.watchparty.dto.VideoRecommendation(
                        id, "https://www.youtube.com/watch?v=" + id, title, thumbnail, channel));

                if (results.size() >= maxResults) break;
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to search related videos for {}: {}", videoId, e.getMessage());
            return List.of();
        }
    }
}
