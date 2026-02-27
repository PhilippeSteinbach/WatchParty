package com.watchparty.service;

import com.watchparty.entity.ChatMessage;
import com.watchparty.entity.Room;
import com.watchparty.repository.ChatMessageRepository;
import com.watchparty.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Security-focused tests for ChatService input sanitization (XSS prevention).
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ChatServiceSecurityTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private ChatService chatService;

    private Room sampleRoom;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        sampleRoom = new Room();
        sampleRoom.setId(roomId);
        sampleRoom.setCode("ABCD1234");
        sampleRoom.setName("Test Room");
        sampleRoom.setCreatedAt(Instant.now());
    }

    @Test
    void whenMessageContainsHtmlThenStripsTagsBeforeSaving() {
        when(chatMessageRepository.countByRoomIdAndNicknameAndSentAtAfter(eq(roomId), any(), any()))
                .thenReturn(0L);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(sampleRoom));

        ChatMessage savedMsg = new ChatMessage();
        savedMsg.setId(UUID.randomUUID());
        savedMsg.setRoom(sampleRoom);
        savedMsg.setNickname("Alice");
        savedMsg.setContent("Hello");
        savedMsg.setReactions(new HashMap<>());
        savedMsg.setSentAt(Instant.now());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);

        chatService.sendMessage(roomId, "Alice", "<script>alert('xss')</script>Hello");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        String savedContent = captor.getValue().getContent();
        assertFalse(savedContent.contains("<script>"), "HTML script tags should be stripped");
        assertFalse(savedContent.contains("</script>"), "HTML closing tags should be stripped");
    }

    @Test
    void whenNicknameContainsHtmlThenStripsTagsBeforeSaving() {
        when(chatMessageRepository.countByRoomIdAndNicknameAndSentAtAfter(eq(roomId), any(), any()))
                .thenReturn(0L);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(sampleRoom));

        ChatMessage savedMsg = new ChatMessage();
        savedMsg.setId(UUID.randomUUID());
        savedMsg.setRoom(sampleRoom);
        savedMsg.setNickname("Alice");
        savedMsg.setContent("Hello");
        savedMsg.setReactions(new HashMap<>());
        savedMsg.setSentAt(Instant.now());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);

        chatService.sendMessage(roomId, "<b onmouseover=alert('xss')>Name</b>", "Hello");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        String savedNickname = captor.getValue().getNickname();
        assertFalse(savedNickname.contains("<"), "HTML tags should be stripped from nickname");
        assertEquals("Name", savedNickname);
    }

    @Test
    void whenContentContainsNullBytesThenRemovesThem() {
        when(chatMessageRepository.countByRoomIdAndNicknameAndSentAtAfter(eq(roomId), any(), any()))
                .thenReturn(0L);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(sampleRoom));

        ChatMessage savedMsg = new ChatMessage();
        savedMsg.setId(UUID.randomUUID());
        savedMsg.setRoom(sampleRoom);
        savedMsg.setNickname("Alice");
        savedMsg.setContent("Hello");
        savedMsg.setReactions(new HashMap<>());
        savedMsg.setSentAt(Instant.now());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);

        chatService.sendMessage(roomId, "Alice", "Hello\0World");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertFalse(captor.getValue().getContent().contains("\0"), "Null bytes should be removed");
    }

    @Test
    void whenAddReactionWithTooLongEmojiThenRejects() {
        String longEmoji = "x".repeat(21);
        assertThrows(IllegalArgumentException.class, () -> chatService.addReaction(UUID.randomUUID(), longEmoji));
    }

    @Test
    void whenAddReactionWithNullEmojiThenRejects() {
        assertThrows(IllegalArgumentException.class, () -> chatService.addReaction(UUID.randomUUID(), null));
    }

    @Test
    void whenAddReactionWithBlankEmojiThenRejects() {
        assertThrows(IllegalArgumentException.class, () -> chatService.addReaction(UUID.randomUUID(), "   "));
    }
}
