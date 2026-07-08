package com.shark.game.engine;

import com.shark.game.session.SharkState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    void calculateFinalScore_WithoutSurvivalBonus() {
        SharkState shark = SharkState.builder()
                .resourcesConsumed(5) // 5 * 10 = 50
                .rivalsEliminated(2) // 2 * 50 = 100
                .maxSizeReached(25.0) // +25
                .build();
                
        // Total esperado: 50 + 100 + 25 = 175
        long score = calculator.calculateFinalScore(shark, false);
        assertEquals(175L, score);
    }

    @Test
    void calculateFinalScore_WithSurvivalBonus() {
        SharkState shark = SharkState.builder()
                .resourcesConsumed(10) // 10 * 10 = 100
                .rivalsEliminated(3) // 3 * 50 = 150
                .maxSizeReached(45.0) // +45
                .build();
                
        // Total esperado: 100 + 150 + 45 + 100(bonus) = 395
        long score = calculator.calculateFinalScore(shark, true);
        assertEquals(395L, score);
    }
}
