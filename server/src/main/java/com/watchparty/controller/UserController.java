package com.watchparty.controller;

import com.watchparty.dto.*;
import com.watchparty.security.AuthenticatedUser;
import com.watchparty.service.AuthService;
import com.watchparty.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Users", description = "Authenticated user operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final RoomService roomService;
    private final AuthService authService;

    public UserController(RoomService roomService, AuthService authService) {
        this.roomService = roomService;
        this.authService = authService;
    }

    @GetMapping("/rooms")
    @Operation(summary = "List own permanent rooms")
    public List<RoomResponse> myRooms(@AuthenticationPrincipal AuthenticatedUser user) {
        return roomService.findByOwner(user.userId());
    }

    @PatchMapping
    @Operation(summary = "Update profile")
    public AuthResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(user.userId(), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change password")
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.userId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete account")
    public void deleteAccount(@AuthenticationPrincipal AuthenticatedUser user,
                              @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(user.userId(), request.password());
    }
}
