package com.watchparty.dto;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record RoomStateMessage(
        String roomCode,
        @Nullable String currentVideoUrl,
        double currentTimeSeconds,
        boolean isPlaying,
        List<ParticipantMessage> participants) {
}
