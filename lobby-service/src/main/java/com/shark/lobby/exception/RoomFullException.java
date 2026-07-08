package com.shark.lobby.exception;

public class RoomFullException extends RuntimeException {
    public RoomFullException() { super("La sala está llena."); }
}
