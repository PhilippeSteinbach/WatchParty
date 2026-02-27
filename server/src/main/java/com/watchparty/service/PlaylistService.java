package com.watchparty.service;

import com.watchparty.dto.PlaylistItemResponse;
import com.watchparty.dto.PlaylistResponse;
import com.watchparty.entity.PlaylistItem;
import com.watchparty.entity.Room;
import com.watchparty.repository.PlaylistItemRepository;
import com.watchparty.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
        Objects.requireNonNull(videoUrl, "videoUrl must not be null");
        Objects.requireNonNull(addedBy, "addedBy must not be null");

        Room room = roomRepository.findById(Objects.requireNonNull(roomId))
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + roomId));

        int nextPosition = playlistItemRepository.countByRoomId(roomId) + 1;

        PlaylistItem item = new PlaylistItem();
        item.setRoom(room);
        item.setVideoUrl(videoUrl);
        item.setAddedBy(addedBy);
        item.setPosition(nextPosition);

        youTubeService.fetchMetadata(videoUrl).ifPresent(metadata -> {
            item.setTitle(metadata.title());
            item.setThumbnailUrl(metadata.thumbnailUrl());
            item.setDurationSeconds(metadata.durationSeconds());
        });

        PlaylistItem saved = playlistItemRepository.save(item);
        return toResponse(saved);
    }

    @Transactional
    public void removeItem(UUID itemId) {
        PlaylistItem item = playlistItemRepository.findById(Objects.requireNonNull(itemId))
                .orElseThrow(() -> new EntityNotFoundException("Playlist item not found: " + itemId));
        playlistItemRepository.delete(Objects.requireNonNull(item));
    }

    @Transactional(readOnly = true)
    @NonNull
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
    public Optional<PlaylistItemResponse> getNextItem(UUID roomId, int currentPosition) {
        return playlistItemRepository.findFirstByRoomIdAndPositionGreaterThanOrderByPositionAsc(roomId, currentPosition)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<PlaylistItemResponse> getRandomItem(UUID roomId, String excludeVideoUrl) {
        List<PlaylistItem> items = playlistItemRepository.findByRoomIdOrderByPositionAsc(roomId);
        List<PlaylistItem> candidates = items.stream()
                .filter(item -> !item.getVideoUrl().equals(excludeVideoUrl))
                .toList();
        if (candidates.isEmpty()) return Optional.empty();
        PlaylistItem picked = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        return Optional.of(toResponse(picked));
    }

    @Transactional
    public void reorderItem(UUID itemId, int newPosition) {
        PlaylistItem item = playlistItemRepository.findById(Objects.requireNonNull(itemId))
                .orElseThrow(() -> new EntityNotFoundException("Playlist item not found: " + itemId));

        List<PlaylistItem> items = playlistItemRepository.findByRoomIdOrderByPositionAsc(item.getRoom().getId());

        // Remove the dragged item from the list
        items.removeIf(i -> i.getId().equals(itemId));

        // Clamp newPosition to valid range (1-based)
        int insertIndex = Math.max(0, Math.min(newPosition - 1, items.size()));

        // Insert at the new position
        items.add(insertIndex, item);

        // Reassign sequential positions starting at 1
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i + 1);
        }

        playlistItemRepository.saveAll(items);
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
