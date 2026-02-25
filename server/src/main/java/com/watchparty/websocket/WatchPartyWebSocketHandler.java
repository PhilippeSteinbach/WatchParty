package com.watchparty.websocket;

import com.watchparty.dto.*;
import com.watchparty.entity.ControlMode;
import com.watchparty.entity.Participant;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.PlaylistItemRepository;
import com.watchparty.repository.RoomRepository;
import com.watchparty.service.ChatService;
import com.watchparty.service.PlaylistService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class WatchPartyWebSocketHandler {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final PlaylistService playlistService;

    public WatchPartyWebSocketHandler(RoomRepository roomRepository,
                                       ParticipantRepository participantRepository,
                                       PlaylistItemRepository playlistItemRepository,
                                       SimpMessagingTemplate messagingTemplate,
                                       ChatService chatService,
                                       PlaylistService playlistService) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.playlistItemRepository = playlistItemRepository;
        this.messagingTemplate = messagingTemplate;
        this.chatService = chatService;
        this.playlistService = playlistService;
    }

    @MessageMapping("/room.join")
    @Transactional
    public void joinRoom(@Payload JoinRoomMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Room room = roomRepository.findByCode(message.roomCode())
                .orElseThrow(() -> new RoomNotFoundException(message.roomCode()));

        List<Participant> existingParticipants = participantRepository.findByRoomId(room.getId());
        boolean isFirstParticipant = existingParticipants.isEmpty();

        var participant = new Participant();
        participant.setNickname(message.nickname());
        participant.setConnectionId(sessionId);
        participant.setHost(isFirstParticipant);
        participant.setRoom(room);

        var sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null) {
            var userId = (java.util.UUID) sessionAttrs.get(WebSocketAuthChannelInterceptor.USER_ID_ATTR);
            if (userId != null) {
                participant.setUserId(userId);
            }
        }

        participantRepository.save(participant);

        if (isFirstParticipant) {
            room.setHostConnectionId(sessionId);
            roomRepository.save(room);
        }

        broadcastRoomState(room);

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/playlist.history", playlist,
                createHeaders(sessionId));
    }

    @MessageMapping("/room.leave")
    @Transactional
    public void leaveRoom(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        handleParticipantLeave(sessionId);
    }

    @MessageMapping("/room.player")
    @Transactional
    public void playerAction(@Payload PlayerStateMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

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
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        broadcastRoomState(room);
    }

    @MessageMapping("/room.position.report")
    @Transactional(readOnly = true)
    public void reportPosition(@Payload PositionReportMessage report, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

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
        var participantOpt = participantRepository.findByConnectionId(sessionId);
        if (participantOpt.isEmpty()) {
            return;
        }

        Participant participant = participantOpt.get();
        UUID roomId = participant.getRoom().getId();
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return;

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

        broadcastRoomState(room);
    }

    @MessageMapping("/room.chat")
    @Transactional
    public void chatMessage(@Payload ChatMessageRequest message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        ChatMessageResponse response = chatService.sendMessage(room.getId(), participant.getNickname(), message.content());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".chat", response);
    }

    @MessageMapping("/room.chat.reaction")
    @Transactional
    public void chatReaction(@Payload ChatReactionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        ChatMessageResponse response = chatService.addReaction(request.messageId(), request.emoji());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".chat", response);
    }

    @MessageMapping("/room.chat.history")
    @Transactional(readOnly = true)
    public void chatHistory(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        List<ChatMessageResponse> history = chatService.getChatHistory(room.getId());
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/chat.history", history,
                createHeaders(sessionId));
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
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        playlistService.addItem(room.getId(), request.videoUrl(), participant.getNickname());

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist.playNow")
    @Transactional
    public void playNow(@Payload AddPlaylistItemRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

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
        String sessionId = headerAccessor.getSessionId();

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
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    @MessageMapping("/room.playlist.next")
    @Transactional
    public void nextPlaylistItem(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        int currentPosition = playlistService.getCurrentPosition(room.getId(), room.getCurrentVideoUrl());

        PlaylistItemResponse nextItem = playlistService.getNextItem(room.getId(), currentPosition);
        if (nextItem != null) {
            room.setCurrentVideoUrl(nextItem.videoUrl());
            room.setCurrentTimeSeconds(0);
            room.setPlaying(false);
            room.setStateUpdatedAt(Instant.now());
            roomRepository.save(room);

            broadcastRoomState(room);

            PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
            messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
        }
    }

    @MessageMapping("/room.playlist.reorder")
    @Transactional
    public void reorderPlaylistItem(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        UUID itemId = UUID.fromString((String) payload.get("itemId"));
        int newPosition = ((Number) payload.get("newPosition")).intValue();

        playlistService.reorderItem(itemId, newPosition);

        PlaylistResponse playlist = playlistService.getPlaylist(room.getId());
        messagingTemplate.convertAndSend("/topic/room." + room.getCode() + ".playlist", playlist);
    }

    private void broadcastRoomState(Room room) {
        List<Participant> participants = participantRepository.findByRoomId(room.getId());

        List<ParticipantMessage> participantMessages = participants.stream()
                .map(p -> new ParticipantMessage(p.getId(), p.getNickname(), p.isHost()))
                .toList();

        var roomState = new RoomStateMessage(
                room.getCode(),
                room.getCurrentVideoUrl(),
                calculateExpectedPosition(room),
                room.isPlaying(),
                participantMessages);

        messagingTemplate.convertAndSend("/topic/room." + room.getCode(), roomState);
    }
}
