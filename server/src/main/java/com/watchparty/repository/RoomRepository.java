package com.watchparty.repository;

import com.watchparty.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByCode(String code);

    void deleteByCode(String code);

    List<Room> findByOwnerId(UUID ownerId);

    List<Room> findByExpiresAtBeforeAndIsPermanentFalse(Instant cutoff);
}
