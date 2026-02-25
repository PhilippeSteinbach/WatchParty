package com.watchparty.dto;

import java.time.Instant;
import java.util.UUID;

public record PlaylistItemResponse(
        UUID id,
        String videoUrl,
        String title,
        String thumbnailUrl,
        int durationSeconds,
        String addedBy,
        int position,
        Instant addedAt
) {
}
