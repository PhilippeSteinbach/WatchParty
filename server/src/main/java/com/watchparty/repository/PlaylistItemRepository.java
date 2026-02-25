package com.watchparty.repository;

import com.watchparty.entity.PlaylistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, UUID> {

    List<PlaylistItem> findByRoomIdOrderByPositionAsc(UUID roomId);

    Optional<PlaylistItem> findFirstByRoomIdAndPositionGreaterThanOrderByPositionAsc(UUID roomId, int currentPosition);

    void deleteByRoomId(UUID roomId);

    int countByRoomId(UUID roomId);
}
