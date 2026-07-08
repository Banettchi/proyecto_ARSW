package com.shark.game.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceOnMap {
    private UUID resourceId;
    private double x;
    private double y;
    private ResourceType type;
}
