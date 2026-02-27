package com.watchparty.service;

import com.watchparty.dto.CreateRoomRequest;
import com.watchparty.entity.ControlMode;
import com.watchparty.entity.Room;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null") // Mockito matchers (any/eq/capture) return null by design
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @InjectMocks
    private RoomService roomService;

    private Room sampleRoom;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        sampleRoom = new Room();
        sampleRoom.setId(UUID.randomUUID());
        sampleRoom.setCode("ABCD1234");
        sampleRoom.setName("Movie Night");
        sampleRoom.setControlMode(ControlMode.COLLABORATIVE);
        sampleRoom.setOwnerId(ownerId);
        sampleRoom.setCreatedAt(Instant.now());
    }

    @Test
    void whenCreateRoomThenReturnsSavedRoom() {
        var request = new CreateRoomRequest("Movie Night", ControlMode.COLLABORATIVE, false);
        when(roomRepository.save(any(Room.class))).thenReturn(sampleRoom);

        var response = roomService.createRoom(request, null);

        assertNotNull(response);
        assertEquals("Movie Night", response.name());
        assertEquals(ControlMode.COLLABORATIVE, response.controlMode());
        assertEquals("ABCD1234", response.code());
        assertEquals(0, response.participantCount());
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void whenFindByCodeThenReturnsRoom() {
        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));
        when(participantRepository.findByRoomId(sampleRoom.getId())).thenReturn(Collections.emptyList());

        var response = roomService.findByCode("ABCD1234");

        assertNotNull(response);
        assertEquals("ABCD1234", response.code());
        assertEquals("Movie Night", response.name());
    }

    @Test
    void whenFindByCodeNotFoundThenThrows() {
        when(roomRepository.findByCode("NOTFOUND")).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, () -> roomService.findByCode("NOTFOUND"));
    }

    @Test
    void whenDeleteByCodeThenDeletes() {
        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));

        roomService.deleteByCode("ABCD1234", ownerId);

        verify(roomRepository).deleteByCode("ABCD1234");
    }

    @Test
    void whenDeleteByCodeNotFoundThenThrows() {
        when(roomRepository.findByCode("NOTFOUND")).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, () -> roomService.deleteByCode("NOTFOUND", ownerId));
    }

    @Test
    void whenDeleteByCodeWithWrongOwnerThenThrows403() {
        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));

        UUID otherUserId = UUID.randomUUID();
        assertThrows(ResponseStatusException.class, () -> roomService.deleteByCode("ABCD1234", otherUserId));
        verify(roomRepository, never()).deleteByCode(any());
    }

    @Test
    void whenRenameRoomWithWrongOwnerThenThrows403() {
        when(roomRepository.findByCode("ABCD1234")).thenReturn(Optional.of(Objects.requireNonNull(sampleRoom)));

        UUID otherUserId = UUID.randomUUID();
        assertThrows(ResponseStatusException.class,
                () -> roomService.renameRoom("ABCD1234", "New Name", otherUserId));
        verify(roomRepository, never()).save(any());
    }
}
