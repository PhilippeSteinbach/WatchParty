package com.watchparty.service;

import com.watchparty.entity.Room;
import com.watchparty.repository.ParticipantRepository;
import com.watchparty.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Deletes anonymous rooms whose expiry timestamp has passed.
 * Runs every 10 minutes.
 * Also clears all stale participants on server startup.
 */
@Service
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;

    public RoomCleanupService(RoomRepository roomRepository, ParticipantRepository participantRepository) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void clearStaleParticipants() {
        long count = participantRepository.count();
        if (count > 0) {
            participantRepository.deleteAll();
            log.info("Cleared {} stale participant(s) from previous server session", count);
        }
    }

    @Scheduled(fixedRate = 10 * 60 * 1000)
    @Transactional
    public void deleteExpiredRooms() {
        List<Room> expired = roomRepository.findByExpiresAtBeforeAndIsPermanentFalse(Instant.now());
        if (!expired.isEmpty()) {
            roomRepository.deleteAll(expired);
            log.info("Cleaned up {} expired anonymous room(s)", expired.size());
        }
    }
}
