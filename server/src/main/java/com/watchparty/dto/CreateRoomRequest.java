package com.watchparty.dto;

import com.watchparty.entity.ControlMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull ControlMode controlMode,
        boolean isPermanent
) {
}
