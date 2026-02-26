package com.watchparty.dto;

import org.jspecify.annotations.Nullable;

public record PlayerStateMessage(
        String action,
        @Nullable String videoUrl,
        double currentTimeSeconds,
        boolean isPlaying) {
}
