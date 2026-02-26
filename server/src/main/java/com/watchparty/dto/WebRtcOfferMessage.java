package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;

public record WebRtcOfferMessage(
        @NotBlank String targetConnectionId,
        @NotBlank String sdp
) {}
