package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomMessage(
        @NotBlank @Size(max = 20) String roomCode,
        @NotBlank @Size(max = 50) String nickname
) {
}
