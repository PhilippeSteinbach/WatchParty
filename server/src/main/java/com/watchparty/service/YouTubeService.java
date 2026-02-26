package com.watchparty.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

@Service
public class YouTubeService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeService.class);
    private static final Pattern SHORT_URL = Pattern.compile("youtu\\.be/([^?&]+)");
    private static final Pattern LONG_URL = Pattern.compile("[?&]v=([^&]+)");
    private static final Pattern ISO_DURATION = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
    private static final Pattern PLAYLIST_ID = Pattern.compile("[?&]list=([^&]+)");
    private static final int MAX_PLAYLIST_ITEMS = 200;

    private final RestClient restClient;
    private final String apiKey;

    public YouTubeService(@Value("${youtube.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create("https://www.googleapis.com/youtube/v3");
    }

    public record VideoMetadata(@Nullable String title, String thumbnailUrl, int durationSeconds) {}
    public record PlaylistInfo(@Nullable String title, int videoCount, List<PlaylistVideoItem> items) {}
    public record PlaylistVideoItem(String videoId, String videoUrl, String title, String thumbnailUrl, int durationSeconds) {}

    public Optional<VideoMetadata> fetchMetadata(String videoUrl) {
        Optional<String> videoIdOpt = extractVideoId(videoUrl);
        if (videoIdOpt.isEmpty()) {
            return Optional.empty();
        }

        String videoId = videoIdOpt.get();
        String thumbnail = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";

        if (apiKey == null || apiKey.isBlank()) {
            return Optional.of(new VideoMetadata(null, thumbnail, 0));
        }

        try {
            JsonNode response = restClient.get()
                    .uri("/videos?part=snippet,contentDetails&id={id}&key={key}", videoId, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode items = response != null ? response.get("items") : null;
            if (items == null || items.isEmpty()) {
                return Optional.of(new VideoMetadata(null, thumbnail, 0));
            }

            JsonNode item = items.get(0);
            String title = item.path("snippet").path("title").asText(null);
            int duration = parseDuration(item.path("contentDetails").path("duration").asText(""));

            return Optional.of(new VideoMetadata(title, thumbnail, duration));
        } catch (Exception e) {
            log.warn("Failed to fetch YouTube metadata for {}: {}", videoId, e.getMessage());
            return Optional.of(new VideoMetadata(null, thumbnail, 0));
        }
    }

    Optional<String> extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher shortMatcher = SHORT_URL.matcher(url);
        if (shortMatcher.find()) {
            return Optional.of(shortMatcher.group(1));
        }
        Matcher longMatcher = LONG_URL.matcher(url);
        return longMatcher.find() ? Optional.of(longMatcher.group(1)) : Optional.empty();
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

    public Optional<PlaylistInfo> fetchPlaylistItems(String playlistId) {
        Objects.requireNonNull(playlistId, "playlistId must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            // Fetch playlist title
            JsonNode playlistResponse = restClient.get()
                    .uri("/playlists?part=snippet&id={id}&key={key}", playlistId, apiKey)
                    .retrieve()
                    .body(JsonNode.class);

            String playlistTitle = null;
            JsonNode plItems = playlistResponse != null ? playlistResponse.get("items") : null;
            if (plItems != null && !plItems.isEmpty()) {
                playlistTitle = plItems.get(0).path("snippet").path("title").asText("Untitled Playlist");
            }

            // Fetch all playlist items with pagination
            List<PlaylistVideoItem> items = new ArrayList<>();
            String pageToken = null;

            do {
                String uri = "/playlistItems?part=snippet,contentDetails&playlistId={plId}&maxResults=50&key={key}"
                        + (pageToken != null ? "&pageToken=" + pageToken : "");

                JsonNode response = restClient.get()
                        .uri(uri, playlistId, apiKey)
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) break;

                JsonNode responseItems = response.get("items");
                if (responseItems != null) {
                    for (JsonNode item : responseItems) {
                        String videoId = item.path("snippet").path("resourceId").path("videoId").asText(null);
                        if (videoId == null) continue;

                        String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                        String title = item.path("snippet").path("title").asText("Untitled");
                        String thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";

                        items.add(new PlaylistVideoItem(videoId, videoUrl, title, thumbnailUrl, 0));

                        if (items.size() >= MAX_PLAYLIST_ITEMS) break;
                    }
                }

                if (items.size() >= MAX_PLAYLIST_ITEMS) break;
                pageToken = response.has("nextPageToken") ? response.get("nextPageToken").asText() : null;
            } while (pageToken != null);

            return Optional.of(new PlaylistInfo(playlistTitle, items.size(), items));
        } catch (Exception e) {
            log.warn("Failed to fetch playlist items for {}: {}", playlistId, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> extractPlaylistId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PLAYLIST_ID.matcher(url);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public List<com.watchparty.dto.VideoRecommendation> searchRelated(String videoId, int maxResults) {
        if (apiKey == null || apiKey.isBlank() || videoId == null || videoId.isBlank()) {
            return List.of();
        }

        try {
            Optional<VideoMetadata> metadata = fetchMetadata("https://www.youtube.com/watch?v=" + videoId);
            String rawQuery = metadata.map(VideoMetadata::title).orElse(videoId);
            if (rawQuery == null) rawQuery = videoId;
            String query = rawQuery.replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim().replaceAll("\\s+", " ");
            if (query.length() > 60) query = query.substring(0, 60).trim();

            JsonNode response = restClient.get()
                    .uri("/search?part=snippet&type=video&maxResults={max}&q={q}&key={key}",
                            maxResults + 1, query, apiKey)
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
