package com.watchparty.repository;

import com.watchparty.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findTop200ByRoomIdOrderBySentAtDesc(UUID roomId);

    long countByRoomIdAndNicknameAndSentAtAfter(UUID roomId, String nickname, Instant after);

    void deleteByRoomId(UUID roomId);
}
