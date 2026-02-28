package com.watchparty.websocket;

import com.watchparty.dto.*;
import com.watchparty.entity.ControlMode;
import com.watchparty.entity.Participant;
import com.watchparty.entity.PlaybackMode;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.PlaylistItemRepository;
import com.watchparty.repository.RoomRepository;
import com.watchparty.repository.UserRepository;
import com.watchparty.service.ChatService;
import com.watchparty.service.PlaylistService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Controller
public class WatchPartyWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WatchPartyWebSocketHandler.class);

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final PlaylistService playlistService;
    private final Validator validator;

    public WatchPartyWebSocketHandler(RoomRepository roomRepository,
                                       ParticipantRepository participantRepository,
                                       PlaylistItemRepository playlistItemRepository,
                                       UserRepository userRepository,
                                       SimpMessagingTemplate messagingTemplate,
                                       ChatService chatService,
                                       PlaylistService playlistService,
                                       Validator validator) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.playlistItemRepository = playlistItemRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
        this.playlistService = playlistService;
        this.validator = validator;
    }

    @MessageMapping("/room.join")
    @Transactional
    public void joinRoom(@Payload JoinRoomMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        validatePayload(message, sessionId);

        Room room = roomRepository.findByCode(message.roomCode())
                .orElseThrow(() -> new RoomNotFoundException(message.roomCode()));

        List<Participant> existingParticipants = participantRepository.findByRoomId(room.getId());
        boolean isFirstParticipant = existingParticipants.isEmpty();

        UUID userId = getUserId(headerAccessor);
        // Authenticated users: use display name from DB; guests: sanitize client-provided nickname
        String nickname;
        if (userId != null) {
            nickname = userRepository.findById(userId)
                    .map(user -> user.getDisplayName())
                    .orElse(sanitizeText(message.nickname()));
        } else {
            nickname = sanitizeText(message.nickname());
        }

        var participant = new Participant();
        participant.setNickname(nickname);
        participant.setConnectionId(sessionId);
        participant.setHost(isFirstParticipant);
        participant.setRoom(room);

        if (userId != null) {
            participant.setUserId(userId);
        }

        participantRepository.save(participant);

        if (isFirstParticipant) {
            room.setHostConnectionId(sessionId);
            roomRepository.save(room);
        }

        broadcastRoomState(room);

        // Send the session ID back so the client knows its own connectionId for WebRTC
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/session.info",
                Objects.requireNonNull(Map.of("connectionId", sessionId)),
                createHeaders(sessionId));

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/playlist.history", playlist,
                createHeaders(sessionId));

        List<ChatMessageResponse> chatHistory = chatService.getChatHistory(room.getId());
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/chat.history", chatHistory,
                createHeaders(sessionId));
    }

    @MessageMapping("/room.leave")
    @Transactional
    public void leaveRoom(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        handleParticipantLeave(sessionId);
    }

    @MessageMapping("/room.player")
    @Transactional
    public void playerAction(@Payload PlayerStateMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        validatePayload(message, sessionId);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();

        if (room.getControlMode() == ControlMode.HOST_ONLY && !participant.isHost()) {
            messagingTemplate.convertAndSend(
                    "/topic/room." + room.getCode(),
                    new ErrorMessage("Only the host can control playback in HOST_ONLY mode"));
            return;
        }

        switch (message.action()) {
            case "PLAY" -> {
                room.setPlaying(true);
                room.setCurrentTimeSeconds(message.currentTimeSeconds());
            }
            case "PAUSE" -> {
                room.setPlaying(false);
                room.setCurrentTimeSeconds(message.currentTimeSeconds());
            }
            case "SEEK" -> room.setCurrentTimeSeconds(message.currentTimeSeconds());
            case "CHANGE_VIDEO" -> {
                room.setCurrentVideoUrl(message.videoUrl());
                room.setCurrentTimeSeconds(0);
                room.setPlaying(false);
            }
            case "SYNC" -> {
                room.setCurrentTimeSeconds(message.currentTimeSeconds());
                room.setPlaying(message.isPlaying());
            }
            default -> throw new IllegalArgumentException("Unknown player action: " + message.action());
        }

        room.setStateUpdatedAt(Instant.now());
        roomRepository.save(room);

        messagingTemplate.convertAndSend("/topic/room." + room.getCode(), message);
    }

    @MessageMapping("/room.sync")
    @Transactional(readOnly = true)
    public void syncState(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        broadcastRoomState(room);
    }

    @MessageMapping("/room.position.report")
    @Transactional(readOnly = true)
    public void reportPosition(@Payload PositionReportMessage report, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();

        if (!room.isPlaying() || room.getCurrentVideoUrl() == null) {
            return;
        }

        double expectedPosition = calculateExpectedPosition(room);
        double drift = report.currentTimeSeconds() - expectedPosition;
        double absDrift = Math.abs(drift);

        SyncCorrectionMessage correction = null;

        if (absDrift >= 5.0) {
            correction = SyncCorrectionMessage.seek(expectedPosition);
        } else if (absDrift >= 2.0) {
            correction = SyncCorrectionMessage.seek(expectedPosition);
        } else if (absDrift >= 0.5) {
            // Behind → speed up, ahead → slow down
            double rate = drift < 0 ? 1.05 : 0.95;
            correction = SyncCorrectionMessage.rateAdjust(expectedPosition, rate);
        }

        if (correction != null) {
            messagingTemplate.convertAndSendToUser(
                    sessionId, "/queue/sync.correction", correction,
                    createHeaders(sessionId));
        }
    }

    double calculateExpectedPosition(Room room) {
        if (!room.isPlaying()) {
            return room.getCurrentTimeSeconds();
        }
        Instant stateUpdated = room.getStateUpdatedAt();
        if (stateUpdated == null) {
            return room.getCurrentTimeSeconds();
        }
        double elapsed = Duration.between(stateUpdated, Instant.now()).toMillis() / 1000.0;
        return room.getCurrentTimeSeconds() + Math.max(0, elapsed);
    }

    public void handleParticipantLeave(String sessionId) {
        Optional<Participant> participantOpt = participantRepository.findByConnectionId(sessionId);
        if (participantOpt.isEmpty()) {
            return;
        }

        Participant participant = participantOpt.get();
        UUID roomId = Objects.requireNonNull(participant.getRoom().getId());
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) return;

        Room room = roomOpt.get();

        boolean wasHost = participant.isHost();

        participantRepository.delete(participant);

        List<Participant> remaining = participantRepository.findByRoomId(room.getId());

        if (remaining.isEmpty()) {
            room.setHostConnectionId(null);
            roomRepository.save(room);
            return;
        }

        if (wasHost) {
            Participant newHost = remaining.getFirst();
            newHost.setHost(true);
            participantRepository.save(newHost);
            room.setHostConnectionId(newHost.getConnectionId());
            roomRepository.save(room);
        }

        // Notify all clients that the leaving participant's camera is off.
        // Without this, other clients keep a stale camera-state entry and
        // may display a frozen video frame (especially on Safari).
        messagingTemplate.convertAndSend(
                "/topic/room." + room.getCode() + ".camera-state",
                new CameraStateMessage(sessionId, false));

        broadcastRoomState(room);
    }

    @MessageMapping("/room.chat")
    @Transactional
    public void chatMessage(@Payload ChatMessageRequest message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        ChatMessageResponse response = chatService.sendMessage(room.getId(), participant.getNickname(), message.content());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".chat", response);
    }

    @MessageMapping("/room.chat.reaction")
    @Transactional
    public void chatReaction(@Payload ChatReactionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        validatePayload(request, sessionId);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        ChatMessageResponse response = chatService.addReaction(request.messageId(), request.emoji());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".chat", response);
    }

    @MessageMapping("/room.chat.history")
    @Transactional(readOnly = true)
    public void chatHistory(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        List<ChatMessageResponse> history = chatService.getChatHistory(room.getId());
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/chat.history", history,
                createHeaders(sessionId));
    }

    @NonNull
    private static String requireSessionId(SimpMessageHeaderAccessor headerAccessor) {
        return Objects.requireNonNull(headerAccessor.getSessionId(), "WebSocket session ID must not be null");
    }

    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        var headerAccessor = SimpMessageHeaderAccessor.create(org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }

    @MessageMapping("/room.playlist.add")
    @Transactional
    public void addPlaylistItem(@Payload AddPlaylistItemRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        playlistService.addItem(room.getId(), request.videoUrl(), participant.getNickname());

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist.add-bulk")
    @Transactional
    public void addBulkPlaylistItems(@Payload BulkAddPlaylistRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        for (String videoUrl : request.videoUrls()) {
            playlistService.addItem(room.getId(), videoUrl, participant.getNickname());
        }

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist.playNow")
    @Transactional
    public void playNow(@Payload AddPlaylistItemRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();

        boolean alreadyInPlaylist = playlistItemRepository
                .findFirstByRoomIdAndVideoUrlOrderByPositionDesc(room.getId(), request.videoUrl())
                .isPresent();
        if (!alreadyInPlaylist) {
            playlistService.addItem(room.getId(), request.videoUrl(), participant.getNickname());
        }

        room.setCurrentVideoUrl(request.videoUrl());
        room.setCurrentTimeSeconds(0);
        room.setPlaying(true);
        room.setStateUpdatedAt(Instant.now());
        roomRepository.save(room);

        broadcastRoomState(room);

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist.remove")
    @Transactional
    public void removePlaylistItem(@Payload Map<String, String> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        UUID itemId = UUID.fromString(payload.get("itemId"));
        playlistService.removeItem(itemId);

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist")
    @Transactional(readOnly = true)
    public void getPlaylist(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist.next")
    @Transactional
    public void nextPlaylistItem(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        Optional<PlaylistItemResponse> nextItem;
        if (room.getPlaybackMode() == PlaybackMode.SHUFFLE) {
            nextItem = playlistService.getRandomItem(room.getId(), room.getCurrentVideoUrl());
        } else {
            int currentPosition = playlistService.getCurrentPosition(room.getId(), room.getCurrentVideoUrl());
            nextItem = playlistService.getNextItem(room.getId(), currentPosition);
        }
        if (nextItem.isPresent()) {
            room.setCurrentVideoUrl(nextItem.get().videoUrl());
            room.setCurrentTimeSeconds(0);
            room.setPlaying(true);
            room.setStateUpdatedAt(Instant.now());
            roomRepository.save(room);

            broadcastRoomState(room);

            PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
            messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
        }
    }

    @MessageMapping("/room.playlist.mode")
    @Transactional
    public void setPlaybackMode(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        String mode = (String) payload.get("mode");

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        room.setPlaybackMode(PlaybackMode.valueOf(mode));
        roomRepository.save(room);

        broadcastRoomState(room);
    }

    @MessageMapping("/room.playlist.reorder")
    @Transactional
    public void reorderPlaylistItem(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        UUID itemId = UUID.fromString((String) payload.get("itemId"));
        int newPosition = ((Number) payload.get("newPosition")).intValue();

        playlistService.reorderItem(itemId, newPosition);

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.webrtc.offer")
    public void webRtcOffer(@Payload WebRtcOfferMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        String targetId = Objects.requireNonNull(message.targetConnectionId());
        messagingTemplate.convertAndSendToUser(
                targetId, "/queue/webrtc.signal",
                WebRtcSignalEnvelope.offer(sessionId, message.sdp()),
                createHeaders(targetId));
    }

    @MessageMapping("/room.webrtc.answer")
    public void webRtcAnswer(@Payload WebRtcAnswerMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        String targetId = Objects.requireNonNull(message.targetConnectionId());
        messagingTemplate.convertAndSendToUser(
                targetId, "/queue/webrtc.signal",
                WebRtcSignalEnvelope.answer(sessionId, message.sdp()),
                createHeaders(targetId));
    }

    @MessageMapping("/room.webrtc.ice")
    public void webRtcIceCandidate(@Payload WebRtcIceCandidateMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        String targetId = Objects.requireNonNull(message.targetConnectionId());
        messagingTemplate.convertAndSendToUser(
                targetId, "/queue/webrtc.signal",
                WebRtcSignalEnvelope.iceCandidate(sessionId, message.candidate(), message.sdpMid(), message.sdpMLineIndex()),
                createHeaders(targetId));
    }

    @MessageMapping("/room.webrtc.camera-state")
    @Transactional(readOnly = true)
    public void webRtcCameraState(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = requireSessionId(headerAccessor);
        boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        messagingTemplate.convertAndSend(
                "/topic/room." + room.getCode() + ".camera-state",
                new CameraStateMessage(sessionId, enabled));
    }

    private void broadcastRoomState(Room room) {
        List<Participant> participants = participantRepository.findByRoomId(room.getId());

        List<ParticipantMessage> participantMessages = participants.stream()
                .map(p -> new ParticipantMessage(p.getId(), p.getNickname(), p.isHost(), p.getConnectionId()))
                .toList();

        var roomState = new RoomStateMessage(
                room.getCode(),
                room.getCurrentVideoUrl(),
                calculateExpectedPosition(room),
                room.isPlaying(),
                room.getPlaybackMode().name(),
                participantMessages);

        messagingTemplate.convertAndSend("/topic/room." + room.getCode(), roomState);
    }

    @MessageExceptionHandler(RoomNotFoundException.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleRoomNotFound(RoomNotFoundException ex) {
        log.debug("Room not found: {}", ex.getRoomCode());
        return new ErrorMessage("Room not found: " + ex.getRoomCode());
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorMessage handleException(Exception ex) {
        log.warn("Unhandled WebSocket message error: {}", ex.getMessage());
        return new ErrorMessage("An error occurred");
    }

    /**
     * Validates a payload using Bean Validation. Sends an error message to the client if invalid.
     */
    private <T> void validatePayload(T payload, String sessionId) {
        Set<ConstraintViolation<T>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Validation failed");
            throw new IllegalArgumentException("Invalid message: " + errors);
        }
    }

    /**
     * Extracts the authenticated user ID from the WebSocket session, or null for guests.
     */
    private static UUID getUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null) {
            return (UUID) sessionAttrs.get(WebSocketAuthChannelInterceptor.USER_ID_ATTR);
        }
        return null;
    }

    /**
     * Strips all HTML tags to prevent stored XSS.
     */
    @NonNull
    private static String sanitizeText(String input) {
        if (input == null) {
            return "";
        }
        String cleaned = input.replace("\0", "");
        cleaned = Jsoup.clean(cleaned, Safelist.none());
        return cleaned.strip();
    }
}
