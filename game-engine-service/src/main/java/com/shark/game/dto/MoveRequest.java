package com.shark.game.dto;

import lombok.Data;

@Data
public class MoveRequest {
    private double x;
    private double y;
    private long timestamp;
}
