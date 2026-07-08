package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.ResourceOnMap;
import com.shark.game.session.ResourceType;
import com.shark.game.session.SharkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollisionDetectorTest {

    private CollisionDetector detector;
    private ActiveGameState state;

    @BeforeEach
    void setUp() {
        detector = new CollisionDetector();
        state = new ActiveGameState();
    }

    @Test
    void checkResourceCollisions_SimultaneousCollision_OnlyOneSharkBenefits() throws InterruptedException {
        UUID resId = UUID.randomUUID();
        ResourceOnMap fish = new ResourceOnMap(resId, 0.0, 0.0, ResourceType.FISH);
        state.getResources().add(fish);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        
        SharkState shark1 = SharkState.builder().userId(user1).x(0.0).y(0.0).size(10.0).resourcesConsumed(0).build();
        SharkState shark2 = SharkState.builder().userId(user2).x(0.0).y(0.0).size(10.0).resourcesConsumed(0).build();
        
        state.getSharks().put(user1, shark1);
        state.getSharks().put(user2, shark2);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        Runnable task1 = () -> {
            try { latch.await(); } catch (InterruptedException ignored) {}
            detector.checkResourceCollisions(state, user1);
            done.countDown();
        };

        Runnable task2 = () -> {
            try { latch.await(); } catch (InterruptedException ignored) {}
            detector.checkResourceCollisions(state, user2);
            done.countDown();
        };

        executor.submit(task1);
        executor.submit(task2);
        
        // Desatamos ambos hilos a la vez
        latch.countDown();
        done.await(2, TimeUnit.SECONDS);

        // El pez solo pudo ser consumido 1 vez. Por lo tanto, un tiburón tendrá 1 y el otro 0.
        int totalConsumed = shark1.getResourcesConsumed() + shark2.getResourcesConsumed();
        double totalSize = shark1.getSize() + shark2.getSize();

        assertEquals(1, totalConsumed, "Solo un pez debe haber sido consumido a pesar del acceso concurrente");
        assertEquals(21.0, totalSize, "El tamaño total de ambos tiburones debe ser 10 + 11 = 21");
        assertEquals(0, state.getResources().size(), "El pez debió ser removido del estado");
    }
}
