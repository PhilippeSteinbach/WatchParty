package com.watchparty.service;

import com.watchparty.dto.CreateRoomRequest;
import com.watchparty.dto.RoomResponse;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;

    public RoomService(RoomRepository roomRepository, ParticipantRepository participantRepository) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        var room = new Room();
        room.setName(request.name());
        room.setControlMode(request.controlMode());
        room.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        room = roomRepository.save(room);
        return toResponse(room, 0);
    }

    @Transactional(readOnly = true)
    public RoomResponse findByCode(String code) {
        var room = roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));
        int participantCount = participantRepository.findByRoomId(room.getId()).size();
        return toResponse(room, participantCount);
    }

    @Transactional
    public void deleteByCode(String code) {
        roomRepository.findByCode(code)
                .orElseThrow(() -> new RoomNotFoundException(code));
        roomRepository.deleteByCode(code);
    }

    private RoomResponse toResponse(Room room, int participantCount) {
        return new RoomResponse(
                room.getId(),
                room.getCode(),
                room.getName(),
                room.getControlMode(),
                participantCount,
                room.getCreatedAt()
        );
    }
}
