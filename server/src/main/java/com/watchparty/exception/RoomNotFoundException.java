package com.watchparty.exception;

public class RoomNotFoundException extends RuntimeException {

    private final String roomCode;

    public RoomNotFoundException(String roomCode) {
        super("Room not found with code: " + roomCode);
        this.roomCode = roomCode;
    }

    public String getRoomCode() {
        return roomCode;
    }
}
