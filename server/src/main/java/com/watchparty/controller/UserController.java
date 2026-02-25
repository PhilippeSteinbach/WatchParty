package com.watchparty.controller;

import com.watchparty.dto.RoomResponse;
import com.watchparty.security.AuthenticatedUser;
import com.watchparty.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Users", description = "Authenticated user operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final RoomService roomService;

    public UserController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/rooms")
    @Operation(summary = "List own permanent rooms")
    public List<RoomResponse> myRooms(@AuthenticationPrincipal AuthenticatedUser user) {
        return roomService.findByOwner(user.userId());
    }
}
