package com.shark.game.listener;

import com.shark.game.controller.GameController;
import com.shark.game.manager.ActiveGameManager;
import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Component
public class GameDisconnectListener {
    
    private static final Logger logger = LoggerFactory.getLogger(GameDisconnectListener.class);
    
    private final ActiveGameManager gameManager;
    private final GameController gameController;

    public GameDisconnectListener(ActiveGameManager gameManager, GameController gameController) {
        this.gameManager = gameManager;
        this.gameController = gameController;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) return;

        UUID userId = UUID.fromString(user.getName());
        String roomId = gameManager.getRoomIdForUser(userId);
        
        if (roomId != null) {
            ActiveGameState state = gameManager.getGameState(roomId);
            if (state != null) {
                SharkState shark = state.getSharks().get(userId);
                if (shark != null && shark.isAlive()) {
                    shark.setConnected(false);
                    shark.setLastSeenAt(Instant.now());
                    logger.info("Usuario {} desconectado. Congelado en grace period (roomId: {}).", userId, roomId);
                    
                    // Hace broadcast para que los demás vean el indicador visual de desconexión
                    gameController.broadcastState(roomId, state);
                }
            }
        }
    }
}
