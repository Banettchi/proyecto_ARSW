package com.shark.game.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ResourceDto {
    private UUID resourceId;
    private double x;
    private double y;
    private String type;
}
