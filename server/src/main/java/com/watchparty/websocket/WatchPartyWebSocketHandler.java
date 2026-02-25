package com.watchparty.websocket;

import com.watchparty.dto.*;
import com.watchparty.entity.ControlMode;
import com.watchparty.entity.Participant;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.RoomRepository;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Controller
public class WatchPartyWebSocketHandler {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WatchPartyWebSocketHandler(RoomRepository roomRepository,
                                       ParticipantRepository participantRepository,
                                       SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.messagingTemplate = messagingTemplate;
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
        participantRepository.save(participant);

        if (isFirstParticipant) {
            room.setHostConnectionId(sessionId);
            roomRepository.save(room);
        }

        broadcastRoomState(room);
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
            case "PLAY" -> room.setPlaying(true);
            case "PAUSE" -> room.setPlaying(false);
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

        roomRepository.save(room);

        messagingTemplate.convertAndSend("/topic/room." + room.getCode(), message);
    }

    @MessageMapping("/room.sync")
    @Transactional(readOnly = true)
    public void syncState(@Payload PlayerStateMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        Participant participant = participantRepository.findByConnectionId(sessionId)
                .orElseThrow(() -> new IllegalStateException("Participant not found for session: " + sessionId));

        Room room = participant.getRoom();
        broadcastRoomState(room);
    }

    public void handleParticipantLeave(String sessionId) {
        var participantOpt = participantRepository.findByConnectionId(sessionId);
        if (participantOpt.isEmpty()) {
            return;
        }

        Participant participant = participantOpt.get();
        Room room = participant.getRoom();
        boolean wasHost = participant.isHost();

        participantRepository.delete(participant);

        List<Participant> remaining = participantRepository.findByRoomId(room.getId());

        if (remaining.isEmpty()) {
            roomRepository.delete(room);
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

    private void broadcastRoomState(Room room) {
        List<Participant> participants = participantRepository.findByRoomId(room.getId());

        List<ParticipantMessage> participantMessages = participants.stream()
                .map(p -> new ParticipantMessage(p.getId(), p.getNickname(), p.isHost()))
                .toList();

        var roomState = new RoomStateMessage(
                room.getCode(),
                room.getCurrentVideoUrl(),
                room.getCurrentTimeSeconds(),
                room.isPlaying(),
                participantMessages);

        messagingTemplate.convertAndSend("/topic/room." + room.getCode(), roomState);
    }
}
