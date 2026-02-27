package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

public record PlayerStateMessage(
        @NotBlank @Pattern(regexp = "PLAY|PAUSE|SEEK|CHANGE_VIDEO|SYNC") String action,
        @Nullable String videoUrl,
        double currentTimeSeconds,
        boolean isPlaying) {
}
