package com.watchparty.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 2, max = 100) String displayName,
    @NotBlank @Size(min = 8, max = 100)
    @Pattern(
        regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    String password
) {}
