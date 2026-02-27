package com.watchparty.service;

import com.watchparty.dto.AuthResponse;
import com.watchparty.dto.ChangePasswordRequest;
import com.watchparty.dto.LoginRequest;
import com.watchparty.dto.RegisterRequest;
import com.watchparty.dto.UpdateProfileRequest;
import com.watchparty.entity.RefreshToken;
import com.watchparty.entity.User;
import com.watchparty.repository.RefreshTokenRepository;
import com.watchparty.repository.RoomRepository;
import com.watchparty.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoomRepository roomRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationMs;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RoomRepository roomRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.roomRepository = roomRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return buildAuthResponse(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return buildAuthResponse(user, UUID.randomUUID());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        try {
            Claims claims = jwtService.parseToken(refreshToken);
            if (!jwtService.isRefreshToken(claims)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type");
            }

            String tokenHash = hashToken(refreshToken);
            RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token not found"));

            if (storedToken.isRevoked()) {
                // Replay detected: revoke the entire token family
                log.warn("Refresh token replay detected for user {} (family {}). Revoking entire family.",
                        storedToken.getUserId(), storedToken.getFamilyId());
                refreshTokenRepository.revokeFamily(storedToken.getFamilyId());
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
            }

            if (storedToken.getExpiresAt().isBefore(Instant.now())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
            }

            // Revoke the used token
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);

            User user = userRepository.findById(Objects.requireNonNull(UUID.fromString(claims.getSubject())))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

            // Issue new tokens in the same family
            return buildAuthResponse(user, storedToken.getFamilyId());
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
    }

    public AuthResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        if (request.email() != null && !request.email().isBlank()) {
            if (userRepository.existsByEmailAndIdNot(request.email(), userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
            }
            user.setEmail(request.email());
        }
        userRepository.save(user);
        return buildAuthResponse(user, UUID.randomUUID());
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password is incorrect");
        }
        refreshTokenRepository.deleteByUserId(userId);
        roomRepository.deleteByOwnerId(userId);
        userRepository.delete(user);
    }

    /**
     * Cleans up expired refresh tokens every hour.
     */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }

    private AuthResponse buildAuthResponse(User user, UUID familyId) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        // Store hashed refresh token for rotation/revocation
        var storedToken = new RefreshToken();
        storedToken.setTokenHash(hashToken(refreshToken));
        storedToken.setUserId(user.getId());
        storedToken.setFamilyId(familyId);
        storedToken.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        storedToken.setRevoked(false);
        refreshTokenRepository.save(storedToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getDisplayName()
        );
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
