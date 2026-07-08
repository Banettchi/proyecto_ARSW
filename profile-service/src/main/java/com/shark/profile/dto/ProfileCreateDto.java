package com.shark.profile.dto;

import java.util.UUID;

public record ProfileCreateDto(
        UUID userId,
        String username,
        String sharkName
) {}
