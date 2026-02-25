package com.watchparty.service;

import com.watchparty.dto.ChatMessageResponse;
import com.watchparty.entity.ChatMessage;
import com.watchparty.entity.Room;
import com.watchparty.repository.ChatMessageRepository;
import com.watchparty.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
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

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

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
        sampleRoom.setName("Movie Night");
        sampleRoom.setCreatedAt(Instant.now());
    }

    @Test
    void whenSendMessageThenSavesAndReturnsResponse() {
        when(chatMessageRepository.countByRoomIdAndNicknameAndSentAtAfter(eq(roomId), eq("Alice"), any(Instant.class)))
                .thenReturn(0L);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(sampleRoom));

        ChatMessage savedMessage = new ChatMessage();
        savedMessage.setId(UUID.randomUUID());
        savedMessage.setRoom(sampleRoom);
        savedMessage.setNickname("Alice");
        savedMessage.setContent("Hello everyone!");
        savedMessage.setReactions(new HashMap<>());
        savedMessage.setSentAt(Instant.now());

        when(chatMessageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);

        ChatMessageResponse response = chatService.sendMessage(roomId, "Alice", "Hello everyone!");

        assertNotNull(response);
        assertEquals("Alice", response.nickname());
        assertEquals("Hello everyone!", response.content());
        assertNotNull(response.id());
        assertNotNull(response.sentAt());
        assertTrue(response.reactions().isEmpty());

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertEquals("Alice", captor.getValue().getNickname());
        assertEquals("Hello everyone!", captor.getValue().getContent());
    }

    @Test
    void whenSendMessageExceedsRateLimitThenThrows() {
        when(chatMessageRepository.countByRoomIdAndNicknameAndSentAtAfter(eq(roomId), eq("Alice"), any(Instant.class)))
                .thenReturn(5L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> chatService.sendMessage(roomId, "Alice", "Too many messages"));

        assertTrue(ex.getMessage().contains("Rate limit exceeded"));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void whenAddReactionThenIncrementsEmojiCount() {
        UUID messageId = UUID.randomUUID();
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setRoom(sampleRoom);
        message.setNickname("Alice");
        message.setContent("Great movie!");
        message.setReactions(new HashMap<>(Map.of("👍", 2)));
        message.setSentAt(Instant.now());

        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageResponse response = chatService.addReaction(messageId, "👍");

        assertEquals(3, response.reactions().get("👍"));
        verify(chatMessageRepository).save(message);
    }

    @Test
    void whenAddReactionToNonExistentMessageThenThrows() {
        UUID messageId = UUID.randomUUID();
        when(chatMessageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> chatService.addReaction(messageId, "👍"));
    }

    @Test
    void whenGetChatHistoryThenReturnsMessagesInAscendingOrder() {
        Instant now = Instant.now();

        ChatMessage msg1 = new ChatMessage();
        msg1.setId(UUID.randomUUID());
        msg1.setRoom(sampleRoom);
        msg1.setNickname("Alice");
        msg1.setContent("First message");
        msg1.setReactions(new HashMap<>());
        msg1.setSentAt(now.minusSeconds(60));

        ChatMessage msg2 = new ChatMessage();
        msg2.setId(UUID.randomUUID());
        msg2.setRoom(sampleRoom);
        msg2.setNickname("Bob");
        msg2.setContent("Second message");
        msg2.setReactions(new HashMap<>());
        msg2.setSentAt(now.minusSeconds(30));

        ChatMessage msg3 = new ChatMessage();
        msg3.setId(UUID.randomUUID());
        msg3.setRoom(sampleRoom);
        msg3.setNickname("Alice");
        msg3.setContent("Third message");
        msg3.setReactions(new HashMap<>());
        msg3.setSentAt(now);

        // Repository returns desc order (newest first)
        when(chatMessageRepository.findTop200ByRoomIdOrderBySentAtDesc(roomId))
                .thenReturn(List.of(msg3, msg2, msg1));

        List<ChatMessageResponse> history = chatService.getChatHistory(roomId);

        assertEquals(3, history.size());
        assertEquals("First message", history.get(0).content());
        assertEquals("Second message", history.get(1).content());
        assertEquals("Third message", history.get(2).content());
    }
}
