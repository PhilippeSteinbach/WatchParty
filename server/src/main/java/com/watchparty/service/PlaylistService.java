package com.watchparty.service;

import com.watchparty.dto.PlaylistItemResponse;
import com.watchparty.dto.PlaylistResponse;
import com.watchparty.entity.PlaylistItem;
import com.watchparty.entity.Room;
import com.watchparty.repository.PlaylistItemRepository;
import com.watchparty.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PlaylistService {

    private final PlaylistItemRepository playlistItemRepository;
    private final RoomRepository roomRepository;

    public PlaylistService(PlaylistItemRepository playlistItemRepository, RoomRepository roomRepository) {
        this.playlistItemRepository = playlistItemRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public PlaylistItemResponse addItem(UUID roomId, String videoUrl, String addedBy) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + roomId));

        int nextPosition = playlistItemRepository.countByRoomId(roomId) + 1;

        var item = new PlaylistItem();
        item.setRoom(room);
        item.setVideoUrl(videoUrl);
        item.setAddedBy(addedBy);
        item.setPosition(nextPosition);

        item = playlistItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public void removeItem(UUID itemId) {
        PlaylistItem item = playlistItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Playlist item not found: " + itemId));
        playlistItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public PlaylistResponse getPlaylist(UUID roomId) {
        List<PlaylistItemResponse> items = playlistItemRepository.findByRoomIdOrderByPositionAsc(roomId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PlaylistResponse(items);
    }

    @Transactional(readOnly = true)
    public PlaylistItemResponse getNextItem(UUID roomId, int currentPosition) {
        return playlistItemRepository.findFirstByRoomIdAndPositionGreaterThanOrderByPositionAsc(roomId, currentPosition)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public PlaylistItemResponse reorderItem(UUID itemId, int newPosition) {
        PlaylistItem item = playlistItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Playlist item not found: " + itemId));
        item.setPosition(newPosition);
        item = playlistItemRepository.save(item);
        return toResponse(item);
    }

    private PlaylistItemResponse toResponse(PlaylistItem item) {
        return new PlaylistItemResponse(
                item.getId(),
                item.getVideoUrl(),
                item.getTitle(),
                item.getThumbnailUrl(),
                item.getDurationSeconds(),
                item.getAddedBy(),
                item.getPosition(),
                item.getAddedAt()
        );
    }
}
