package com.watchparty.service;

import com.watchparty.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Deletes anonymous rooms whose expiry timestamp has passed.
 * Runs every 10 minutes.
 */
@Service
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    private final RoomRepository roomRepository;

    public RoomCleanupService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Scheduled(fixedRate = 10 * 60 * 1000)
    @Transactional
    public void deleteExpiredRooms() {
        List<?> expired = roomRepository.findByExpiresAtBeforeAndIsPermanentFalse(Instant.now());
        if (!expired.isEmpty()) {
            roomRepository.deleteAll((Iterable) expired);
            log.info("Cleaned up {} expired anonymous room(s)", expired.size());
        }
    }
}
