package com.watchparty.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.security.SecureRandom;

@Entity
@Table(name = "rooms")
public class Room {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", unique = true, nullable = false, length = 8)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "control_mode", nullable = false)
    private ControlMode controlMode;

    @Column(name = "host_connection_id")
    private String hostConnectionId;

    @Column(name = "current_video_url")
    private String currentVideoUrl;

    @Column(name = "current_time_seconds", nullable = false)
    private double currentTimeSeconds;

    @Column(name = "is_playing", nullable = false)
    private boolean isPlaying;

    @Column(name = "state_updated_at")
    private Instant stateUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        if (this.code == null) {
            this.code = generateCode(8);
        }
    }

    private String generateCode(int length) {
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ControlMode getControlMode() {
        return controlMode;
    }

    public void setControlMode(ControlMode controlMode) {
        this.controlMode = controlMode;
    }

    public String getHostConnectionId() {
        return hostConnectionId;
    }

    public void setHostConnectionId(String hostConnectionId) {
        this.hostConnectionId = hostConnectionId;
    }

    public String getCurrentVideoUrl() {
        return currentVideoUrl;
    }

    public void setCurrentVideoUrl(String currentVideoUrl) {
        this.currentVideoUrl = currentVideoUrl;
    }

    public double getCurrentTimeSeconds() {
        return currentTimeSeconds;
    }

    public void setCurrentTimeSeconds(double currentTimeSeconds) {
        this.currentTimeSeconds = currentTimeSeconds;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getStateUpdatedAt() {
        return stateUpdatedAt;
    }

    public void setStateUpdatedAt(Instant stateUpdatedAt) {
        this.stateUpdatedAt = stateUpdatedAt;
    }
}
