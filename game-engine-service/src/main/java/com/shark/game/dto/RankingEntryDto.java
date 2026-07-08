package com.shark.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class RankingEntryDto {
    private UUID userId;
    private String username;
    private int position;
    private long score;
}
