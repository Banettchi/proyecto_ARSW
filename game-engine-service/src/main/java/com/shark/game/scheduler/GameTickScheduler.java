package com.shark.game.scheduler;

import com.shark.game.controller.GameController;
import com.shark.game.engine.EndConditionChecker;
import com.shark.game.manager.ActiveGameManager;
import com.shark.game.session.ActiveGameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GameTickScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GameTickScheduler.class);
    
    private final ActiveGameManager gameManager;
    private final EndConditionChecker endConditionChecker;
    private final GameController gameController;

    public GameTickScheduler(ActiveGameManager gameManager, EndConditionChecker endConditionChecker, GameController gameController) {
        this.gameManager = gameManager;
        this.endConditionChecker = endConditionChecker;
        this.gameController = gameController;
    }

    @Scheduled(fixedRate = 1000)
    public void tickAllGames() {
        for (Map.Entry<String, ActiveGameState> entry : gameManager.getAllGames().entrySet()) {
            String roomId = entry.getKey();
            ActiveGameState state = entry.getValue();
            
            boolean timeEnded = endConditionChecker.checkTimeLimit(state);
            boolean lastStanding = endConditionChecker.checkLastPlayerStanding(state);
            
            if (timeEnded || lastStanding) {
                String trigger = timeEnded ? "TIME_LIMIT" : "LAST_PLAYER";
                logger.info("GameTickScheduler cerró la sala {} por {}", roomId, trigger);
                gameController.finishGame(roomId, trigger, state);
            }
        }
    }
}
