package com.watchparty.service;

import com.watchparty.dto.ChangePasswordRequest;
import com.watchparty.dto.UpdateProfileRequest;
import com.watchparty.entity.User;
import com.watchparty.repository.RoomRepository;
import com.watchparty.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setId(userId);
        sampleUser.setEmail("alice@example.com");
        sampleUser.setDisplayName("Alice");
        sampleUser.setPasswordHash("hashed_password");
    }

    @Test
    void whenUpdateProfileDisplayNameThenUpdates() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("refresh");

        var request = new UpdateProfileRequest("NewName", null);
        var response = authService.updateProfile(userId, request);

        assertEquals("NewName", sampleUser.getDisplayName());
        assertNotNull(response);
        verify(userRepository).save(sampleUser);
    }

    @Test
    void whenUpdateProfileEmailThenChecksUniqueness() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByEmailAndIdNot("new@example.com", userId)).thenReturn(false);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("refresh");

        var request = new UpdateProfileRequest(null, "new@example.com");
        authService.updateProfile(userId, request);

        assertEquals("new@example.com", sampleUser.getEmail());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void whenUpdateProfileWithDuplicateEmailThenThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByEmailAndIdNot("taken@example.com", userId)).thenReturn(true);

        var request = new UpdateProfileRequest(null, "taken@example.com");

        assertThrows(ResponseStatusException.class, () -> authService.updateProfile(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void whenChangePasswordWithCorrectCurrentThenUpdates() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("oldpass", "hashed_password")).thenReturn(true);
        when(passwordEncoder.encode("newpass12")).thenReturn("new_hashed");

        var request = new ChangePasswordRequest("oldpass", "newpass12");
        authService.changePassword(userId, request);

        assertEquals("new_hashed", sampleUser.getPasswordHash());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void whenChangePasswordWithWrongCurrentThenThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong", "hashed_password")).thenReturn(false);

        var request = new ChangePasswordRequest("wrong", "newpass12");

        assertThrows(ResponseStatusException.class, () -> authService.changePassword(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void whenDeleteAccountWithCorrectPasswordThenDeletesUserAndRooms() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("mypassword", "hashed_password")).thenReturn(true);

        authService.deleteAccount(userId, "mypassword");

        verify(roomRepository).deleteByOwnerId(userId);
        verify(userRepository).delete(sampleUser);
    }

    @Test
    void whenDeleteAccountWithWrongPasswordThenThrows() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong", "hashed_password")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> authService.deleteAccount(userId, "wrong"));
        verify(roomRepository, never()).deleteByOwnerId(any());
        verify(userRepository, never()).delete(any());
    }
}
