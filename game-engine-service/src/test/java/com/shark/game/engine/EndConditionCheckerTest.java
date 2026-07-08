package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndConditionCheckerTest {

    private EndConditionChecker checker;
    private ActiveGameState state;

    @BeforeEach
    void setUp() {
        checker = new EndConditionChecker();
        state = new ActiveGameState();
    }

    @Test
    void checkTimeLimit_NotExpired_ReturnsFalse() {
        // Empezó hace 10 segundos, dura 300
        state.setStartedAt(Instant.now().minusSeconds(10));
        state.setTimeLimitSeconds(300);
        
        assertFalse(checker.checkTimeLimit(state));
    }

    @Test
    void checkTimeLimit_Expired_ReturnsTrue() {
        // Empezó hace 310 segundos, dura 300
        state.setStartedAt(Instant.now().minusSeconds(310));
        state.setTimeLimitSeconds(300);
        
        assertTrue(checker.checkTimeLimit(state));
    }

    @Test
    void checkLastPlayerStanding_MoreThanOneAlive_ReturnsFalse() {
        SharkState s1 = SharkState.builder().alive(true).build();
        SharkState s2 = SharkState.builder().alive(true).build();
        
        state.getSharks().put(UUID.randomUUID(), s1);
        state.getSharks().put(UUID.randomUUID(), s2);
        
        assertFalse(checker.checkLastPlayerStanding(state));
    }

    @Test
    void checkLastPlayerStanding_OnlyOneAlive_ReturnsTrue() {
        SharkState s1 = SharkState.builder().alive(true).build();
        SharkState s2 = SharkState.builder().alive(false).build();
        
        state.getSharks().put(UUID.randomUUID(), s1);
        state.getSharks().put(UUID.randomUUID(), s2);
        
        assertTrue(checker.checkLastPlayerStanding(state));
    }
}
