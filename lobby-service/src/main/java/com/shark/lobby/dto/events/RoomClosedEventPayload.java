package com.shark.lobby.dto.events;

public record RoomClosedEventPayload(
        String roomId,
        String reason
) {}
