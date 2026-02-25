package com.watchparty.dto;

public record PlayerStateMessage(
        String action,
        String videoUrl,
        double currentTimeSeconds,
        boolean isPlaying) {
}
