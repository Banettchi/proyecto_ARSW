package com.shark.profile.dto.events;

import java.util.UUID;

public record PlayerResult(
        UUID userId,
        Integer finalPosition,
        Long score,
        Integer maxSizeReached
) {}
