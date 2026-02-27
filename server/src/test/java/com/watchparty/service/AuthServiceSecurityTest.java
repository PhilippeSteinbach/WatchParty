package com.watchparty.service;

import com.watchparty.entity.RefreshToken;
import com.watchparty.entity.User;
import com.watchparty.repository.RefreshTokenRepository;
import com.watchparty.repository.RoomRepository;
import com.watchparty.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Security-focused tests for AuthService refresh token rotation and replay detection.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                roomRepository, refreshTokenRepository, 604800000L);
    }

    @Test
    void whenRefreshTokenReplayDetectedThenRevokesEntireFamily() {
        var claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtService.parseToken("stolen-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);

        UUID familyId = UUID.randomUUID();
        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setTokenHash(anyHash());
        revokedToken.setUserId(UUID.randomUUID());
        revokedToken.setFamilyId(familyId);
        revokedToken.setRevoked(true);
        revokedToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedToken));

        assertThrows(ResponseStatusException.class, () -> authService.refresh("stolen-token"));

        verify(refreshTokenRepository).revokeFamily(familyId);
    }

    @Test
    void whenRefreshTokenExpiredThenRejects() {
        var claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtService.parseToken("expired-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);

        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setTokenHash(anyHash());
        expiredToken.setUserId(UUID.randomUUID());
        expiredToken.setFamilyId(UUID.randomUUID());
        expiredToken.setRevoked(false);
        expiredToken.setExpiresAt(Instant.now().minusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThrows(ResponseStatusException.class, () -> authService.refresh("expired-token"));
    }

    @Test
    void whenRefreshTokenValidThenRotatesAndRevokesOld() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        var claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getSubject()).thenReturn(userId.toString());
        when(jwtService.parseToken("valid-refresh")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("new-access");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("new-refresh");

        RefreshToken validToken = new RefreshToken();
        validToken.setTokenHash(anyHash());
        validToken.setUserId(userId);
        validToken.setFamilyId(familyId);
        validToken.setRevoked(false);
        validToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(validToken));

        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");
        user.setDisplayName("Test");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var response = authService.refresh("valid-refresh");

        assertNotNull(response);
        assertEquals("new-access", response.accessToken());
        assertTrue(validToken.isRevoked());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void whenRefreshWithNonRefreshTokenTypeThenRejects() {
        var claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtService.parseToken("access-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> authService.refresh("access-token"));
    }

    @Test
    void whenRefreshTokenNotInDatabaseThenRejects() {
        var claims = mock(io.jsonwebtoken.Claims.class);
        when(jwtService.parseToken("unknown-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> authService.refresh("unknown-token"));
    }

    private String anyHash() {
        return "a".repeat(64);
    }
}
