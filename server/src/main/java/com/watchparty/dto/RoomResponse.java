package com.watchparty.dto;

import com.watchparty.entity.ControlMode;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String code,
        String name,
        ControlMode controlMode,
        int participantCount,
        Instant createdAt
) {
}
