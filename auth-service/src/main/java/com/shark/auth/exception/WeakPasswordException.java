package com.shark.auth.exception;

public class WeakPasswordException extends RuntimeException {
    public WeakPasswordException() {
        super("La contraseña debe tener al menos 8 caracteres");
    }
}
