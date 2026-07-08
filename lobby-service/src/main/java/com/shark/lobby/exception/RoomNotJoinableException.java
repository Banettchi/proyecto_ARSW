package com.shark.lobby.exception;

public class RoomNotJoinableException extends RuntimeException {
    public RoomNotJoinableException() { super("La sala no está disponible para unirse."); }
}
