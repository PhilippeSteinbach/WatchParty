package com.watchparty.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
        if (videoId == null || apiKey == null || apiKey.isBlank()) {
            return null;
        }

        try {
            JsonNode response = restClient.get()
                    .uri("/videos?part=snippet,contentDetails&id={id}&key={key}", videoId, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode items = response != null ? response.get("items") : null;
            if (items == null || items.isEmpty()) {
                return null;
            }

            JsonNode item = items.get(0);
            String title = item.path("snippet").path("title").asText(null);
            String thumbnail = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
            int duration = parseDuration(item.path("contentDetails").path("duration").asText(""));

            return new VideoMetadata(title, thumbnail, duration);
        } catch (Exception e) {
            log.warn("Failed to fetch YouTube metadata for {}: {}", videoId, e.getMessage());
            return null;
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
}
