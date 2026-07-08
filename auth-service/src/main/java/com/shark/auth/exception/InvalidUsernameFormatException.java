package com.shark.auth.exception;

public class InvalidUsernameFormatException extends RuntimeException {
    public InvalidUsernameFormatException() {
        super("El formato del nombre de usuario es inválido");
    }
}
