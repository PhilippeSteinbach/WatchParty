package com.watchparty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.dto.*;
import com.watchparty.security.AuthenticatedUser;
import com.watchparty.service.AuthService;
import com.watchparty.service.JwtService;
import com.watchparty.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Objects;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@SuppressWarnings("null") // Mockito matchers (any/eq/capture) return null by design
class UserControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    private void setAuthPrincipal(UUID userId) {
        var user = new AuthenticatedUser(userId, "test@example.com");
        var auth = new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void whenUpdateProfileThenReturns200() throws Exception {
        var userId = UUID.randomUUID();
        setAuthPrincipal(userId);

        var authResponse = new AuthResponse("access", "refresh", userId, "new@example.com", "NewName");
        when(authService.updateProfile(eq(userId), any(UpdateProfileRequest.class))).thenReturn(authResponse);

        var request = new UpdateProfileRequest("NewName", "new@example.com");

        mockMvc.perform(patch("/api/users/me")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("NewName"))
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void whenChangePasswordThenReturns204() throws Exception {
        var userId = UUID.randomUUID();
        setAuthPrincipal(userId);

        doNothing().when(authService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        var request = new ChangePasswordRequest("oldpass", "newpass12");

        mockMvc.perform(put("/api/users/me/password")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isNoContent());
    }

    @Test
    void whenDeleteAccountThenReturns204() throws Exception {
        var userId = UUID.randomUUID();
        setAuthPrincipal(userId);

        doNothing().when(authService).deleteAccount(eq(userId), eq("mypassword"));

        var request = new DeleteAccountRequest("mypassword");

        mockMvc.perform(delete("/api/users/me")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isNoContent());
    }
}
