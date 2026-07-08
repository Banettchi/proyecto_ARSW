package com.shark.profile.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID userId,
        String sharkName,
        String colorHex,
        Integer level,
        Integer experience,
        Long totalScore
) {}
