package com.shark.game.engine;

import com.shark.game.session.SharkState;
import org.springframework.stereotype.Component;

@Component
public class ScoreCalculator {

    public long calculateFinalScore(SharkState shark, boolean isSurvivor) {
        // Fórmula exacta documentada en Overleaf
        long score = (shark.getResourcesConsumed() * 10L) 
                   + (shark.getRivalsEliminated() * 50L) 
                   + (long) shark.getMaxSizeReached();
                   
        if (isSurvivor) {
            score += 100L;
        }
        
        return score;
    }
}
