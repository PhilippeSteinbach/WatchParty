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
    private final YouTubeService youTubeService;

    public PlaylistService(PlaylistItemRepository playlistItemRepository, RoomRepository roomRepository,
                           YouTubeService youTubeService) {
        this.playlistItemRepository = playlistItemRepository;
        this.roomRepository = roomRepository;
        this.youTubeService = youTubeService;
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

        YouTubeService.VideoMetadata metadata = youTubeService.fetchMetadata(videoUrl);
        if (metadata != null) {
            item.setTitle(metadata.title());
            item.setThumbnailUrl(metadata.thumbnailUrl());
            item.setDurationSeconds(metadata.durationSeconds());
        }

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
    public int getCurrentPosition(UUID roomId, String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return 0;
        }
        return playlistItemRepository.findFirstByRoomIdAndVideoUrlOrderByPositionDesc(roomId, videoUrl)
                .map(PlaylistItem::getPosition)
                .orElse(0);
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
