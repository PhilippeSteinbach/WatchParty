package com.watchparty.websocket;

import com.watchparty.dto.*;
import com.watchparty.entity.ControlMode;
import com.watchparty.entity.Participant;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.PlaylistItemRepository;
import com.watchparty.repository.RoomRepository;
import com.watchparty.repository.UserRepository;
import com.watchparty.service.ChatService;
import com.watchparty.service.PlaylistService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null") // Mockito matchers (any/eq/capture) return null by design
class WatchPartyWebSocketHandlerTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private PlaylistItemRepository playlistItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatService chatService;

    @Mock
    private PlaylistService playlistService;

    @Mock
    private Validator validator;

    @InjectMocks
    private WatchPartyWebSocketHandler handler;

    @Captor
    private ArgumentCaptor<Object> messageCaptor;

    private Room sampleRoom;
    private Participant hostParticipant;
    private SimpMessageHeaderAccessor headerAccessor;

    @BeforeEach
    void setUp() {
        // Validator mock should return no violations by default
        lenient().when(validator.validate(any())).thenReturn(Collections.emptySet());

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

        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));
        when(participantRepository.findByRoomId(Objects.requireNonNull(sampleRoom.getId())))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(hostParticipant));
        when(participantRepository.save(any(Participant.class))).thenReturn(hostParticipant);
        when(playlistService.getPlaylist(sampleRoom.getId()))
                .thenReturn(new PlaylistResponse(Collections.emptyList()));

        handler.joinRoom(joinMessage, headerAccessor);

        ArgumentCaptor<Participant> participantCaptor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        Participant saved = participantCaptor.getValue();
        assertEquals("Alice", saved.getNickname());
        assertEquals("session-1", saved.getConnectionId());
        assertTrue(saved.isHost());

        verify(roomRepository).save(Objects.requireNonNull(sampleRoom));
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

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));

        handler.playerAction(playerMessage, headerAccessor);

        assertFalse(sampleRoom.isPlaying());
        verify(roomRepository).save(Objects.requireNonNull(sampleRoom));
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

        when(participantRepository.findByConnectionId("session-2")).thenReturn(Optional.of(Objects.requireNonNull(nonHost)));

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

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));
        when(roomRepository.findById(Objects.requireNonNull(sampleRoom.getId()))).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));
        when(participantRepository.findByRoomId(Objects.requireNonNull(sampleRoom.getId())))
                .thenReturn(List.of(remainingParticipant));

        handler.leaveRoom(headerAccessor);

        verify(participantRepository).delete(hostParticipant);

        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(captor.capture());
        assertTrue(captor.getValue().isHost());
        assertEquals("Bob", captor.getValue().getNickname());

        verify(roomRepository).save(Objects.requireNonNull(sampleRoom));
        assertEquals("session-2", sampleRoom.getHostConnectionId());
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), any(RoomStateMessage.class));
    }

    @Test
    void whenLeaveRoomAndLastParticipantThenClearsHostConnectionId() {
        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));
        when(roomRepository.findById(Objects.requireNonNull(sampleRoom.getId()))).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));
        when(participantRepository.findByRoomId(Objects.requireNonNull(sampleRoom.getId()))).thenReturn(Collections.emptyList());

        handler.leaveRoom(headerAccessor);

        verify(participantRepository).delete(hostParticipant);
        assertNull(sampleRoom.getHostConnectionId());
        verify(roomRepository).save(Objects.requireNonNull(sampleRoom));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(RoomStateMessage.class));
    }

    @Test
    void whenSyncStateThenBroadcastsCurrentRoomState() {
        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));
        when(participantRepository.findByRoomId(Objects.requireNonNull(sampleRoom.getId()))).thenReturn(List.of(hostParticipant));

        handler.syncState(headerAccessor);

        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABCD1234"), messageCaptor.capture());
        Object sent = messageCaptor.getValue();
        assertInstanceOf(RoomStateMessage.class, sent);
        var state = (RoomStateMessage) sent;
        assertEquals("ABCD1234", state.roomCode());
        assertEquals(42.5, state.currentTimeSeconds());
        assertTrue(state.isPlaying());
        assertEquals(1, state.participants().size());
    }

    @Test
    void whenPositionReportWithLargeDriftThenSendsSeekCorrection() {
        sampleRoom.setCurrentTimeSeconds(100.0);
        sampleRoom.setPlaying(true);
        sampleRoom.setStateUpdatedAt(Instant.now());

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));

        // Client reports position 10s behind expected (~100s)
        var report = new PositionReportMessage(90.0);
        handler.reportPosition(report, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-1"), eq("/queue/sync.correction"), messageCaptor.capture(), any(MessageHeaders.class));
        var correction = (SyncCorrectionMessage) messageCaptor.getValue();
        assertEquals("SEEK", correction.correctionType());
        assertEquals(1.0, correction.playbackRate());
    }

    @Test
    void whenPositionReportWithSmallDriftThenSendsRateAdjust() {
        sampleRoom.setCurrentTimeSeconds(100.0);
        sampleRoom.setPlaying(true);
        sampleRoom.setStateUpdatedAt(Instant.now());

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));

        // Client reports position 1s behind expected (~100s)
        var report = new PositionReportMessage(99.0);
        handler.reportPosition(report, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-1"), eq("/queue/sync.correction"), messageCaptor.capture(), any(MessageHeaders.class));
        var correction = (SyncCorrectionMessage) messageCaptor.getValue();
        assertEquals("RATE_ADJUST", correction.correctionType());
        assertEquals(1.05, correction.playbackRate());
    }

    @Test
    void whenPositionReportWithNoDriftThenNoCorrection() {
        sampleRoom.setCurrentTimeSeconds(100.0);
        sampleRoom.setPlaying(true);
        sampleRoom.setStateUpdatedAt(Instant.now());

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));

        // Client reports position within 0.5s tolerance
        var report = new PositionReportMessage(100.2);
        handler.reportPosition(report, headerAccessor);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(), any(MessageHeaders.class));
    }

    @Test
    void whenPositionReportWhilePausedThenNoCorrection() {
        sampleRoom.setCurrentTimeSeconds(100.0);
        sampleRoom.setPlaying(false);

        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));

        var report = new PositionReportMessage(90.0);
        handler.reportPosition(report, headerAccessor);

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(), any(MessageHeaders.class));
    }

    @Test
    void whenCalculateExpectedPositionWhilePlayingThenAddsElapsedTime() {
        sampleRoom.setPlaying(true);
        sampleRoom.setCurrentTimeSeconds(50.0);
        sampleRoom.setStateUpdatedAt(Instant.now().minusSeconds(10));

        double expected = handler.calculateExpectedPosition(sampleRoom);

        // Should be approximately 60.0 (50 + 10 seconds elapsed)
        assertTrue(expected >= 59.5 && expected <= 60.5,
                "Expected ~60.0 but got " + expected);
    }

    @Test
    void whenCalculateExpectedPositionWhilePausedThenReturnsCurrentTime() {
        sampleRoom.setPlaying(false);
        sampleRoom.setCurrentTimeSeconds(50.0);
        sampleRoom.setStateUpdatedAt(Instant.now().minusSeconds(10));

        double expected = handler.calculateExpectedPosition(sampleRoom);

        assertEquals(50.0, expected);
    }

    @Test
    void whenPlayerActionThenSetsStateUpdatedAt() {
        var playerMessage = new PlayerStateMessage("PAUSE", null, 50.0, false);
        when(participantRepository.findByConnectionId("session-1")).thenReturn(Optional.of(Objects.requireNonNull(hostParticipant)));

        assertNull(sampleRoom.getStateUpdatedAt());

        handler.playerAction(playerMessage, headerAccessor);

        assertNotNull(sampleRoom.getStateUpdatedAt());
        verify(roomRepository).save(Objects.requireNonNull(sampleRoom));
    }

    @Test
    void whenWebRtcOfferThenForwardsToTarget() {
        var offer = new WebRtcOfferMessage("session-2", "sdp-offer-data");

        handler.webRtcOffer(offer, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-2"), eq("/queue/webrtc.signal"), messageCaptor.capture(), any(MessageHeaders.class));
        var envelope = (WebRtcSignalEnvelope) messageCaptor.getValue();
        assertEquals("offer", envelope.type());
        assertEquals("session-1", envelope.fromConnectionId());
        assertEquals("sdp-offer-data", envelope.sdp());
    }

    @Test
    void whenWebRtcAnswerThenForwardsToTarget() {
        var answer = new WebRtcAnswerMessage("session-2", "sdp-answer-data");

        handler.webRtcAnswer(answer, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-2"), eq("/queue/webrtc.signal"), messageCaptor.capture(), any(MessageHeaders.class));
        var envelope = (WebRtcSignalEnvelope) messageCaptor.getValue();
        assertEquals("answer", envelope.type());
        assertEquals("session-1", envelope.fromConnectionId());
        assertEquals("sdp-answer-data", envelope.sdp());
    }

    @Test
    void whenWebRtcIceCandidateThenForwardsToTarget() {
        var ice = new WebRtcIceCandidateMessage("session-2", "candidate-data", "audio", 0);

        handler.webRtcIceCandidate(ice, headerAccessor);

        verify(messagingTemplate).convertAndSendToUser(
                eq("session-2"), eq("/queue/webrtc.signal"), messageCaptor.capture(), any(MessageHeaders.class));
        var envelope = (WebRtcSignalEnvelope) messageCaptor.getValue();
        assertEquals("ice-candidate", envelope.type());
        assertEquals("session-1", envelope.fromConnectionId());
        assertEquals("candidate-data", envelope.candidate());
        assertEquals("audio", envelope.sdpMid());
        assertEquals(0, envelope.sdpMLineIndex());
    }

    @Test
    void whenJoinRoomThenSendsSessionInfo() {
        var joinMessage = new JoinRoomMessage("ABCD1234", "Alice");

        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));
        when(participantRepository.findByRoomId(Objects.requireNonNull(sampleRoom.getId())))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(hostParticipant));
        when(participantRepository.save(any(Participant.class))).thenReturn(hostParticipant);
        when(playlistService.getPlaylist(sampleRoom.getId()))
                .thenReturn(new PlaylistResponse(Collections.emptyList()));

        handler.joinRoom(joinMessage, headerAccessor);

        // Verify session.info was sent
        verify(messagingTemplate).convertAndSendToUser(
                eq("session-1"), eq("/queue/session.info"), messageCaptor.capture(), any(MessageHeaders.class));
    }
}
