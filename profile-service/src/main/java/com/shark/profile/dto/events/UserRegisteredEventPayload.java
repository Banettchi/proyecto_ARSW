package com.shark.profile.dto.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEventPayload(
        UUID userId,
        String username,
        String email,
        Instant registeredAt
) {}
