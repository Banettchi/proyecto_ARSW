package com.shark.game.dto.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GameSessionFinishedEventPayload(
        UUID sessionId,
        String roomId,
        List<PlayerResult> results,
        Instant finishedAt
) {}
