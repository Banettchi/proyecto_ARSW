package com.shark.profile.dto;

public record RankingEntryResponse(
        Integer position,
        String username,
        Long totalScore,
        Integer gamesPlayed
) {}
