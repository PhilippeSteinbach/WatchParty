package com.watchparty.websocket;

import com.watchparty.dto.*;
import com.watchparty.entity.ControlMode;
import com.watchparty.entity.Participant;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchPartyWebSocketHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WatchPartyWebSocketHandler handler;

    @Captor
    private ArgumentCaptor<Object> messageCaptor;

    private Room sampleRoom;
    private Participant hostParticipant;
    private SimpMessageHeaderAccessor headerAccessor;

    @BeforeEach
    void setUp() {
        sampleRoom = new Room();
        sampleRoom.setId(UUID.randomUUID());
        sampleRoom.setCode("ABCD1234");
        sampleRoom.setName("Movie Night");
        sampleRoom.setControlMode(ControlMode.COLLABORATIVE);
        sampleRoom.setCurrentVideoUrl("https://youtube.com/watch?v=test");
        sampleRoom.setCurrentTimeSeconds(42.5);
        sampleRoom.setPlaying(true);
        sampleRoom.setCreatedAt(Instant.now());

        hostParticipant = new Participant();
        hostParticipant.setId(UUID.randomUUID());
        hostParticipant.setNickname("HostUser");
        hostParticipant.setConnectionId("session-1");
        hostParticipant.setHost(true);
        hostParticipant.setRoom(sampleRoom);
        hostParticipant.setJoinedAt(Instant.now());

        headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionId("session-1");
    }

    @Test
    void whenJoinRoomThenCreatesParticipantAndBroadcastsState() {
        var joinMessage = new JoinRoomMessage("ABCD1234", "Alice");

        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(sampleRoom));
        when(participantRepository.findByRoomId(sampleRoom.getId()))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(hostParticipant));
        when(participantRepository.save(any(Participant.class))).thenReturn(hostParticipant);

        handler.joinRoom(joinMessage, headerAccessor);

        ArgumentCaptor<Participant> participantCaptor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        Participant saved = participantCaptor.getValue();
        assertEquals("Alice", saved.getNickname());
        assertEquals("session-1", saved.getConnectionId());
        assertTrue(saved.isHost());

        verify(roomRepository).save(sampleRoom);
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), any(RoomStateMessage.class));
    }

    @Test
    void whenJoinRoomNotFoundThenThrows() {
        var joinMessage = new JoinRoomMessage("NOTFOUND", "Alice");
        when(roomRepository.findByCode("NOTFOUND")).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, () -> handler.joinRoom(joinMessage, headerAccessor));
    }

    @Test
    void whenPlayerActionByHostThenUpdatesRoom() {
        sampleRoom.setControlMode(ControlMode.HOST_ONLY);
        var playerMessage = new PlayerStateMessage("PAUSE", null, 50.0, false);

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(hostParticipant));

        handler.playerAction(playerMessage, headerAccessor);

        assertFalse(sampleRoom.isPlaying());
        verify(roomRepository).save(sampleRoom);
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), eq(playerMessage));
    }

    @Test
    void whenPlayerActionByNonHostInHostOnlyModeThenRejects() {
        sampleRoom.setControlMode(ControlMode.HOST_ONLY);

        var nonHost = new Participant();
        nonHost.setId(UUID.randomUUID());
        nonHost.setNickname("Viewer");
        nonHost.setConnectionId("session-2");
        nonHost.setHost(false);
        nonHost.setRoom(sampleRoom);
        nonHost.setJoinedAt(Instant.now());

        headerAccessor.setSessionId("session-2");
        var playerMessage = new PlayerStateMessage("PLAY", null, 0.0, true);

        when(participantRepository.findByConnectionId("session-2")).thenReturn(Optional.of(nonHost));

        handler.playerAction(playerMessage, headerAccessor);

        verify(roomRepository, never()).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), any(ErrorMessage.class));
    }

    @Test
    void whenLeaveRoomAndWasHostThenReassignsHost() {
        var remainingParticipant = new Participant();
        remainingParticipant.setId(UUID.randomUUID());
        remainingParticipant.setNickname("Bob");
        remainingParticipant.setConnectionId("session-2");
        remainingParticipant.setHost(false);
        remainingParticipant.setRoom(sampleRoom);
        remainingParticipant.setJoinedAt(Instant.now());

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(hostParticipant));
        when(participantRepository.findByRoomId(sampleRoom.getId()))
                .thenReturn(List.of(remainingParticipant));

        handler.leaveRoom(headerAccessor);

        verify(participantRepository).delete(hostParticipant);

        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(captor.capture());
        assertTrue(captor.getValue().isHost());
        assertEquals("Bob", captor.getValue().getNickname());

        verify(roomRepository).save(sampleRoom);
        assertEquals("session-2", sampleRoom.getHostConnectionId());
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), any(RoomStateMessage.class));
    }

    @Test
    void whenLeaveRoomAndLastParticipantThenDeletesRoom() {
        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(hostParticipant));
        when(participantRepository.findByRoomId(sampleRoom.getId())).thenReturn(Collections.emptyList());

        handler.leaveRoom(headerAccessor);

        verify(participantRepository).delete(hostParticipant);
        verify(roomRepository).delete(sampleRoom);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(RoomStateMessage.class));
    }

    @Test
    void whenSyncStateThenBroadcastsCurrentRoomState() {
        var syncMessage = new PlayerStateMessage("SYNC", null, 0.0, false);

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(hostParticipant));
        when(participantRepository.findByRoomId(sampleRoom.getId())).thenReturn(List.of(hostParticipant));

        handler.syncState(syncMessage, headerAccessor);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), messageCaptor.capture());
        Object sent = messageCaptor.getValue();
        assertInstanceOf(RoomStateMessage.class, sent);
        var state = (RoomStateMessage) sent;
        assertEquals("ABCD1234", state.roomCode());
        assertEquals(42.5, state.currentTimeSeconds());
        assertTrue(state.isPlaying());
        assertEquals(1, state.participants().size());
    }
}
