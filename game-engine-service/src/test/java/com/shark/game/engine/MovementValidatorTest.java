package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementValidatorTest {

    private MovementValidator validator;
    private ActiveGameState state;
    private UUID userId;
    private SharkState shark;

    @BeforeEach
    void setUp() {
        validator = new MovementValidator();
        state = new ActiveGameState();
        userId = UUID.randomUUID();
        
        shark = SharkState.builder()
                .userId(userId)
                .x(0.0)
                .y(0.0)
                .lastClientTimestamp(1000L) // Estado inicial: t=1000
                .alive(true)
                .build();
                
        state.getSharks().put(userId, shark);
    }

    @Test
    void validateAndApplyMove_ValidMove_ReturnsTrueAndUpdatesPos() {
        // Mueve 50 unidades en 1 segundo (1000ms), velocidad max es 150u/s
        boolean result = validator.validateAndApplyMove(state, userId, 50.0, 0.0, 2000L);
        
        assertTrue(result);
        assertTrue(shark.getX() == 50.0);
        assertTrue(shark.getLastClientTimestamp() == 2000L);
    }

    @Test
    void validateAndApplyMove_CheatMove_ReturnsFalseAndIgnoresPos() {
        // Mueve 500 unidades en 1 segundo (500u/s), superior al max de 150u/s (+ 20% grace)
        boolean result = validator.validateAndApplyMove(state, userId, 500.0, 0.0, 2000L);
        
        assertFalse(result, "Movimiento trampa debe ser rechazado");
        assertTrue(shark.getX() == 0.0, "La posición original no debe alterarse");
        assertTrue(shark.getLastClientTimestamp() == 1000L, "El timestamp original no debe alterarse");
    }

    @Test
    void validateAndApplyMove_DeadShark_ReturnsFalse() {
        shark.setAlive(false);
        boolean result = validator.validateAndApplyMove(state, userId, 10.0, 0.0, 2000L);
        assertFalse(result);
    }
}
