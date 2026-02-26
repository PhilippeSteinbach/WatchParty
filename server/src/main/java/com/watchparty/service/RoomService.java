package com.watchparty.service;

import com.watchparty.dto.CreateRoomRequest;
import com.watchparty.dto.RoomResponse;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;

    public RoomService(RoomRepository roomRepository, ParticipantRepository participantRepository) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request, UUID ownerId) {
        var room = new Room();
        room.setName(request.name());
        room.setControlMode(request.controlMode());
        if (ownerId != null && request.isPermanent()) {
            room.setOwnerId(ownerId);
            room.setPermanent(true);
        }
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

    @Transactional(readOnly = true)
    public List<RoomResponse> findByOwner(UUID ownerId) {
        return roomRepository.findByOwnerId(ownerId).stream()
                .map(room -> toResponse(room, participantRepository.findByRoomId(room.getId()).size()))
                .toList();
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
                room.getCreatedAt(),
                room.getOwnerId(),
                room.isPermanent()
        );
    }
}
