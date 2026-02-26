package com.watchparty.service;

import com.watchparty.dto.ChatMessageResponse;
import com.watchparty.entity.ChatMessage;
import com.watchparty.entity.Room;
import com.watchparty.repository.ChatMessageRepository;
import com.watchparty.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatService {

    private static final int RATE_LIMIT_MAX_MESSAGES = 5;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 10;

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;

    public ChatService(ChatMessageRepository chatMessageRepository, RoomRepository roomRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    @NonNull
    public ChatMessageResponse sendMessage(UUID roomId, String nickname, String content) {
        Instant windowStart = Instant.now().minusSeconds(RATE_LIMIT_WINDOW_SECONDS);
        long recentCount = chatMessageRepository.countByRoomIdAndNicknameAndSentAtAfter(roomId, nickname, windowStart);

        if (recentCount >= RATE_LIMIT_MAX_MESSAGES) {
            throw new IllegalStateException("Rate limit exceeded: max " + RATE_LIMIT_MAX_MESSAGES
                    + " messages per " + RATE_LIMIT_WINDOW_SECONDS + " seconds");
        }

        Room room = roomRepository.findById(Objects.requireNonNull(roomId))
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + roomId));

        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setNickname(nickname);
        message.setContent(content);

        message = chatMessageRepository.save(message);
        return toResponse(message);
    }

    @Transactional
    @NonNull
    public ChatMessageResponse addReaction(UUID messageId, String emoji) {
        ChatMessage message = chatMessageRepository.findById(Objects.requireNonNull(messageId))
                .orElseThrow(() -> new EntityNotFoundException("Message not found: " + messageId));

        message.getReactions().merge(emoji, 1, (a, b) -> a + b);
        message = chatMessageRepository.save(message);
        return toResponse(message);
    }

    @Transactional(readOnly = true)
    @NonNull
    public List<ChatMessageResponse> getChatHistory(UUID roomId) {
        List<ChatMessage> messages = chatMessageRepository.findTop200ByRoomIdOrderBySentAtDesc(roomId);
        return Objects.requireNonNull(messages.reversed().stream()
                .map(this::toResponse)
                .toList());
    }

    @NonNull
    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getNickname(),
                message.getContent(),
                message.getReactions(),
                message.getSentAt()
        );
    }
}
