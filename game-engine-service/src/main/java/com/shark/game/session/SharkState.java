package com.shark.game.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharkState {
    private UUID userId;
    private String username;
    private double x;
    private double y;
    
    @Builder.Default private double size = 10.0;
    @Builder.Default private double maxSizeReached = 10.0;
    
    @Builder.Default private long score = 0;
    @Builder.Default private int resourcesConsumed = 0;
    @Builder.Default private int rivalsEliminated = 0;
    
    @Builder.Default private boolean alive = true;
    @Builder.Default private boolean connected = true;
    
    @Builder.Default private Instant lastSeenAt = Instant.now();
    @Builder.Default private long lastClientTimestamp = 0;
}
