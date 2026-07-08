package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EndConditionChecker {

    /*
     * Documentación de Arquitectura:
     * Ambas condiciones (checkTimeLimit y checkLastPlayerStanding) se revisan:
     * 1) Tras CADA movimiento procesado en el GameLoop (para reaccionar instantáneamente
     *    a la eliminación de un tiburón sin esperar un tick extra).
     * 2) En un scheduled task de respaldo cada 1 segundo (por si la partida se acaba
     *    por tiempo límite en un instante donde ningún jugador se está moviendo).
     */

    public boolean checkTimeLimit(ActiveGameState state) {
        Instant endTime = state.getStartedAt().plusSeconds(state.getTimeLimitSeconds());
        return Instant.now().isAfter(endTime);
    }

    public boolean checkLastPlayerStanding(ActiveGameState state) {
        long aliveCount = state.getSharks().values().stream()
                .filter(SharkState::isAlive)
                .count();
        
        return aliveCount <= 1;
    }
}
