package com.shark.profile.exception;

public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException() {
        super("El perfil del jugador no fue encontrado");
    }
}
