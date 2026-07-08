package com.shark.profile.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateColorRequest(
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser un código hexadecimal válido (ej. #00D2FF)")
        String colorHex
) {}
