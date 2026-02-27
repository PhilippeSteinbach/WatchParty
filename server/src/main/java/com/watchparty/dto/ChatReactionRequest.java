package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatReactionRequest(
        @NotNull UUID messageId,
        @NotBlank @Size(max = 20) String emoji
) {
}
