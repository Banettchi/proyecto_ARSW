package com.shark.lobby.dto.events;

import java.time.Instant;

public record RoomCreatedEventPayload(
        String roomId,
        String hostUsername,
        Instant createdAt
) {}
