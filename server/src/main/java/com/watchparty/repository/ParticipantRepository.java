package com.watchparty.repository;

import com.watchparty.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findByRoomId(UUID roomId);

    Optional<Participant> findByConnectionId(String connectionId);

    void deleteByConnectionId(String connectionId);
}
