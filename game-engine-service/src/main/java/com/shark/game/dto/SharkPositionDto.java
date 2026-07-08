package com.shark.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SharkPositionDto {
    private UUID userId;
    private double x;
    private double y;
    private double size;
    private boolean alive;
}
