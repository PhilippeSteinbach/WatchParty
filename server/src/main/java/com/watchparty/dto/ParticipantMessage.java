package com.watchparty.dto;

import java.util.UUID;

public record ParticipantMessage(UUID id, String nickname, boolean isHost) {
}
