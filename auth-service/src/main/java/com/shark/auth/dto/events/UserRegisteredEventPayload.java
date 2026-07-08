package com.shark.auth.dto.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEventPayload(
        UUID userId,
        String username,
        String email,
        Instant registeredAt
) {}
