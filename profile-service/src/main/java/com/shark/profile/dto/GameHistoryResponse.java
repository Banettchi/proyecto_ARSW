package com.shark.profile.dto;

import java.time.Instant;
import java.util.UUID;

public record GameHistoryResponse(
        UUID sessionId,
        Instant playedAt,
        Integer finalPosition,
        Long sessionScore,
        Integer maxSizeReached
) {}
