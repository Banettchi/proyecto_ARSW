package com.shark.auth.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Este correo ya esta registrado");
    }
}
