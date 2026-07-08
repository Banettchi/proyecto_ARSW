package com.shark.auth.exception;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException() {
        super("Este nombre de usuario ya esta en uso");
    }
}
