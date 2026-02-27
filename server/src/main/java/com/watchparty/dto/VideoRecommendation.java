package com.watchparty.dto;

public record VideoRecommendation(
        String videoId,
        String videoUrl,
        String title,
        String thumbnailUrl,
        String channelName,
        int durationSeconds
) {
    /** Backwards-compatible constructor without durationSeconds. */
    public VideoRecommendation(String videoId, String videoUrl, String title, String thumbnailUrl, String channelName) {
        this(videoId, videoUrl, title, thumbnailUrl, channelName, 0);
    }
}
