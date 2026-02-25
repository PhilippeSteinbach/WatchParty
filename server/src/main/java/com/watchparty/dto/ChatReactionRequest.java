package com.watchparty.dto;

import java.util.UUID;

public record ChatReactionRequest(
        UUID messageId,
        String emoji
) {
}
