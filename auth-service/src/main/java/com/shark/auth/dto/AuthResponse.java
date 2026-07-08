package com.shark.auth.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String username
) {}
