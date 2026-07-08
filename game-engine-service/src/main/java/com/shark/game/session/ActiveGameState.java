package com.shark.game.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveGameState {
    private UUID sessionId;
    private String roomId;
    
    @Builder.Default 
    private Map<UUID, SharkState> sharks = new ConcurrentHashMap<>();
    
    @Builder.Default 
    private List<ResourceOnMap> resources = new CopyOnWriteArrayList<>();
    
    private Instant startedAt;
    
    @Builder.Default 
    private int timeLimitSeconds = 300;
}
