package com.shark.profile.dto;

import java.util.List;

public record RankingPageResponse(
        List<RankingEntryResponse> topPlayers,
        Integer myPosition,
        Long myTotalScore
) {}
