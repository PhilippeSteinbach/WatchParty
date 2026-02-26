package com.watchparty.dto;

import jakarta.validation.constraints.NotBlank;

public record WebRtcIceCandidateMessage(
        @NotBlank String targetConnectionId,
        @NotBlank String candidate,
        String sdpMid,
        Integer sdpMLineIndex
) {}
