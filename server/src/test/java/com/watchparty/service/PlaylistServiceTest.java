package com.watchparty.service;

import com.watchparty.dto.PlaylistItemResponse;
import com.watchparty.dto.PlaylistResponse;
import com.watchparty.entity.PlaylistItem;
import com.watchparty.entity.Room;
import com.watchparty.repository.PlaylistItemRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null") // Mockito matchers (any/eq/capture) return null by design
class PlaylistServiceTest {

    @Mock
    private PlaylistItemRepository playlistItemRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private YouTubeService youTubeService;

    @InjectMocks
    private PlaylistService playlistService;

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
    void whenAddItemThenCreatesItemWithCorrectPosition() {
        when(roomRepository.findById(Objects.requireNonNull(roomId))).thenReturn(Optional.of(sampleRoom));
        when(playlistItemRepository.countByRoomId(roomId)).thenReturn(2);

        PlaylistItem savedItem = createItem(UUID.randomUUID(), "https://youtube.com/watch?v=abc", "Alice", 3);
        when(playlistItemRepository.save(any(PlaylistItem.class))).thenReturn(savedItem);

        PlaylistItemResponse response = playlistService.addItem(roomId, "https://youtube.com/watch?v=abc", "Alice");

        assertNotNull(response);
        assertEquals("https://youtube.com/watch?v=abc", response.videoUrl());
        assertEquals("Alice", response.addedBy());
        assertEquals(3, response.position());

        ArgumentCaptor<PlaylistItem> captor = ArgumentCaptor.forClass(PlaylistItem.class);
        verify(playlistItemRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getPosition());
        assertEquals("Alice", captor.getValue().getAddedBy());
    }

    @Test
    void whenGetPlaylistThenReturnsOrderedItems() {
        PlaylistItem item1 = createItem(UUID.randomUUID(), "https://youtube.com/watch?v=1", "Alice", 1);
        PlaylistItem item2 = createItem(UUID.randomUUID(), "https://youtube.com/watch?v=2", "Bob", 2);
        PlaylistItem item3 = createItem(UUID.randomUUID(), "https://youtube.com/watch?v=3", "Alice", 3);

        when(playlistItemRepository.findByRoomIdOrderByPositionAsc(roomId))
                .thenReturn(List.of(item1, item2, item3));

        PlaylistResponse response = playlistService.getPlaylist(roomId);

        assertEquals(3, response.items().size());
        assertEquals(1, response.items().get(0).position());
        assertEquals(2, response.items().get(1).position());
        assertEquals(3, response.items().get(2).position());
        assertEquals("https://youtube.com/watch?v=1", response.items().get(0).videoUrl());
    }

    @Test
    void whenRemoveItemThenDeletesItem() {
        UUID itemId = UUID.randomUUID();
        PlaylistItem item = createItem(itemId, "https://youtube.com/watch?v=1", "Alice", 1);

        when(playlistItemRepository.findById(Objects.requireNonNull(itemId))).thenReturn(Optional.of(item));

        playlistService.removeItem(itemId);

        verify(playlistItemRepository).delete(Objects.requireNonNull(item));
    }

    @Test
    void whenRemoveNonExistentItemThenThrows() {
        UUID itemId = UUID.randomUUID();
        when(playlistItemRepository.findById(Objects.requireNonNull(itemId))).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> playlistService.removeItem(itemId));
    }

    @Test
    void whenGetNextItemThenReturnsCorrectItem() {
        PlaylistItem nextItem = createItem(UUID.randomUUID(), "https://youtube.com/watch?v=next", "Bob", 3);

        when(playlistItemRepository.findFirstByRoomIdAndPositionGreaterThanOrderByPositionAsc(roomId, 2))
                .thenReturn(Optional.of(nextItem));

        Optional<PlaylistItemResponse> response = playlistService.getNextItem(roomId, 2);

        assertTrue(response.isPresent());
        assertEquals("https://youtube.com/watch?v=next", response.get().videoUrl());
        assertEquals(3, response.get().position());
    }

    @Test
    void whenGetNextItemAndNoneExistsThenReturnsEmpty() {
        when(playlistItemRepository.findFirstByRoomIdAndPositionGreaterThanOrderByPositionAsc(roomId, 5))
                .thenReturn(Optional.empty());

        Optional<PlaylistItemResponse> response = playlistService.getNextItem(roomId, 5);

        assertTrue(response.isEmpty());
    }

    private PlaylistItem createItem(UUID id, String videoUrl, String addedBy, int position) {
        var item = new PlaylistItem();
        item.setId(id);
        item.setRoom(sampleRoom);
        item.setVideoUrl(videoUrl);
        item.setAddedBy(addedBy);
        item.setPosition(position);
        item.setAddedAt(Instant.now());
        return item;
    }
}
