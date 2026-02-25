package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;

public record AddPlaylistItemRequest(
        @NotBlank String videoUrl
) {
}
