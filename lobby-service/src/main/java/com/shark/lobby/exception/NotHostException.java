package com.shark.lobby.exception;

public class NotHostException extends RuntimeException {
    public NotHostException() { super("Solo el anfitrión puede realizar esta acción."); }
}
