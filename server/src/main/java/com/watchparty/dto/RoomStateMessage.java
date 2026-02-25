package com.watchparty.dto;

import java.util.List;

public record RoomStateMessage(
        String roomCode,
        String currentVideoUrl,
        double currentTimeSeconds,
        boolean isPlaying,
        List<ParticipantMessage> participants) {
}
