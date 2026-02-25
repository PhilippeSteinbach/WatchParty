package com.watchparty.dto;

public record VideoRecommendation(
        String videoId,
        String videoUrl,
        String title,
        String thumbnailUrl,
        String channelName
) {}
