package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MovementValidator {
    
    // Velocidad máxima configurable: 150 unidades por segundo
    private static final double MAX_UNITS_PER_SECOND = 150.0; 
    
    public boolean validateAndApplyMove(ActiveGameState state, UUID userId, double newX, double newY, long clientTimestamp) {
        SharkState shark = state.getSharks().get(userId);
        
        // Valida que el shark exista y esté vivo
        if (shark == null || !shark.isAlive()) {
            return false;
        }

        long timeElapsedMs = clientTimestamp - shark.getLastClientTimestamp();
        
        // Primer movimiento (timestamp == 0)
        if (shark.getLastClientTimestamp() == 0) {
            applyMove(shark, newX, newY, clientTimestamp);
            return true;
        }
        
        // Fallback por si hay desincronización de reloj grave (ej. trampa de tiempo)
        if (timeElapsedMs < 0 || timeElapsedMs > 5000) {
            return false;
        }

        double dx = newX - shark.getX();
        double dy = newY - shark.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Distancia máxima físicamente posible dado el tiempo transcurrido
        double maxAllowedDistance = MAX_UNITS_PER_SECOND * (timeElapsedMs / 1000.0);
        
        // Añadimos un buffer de 20% para compensar jittering de latencia normal
        if (distance <= maxAllowedDistance * 1.2) {
            applyMove(shark, newX, newY, clientTimestamp);
            return true;
        }
        
        // Si es inválido, NO actualizamos nada. 
        // El cliente que intentó la trampa no recibe confirmación.
        return false;
    }
    
    private void applyMove(SharkState shark, double x, double y, long timestamp) {
        shark.setX(x);
        shark.setY(y);
        shark.setLastClientTimestamp(timestamp);
        shark.setLastSeenAt(Instant.now());
    }
}
