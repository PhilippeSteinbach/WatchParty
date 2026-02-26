package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;

public record WebRtcAnswerMessage(
        @NotBlank String targetConnectionId,
        @NotBlank String sdp
) {}
