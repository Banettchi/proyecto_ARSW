package com.shark.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class GameEndBroadcast {
    private List<RankingEntryDto> finalRanking;
    private String endTrigger;
}
