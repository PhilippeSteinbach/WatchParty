package com.watchparty.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String nickname,
        String content,
        Map<String, Integer> reactions,
        Instant sentAt
) {
}
