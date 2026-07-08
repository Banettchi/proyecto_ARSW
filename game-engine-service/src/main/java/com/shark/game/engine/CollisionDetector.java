package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.ResourceOnMap;
import com.shark.game.session.SharkState;
import com.shark.game.session.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CollisionDetector {

    private static final Logger logger = LoggerFactory.getLogger(CollisionDetector.class);

    // Distancia al cuadrado (15 unidades)
    private static final double COLLISION_RADIUS_SQ = 15.0 * 15.0;

    public void checkResourceCollisions(ActiveGameState state, UUID userId) {
        SharkState shark = state.getSharks().get(userId);
        if (shark == null || !shark.isAlive()) return;

        List<ResourceOnMap> resources = state.getResources();
        
        for (ResourceOnMap res : resources) {
            double dx = res.getX() - shark.getX();
            double dy = res.getY() - shark.getY();
            
            if (dx * dx + dy * dy <= COLLISION_RADIUS_SQ) {
                // Lock por resourceId para que solo un tiburón gane la carrera
                synchronized (res.getResourceId().toString().intern()) {
                    if (resources.contains(res)) {
                        applyResourceEffect(shark, res);
                        resources.remove(res);
                    }
                }
            }
        }
    }

    private void applyResourceEffect(SharkState shark, ResourceOnMap res) {
        if (res.getType() == ResourceType.FISH || res.getType() == ResourceType.ALGA) {
            shark.setResourcesConsumed(shark.getResourcesConsumed() + 1);
            shark.setSize(shark.getSize() + 1.0);
            
            if (shark.getSize() > shark.getMaxSizeReached()) {
                shark.setMaxSizeReached(shark.getSize());
            }
            
            // Incrementa score (+10)
            shark.setScore(shark.getScore() + 10);
        } else if (res.getType() == ResourceType.POLLUTION_OBSTACLE) {
            // Penalización
            shark.setSize(Math.max(5.0, shark.getSize() - 2.0));
            logger.info("Shark {} colisionó con contaminación. Size reducido a {}", shark.getUserId(), shark.getSize());
        }
    }

    public void checkSharkCollisions(ActiveGameState state, UUID userId) {
        SharkState shark = state.getSharks().get(userId);
        if (shark == null || !shark.isAlive()) return;

        for (SharkState other : state.getSharks().values()) {
            if (other.getUserId().equals(shark.getUserId()) || !other.isAlive()) continue;

            double dx = other.getX() - shark.getX();
            double dy = other.getY() - shark.getY();
            
            // El radio de colisión se basa en el tamaño de ambos tiburones
            double combinedRadius = (shark.getSize() + other.getSize()) / 2.0; 
            
            if (dx * dx + dy * dy <= combinedRadius * combinedRadius) {
                // Lock en el ID de usuario mayor siempre primero para evitar Deadlocks bidireccionales
                UUID firstLock = shark.getUserId().compareTo(other.getUserId()) > 0 ? shark.getUserId() : other.getUserId();
                UUID secondLock = shark.getUserId().compareTo(other.getUserId()) > 0 ? other.getUserId() : shark.getUserId();
                
                synchronized (firstLock.toString().intern()) {
                    synchronized (secondLock.toString().intern()) {
                        resolveSharkCombat(shark, other);
                    }
                }
            }
        }
    }

    private void resolveSharkCombat(SharkState s1, SharkState s2) {
        if (!s1.isAlive() || !s2.isAlive()) return;

        SharkState winner = null;
        SharkState loser = null;

        if (s1.getSize() > s2.getSize()) {
            winner = s1; loser = s2;
        } else if (s2.getSize() > s1.getSize()) {
            winner = s2; loser = s1;
        } else {
            // Regla de desempate por score acumulado (HU-05c/KAN-16)
            if (s1.getScore() >= s2.getScore()) {
                winner = s1; loser = s2;
            } else {
                winner = s2; loser = s1;
            }
        }

        loser.setAlive(false);
        winner.setSize(winner.getSize() + (loser.getSize() * 0.20)); // Absorbe 20%
        
        if (winner.getSize() > winner.getMaxSizeReached()) {
            winner.setMaxSizeReached(winner.getSize());
        }
        
        winner.setRivalsEliminated(winner.getRivalsEliminated() + 1);
        winner.setScore(winner.getScore() + 50);
    }
}
