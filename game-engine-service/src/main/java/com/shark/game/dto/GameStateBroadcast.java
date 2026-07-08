package com.shark.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class GameStateBroadcast {
    private List<SharkPositionDto> sharks;
    private List<ResourceDto> resources;
    private int secondsRemaining;
}
