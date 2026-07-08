package com.shark.lobby.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException() { super("La sala no existe."); }
}
