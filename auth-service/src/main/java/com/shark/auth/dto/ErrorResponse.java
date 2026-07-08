package com.shark.auth.dto;

import java.time.Instant;

public record ErrorResponse(
        String message,
        String field,
        Instant timestamp
) {}
