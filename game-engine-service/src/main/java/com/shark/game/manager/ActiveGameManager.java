package com.shark.game.manager;

import com.shark.game.session.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveGameManager {
    
    // ConcurrentHashMap es thread-safe, sin locks manuales en get/put
    private final ConcurrentHashMap<String, ActiveGameState> activeGames = new ConcurrentHashMap<>();
    
    // Mapa auxiliar para búsqueda O(1) durante desconexiones/reconexiones
    private final ConcurrentHashMap<UUID, String> userToRoomMap = new ConcurrentHashMap<>();

    public ActiveGameState startSession(String roomId, List<PlayerInfo> players) {
        ActiveGameState state = ActiveGameState.builder()
                .sessionId(UUID.randomUUID())
                .roomId(roomId)
                .startedAt(Instant.now())
                .build();
        
        double currentX = 0.0;
        for (PlayerInfo p : players) {
            SharkState shark = SharkState.builder()
                    .userId(p.getUserId())
                    .username(p.getUsername())
                    .x(currentX) // Posicionamiento básico disperso (a mejorar con spawn points)
                    .y(0.0)
                    .build();
            state.getSharks().put(p.getUserId(), shark);
            userToRoomMap.put(p.getUserId(), roomId);
            currentX += 50.0; 
        }

        // Genera recursos en el mapa de forma aleatoria
        for (int i = 0; i < 50; i++) {
            ResourceType type = (Math.random() > 0.1) ? ResourceType.FISH : ResourceType.POLLUTION_OBSTACLE;
            state.getResources().add(new ResourceOnMap(
                    UUID.randomUUID(),
                    Math.random() * 1000 - 500, // X: -500 a 500
                    Math.random() * 1000 - 500, // Y: -500 a 500
                    type
            ));
        }

        activeGames.put(roomId, state);
        
        // El scheduled task interno se abordará en un GameLoop/Scheduler que use este manager.
        return state;
    }
    
    public ActiveGameState getGameState(String roomId) {
        return activeGames.get(roomId);
    }
    
    public void removeGameState(String roomId) {
        ActiveGameState state = activeGames.remove(roomId);
        if (state != null) {
            state.getSharks().keySet().forEach(userToRoomMap::remove);
        }
    }
    
    public String getRoomIdForUser(UUID userId) {
        return userToRoomMap.get(userId);
    }
    
    public ConcurrentHashMap<String, ActiveGameState> getAllGames() {
        return activeGames;
    }
}
