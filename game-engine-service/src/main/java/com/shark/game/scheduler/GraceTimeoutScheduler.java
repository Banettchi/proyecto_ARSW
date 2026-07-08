package com.shark.game.scheduler;

import com.shark.game.controller.GameController;
import com.shark.game.engine.EndConditionChecker;
import com.shark.game.manager.ActiveGameManager;
import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class GraceTimeoutScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GraceTimeoutScheduler.class);
    
    private final ActiveGameManager gameManager;
    private final GameController gameController;
    private final EndConditionChecker endConditionChecker;

    public GraceTimeoutScheduler(ActiveGameManager gameManager, GameController gameController, EndConditionChecker endConditionChecker) {
        this.gameManager = gameManager;
        this.gameController = gameController;
        this.endConditionChecker = endConditionChecker;
    }

    @Scheduled(fixedRate = 5000)
    public void checkGraceTimeouts() {
        for (Map.Entry<String, ActiveGameState> entry : gameManager.getAllGames().entrySet()) {
            String roomId = entry.getKey();
            ActiveGameState state = entry.getValue();
            boolean stateChanged = false;

            for (SharkState shark : state.getSharks().values()) {
                if (!shark.isConnected() && shark.isAlive()) {
                    long disconnectedSeconds = Duration.between(shark.getLastSeenAt(), Instant.now()).getSeconds();
                    
                    if (disconnectedSeconds > 30) {
                        logger.info("Grace period (30s) expirado para usuario {}. Marcado como eliminado.", shark.getUserId());
                        shark.setAlive(false); // Como si hubiera sido consumido
                        stateChanged = true;
                    }
                }
            }

            if (stateChanged) {
                // Broadcast del estado actualizado
                gameController.broadcastState(roomId, state);
                
                // Si esto deja solo 1 shark vivo, dispara fin de partida
                if (endConditionChecker.checkLastPlayerStanding(state)) {
                    logger.info("GraceTimeoutScheduler disparando fin de partida por LAST_PLAYER en sala {}", roomId);
                    gameController.finishGame(roomId, "LAST_PLAYER", state);
                }
            }
        }
    }
}
