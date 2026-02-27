package com.watchparty.repository;

import com.watchparty.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.familyId = :familyId")
    void revokeFamily(UUID familyId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
    void deleteExpired(Instant cutoff);

    void deleteByUserId(UUID userId);
}
