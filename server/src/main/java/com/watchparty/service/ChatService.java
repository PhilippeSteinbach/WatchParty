package com.watchparty.service;

import com.watchparty.dto.ChatMessageResponse;
import com.watchparty.entity.ChatMessage;
import com.watchparty.entity.Room;
import com.watchparty.repository.ChatMessageRepository;
import com.watchparty.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

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
        message.setNickname(sanitizeText(nickname));
        message.setContent(sanitizeText(content));

        message = chatMessageRepository.save(message);
        return toResponse(message);
    }

    @Transactional
    @NonNull
    public ChatMessageResponse addReaction(UUID messageId, String emoji, String nickname) {
        if (emoji == null || emoji.isBlank() || emoji.length() > 20) {
            throw new IllegalArgumentException("Invalid emoji");
        }
        String sanitizedEmoji = sanitizeText(emoji);
        String sanitizedNickname = sanitizeText(nickname);
        ChatMessage message = chatMessageRepository.findById(Objects.requireNonNull(messageId))
                .orElseThrow(() -> new EntityNotFoundException("Message not found: " + messageId));

        String previousEmoji = message.getUserReactions().get(sanitizedNickname);

        if (previousEmoji != null) {
            // Remove the old emoji count (decrement or remove entry)
            message.getReactions().compute(previousEmoji, (k, count) ->
                    (count == null || count <= 1) ? null : count - 1);
        }

        if (sanitizedEmoji.equals(previousEmoji)) {
            // Same emoji toggled off: just remove from user reactions
            message.getUserReactions().remove(sanitizedNickname);
        } else {
            // New or different emoji: add/replace
            message.getReactions().merge(sanitizedEmoji, 1, Integer::sum);
            message.getUserReactions().put(sanitizedNickname, sanitizedEmoji);
        }

        message = chatMessageRepository.save(message);
        return toResponse(message);
    }

    @Transactional(readOnly = true)
    @NonNull
    public List<ChatMessageResponse> getChatHistory(UUID roomId) {
        List<ChatMessage> messages = chatMessageRepository.findTop200ByRoomIdOrderBySentAtDesc(roomId);
        // Skip individual messages that fail to convert (e.g. legacy/malformed data) rather
        // than letting one bad row take down the whole history and the join it's sent from.
        return Objects.requireNonNull(messages.reversed().stream()
                .map(this::tryToResponse)
                .filter(Objects::nonNull)
                .toList());
    }

    private @Nullable ChatMessageResponse tryToResponse(ChatMessage message) {
        try {
            return toResponse(message);
        } catch (Exception ex) {
            log.warn("Skipping malformed chat message {}: {}", message.getId(), ex.getMessage());
            return null;
        }
    }

    @NonNull
    private ChatMessageResponse toResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getNickname(),
                message.getContent(),
                message.getReactions(),
                message.getUserReactions(),
                message.getSentAt()
        );
    }

    /**
     * Strips all HTML tags and normalizes whitespace to prevent stored XSS.
     */
    @NonNull
    private static String sanitizeText(String input) {
        if (input == null) {
            return "";
        }
        // Remove null bytes
        String cleaned = input.replace("\0", "");
        // Strip all HTML tags
        cleaned = Jsoup.clean(cleaned, Safelist.none());
        // Normalize whitespace
        return cleaned.strip();
    }
}
