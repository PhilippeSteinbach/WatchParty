package com.watchparty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchparty.dto.CreateRoomRequest;
import com.watchparty.dto.RoomResponse;
import com.watchparty.entity.ControlMode;
import com.watchparty.exception.RoomNotFoundException;
import com.watchparty.service.JwtService;
import com.watchparty.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

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
    private JwtService jwtService;

    @Test
    void whenCreateRoomThenReturns201() throws Exception {
        var request = new CreateRoomRequest("Movie Night", ControlMode.COLLABORATIVE, false);
        var response = new RoomResponse(
                UUID.randomUUID(), "ABCD1234", "Movie Night",
                ControlMode.COLLABORATIVE, 0, Instant.now(), null, false
        );
        when(roomService.createRoom(any(CreateRoomRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ABCD1234"))
                .andExpect(jsonPath("$.name").value("Movie Night"))
                .andExpect(jsonPath("$.controlMode").value("COLLABORATIVE"));
    }

    @Test
    void whenCreateRoomWithInvalidRequestThenReturns400() throws Exception {
        var invalidRequest = new CreateRoomRequest("", null, false);

        mockMvc.perform(post("/api/rooms")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(invalidRequest))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenGetRoomThenReturnsRoom() throws Exception {
        var response = new RoomResponse(
                UUID.randomUUID(), "ABCD1234", "Movie Night",
                ControlMode.HOST_ONLY, 3, Instant.now(), null, false
        );
        when(roomService.findByCode("ABCD1234")).thenReturn(response);

        mockMvc.perform(get("/api/rooms/ABCD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ABCD1234"))
                .andExpect(jsonPath("$.participantCount").value(3));
    }

    @Test
    void whenGetNonExistentRoomThenReturns404() throws Exception {
        when(roomService.findByCode("NOTFOUND")).thenThrow(new RoomNotFoundException("NOTFOUND"));

        mockMvc.perform(get("/api/rooms/NOTFOUND"))
                .andExpect(status().isNotFound());
    }

    @Test
    void whenDeleteRoomThenReturns204() throws Exception {
        doNothing().when(roomService).deleteByCode("ABCD1234");

        mockMvc.perform(delete("/api/rooms/ABCD1234"))
                .andExpect(status().isNoContent());
    }

    @Test
    void whenDeleteNonExistentRoomThenReturns404() throws Exception {
        doThrow(new RoomNotFoundException("NOTFOUND")).when(roomService).deleteByCode("NOTFOUND");

        mockMvc.perform(delete("/api/rooms/NOTFOUND"))
                .andExpect(status().isNotFound());
    }
}
