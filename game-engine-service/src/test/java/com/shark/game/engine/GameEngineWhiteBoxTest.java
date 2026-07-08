package com.shark.game.engine;

import com.shark.game.session.ActiveGameState;
import com.shark.game.session.ResourceOnMap;
import com.shark.game.session.ResourceType;
import com.shark.game.session.SharkState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ======================== PRUEBAS UNITARIAS / CAJA BLANCA (AVANZADAS) ========================
 *
 * Tipo: Unitarias (Unit Tests) + Caja Blanca (White-Box)
 *
 * Justificación:
 * - Cobertura completa de ramas internas del motor de juego.
 * - Prueba de valores límite (boundary testing) en las constantes de física.
 * - Se verifica cada bifurcación interna: colisión recurso vs obstáculo, combate
 *   por tamaño vs empate por score, tiburón muerto vs vivo.
 * - Sin mocks: clases del motor son lógica pura sin dependencias Spring.
 *
 * CORRECCIÓN (develop):
 * - ActiveGameState requiere setStartedAt() via @Data — verificado OK
 * - SharkState builder con campos correctos — verificado OK
 * - Eliminado test de "exactBoundary" vacío que no hacía ningún assertion
 */
class GameEngineWhiteBoxTest {

    // =================== MOVEMENT VALIDATOR ===================

    @Nested
    @DisplayName("MovementValidator - Caja Blanca: todas las ramas")
    class MovementValidatorWhiteBox {

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
                    .userId(userId).x(0.0).y(0.0)
                    .lastClientTimestamp(1000L)
                    .alive(true).connected(true)
                    .build();
            state.getSharks().put(userId, shark);
        }

        @Test
        @DisplayName("Rama: Primer movimiento (lastTimestamp=0) siempre se acepta sin importar distancia")
        void firstMove_AlwaysAccepted() {
            shark.setLastClientTimestamp(0);
            boolean result = validator.validateAndApplyMove(state, userId, 999.0, 999.0, 500L);
            assertTrue(result, "El primer movimiento debe aceptarse siempre");
            // Verifica que la posición se actualizó
            assertEquals(999.0, shark.getX());
            assertEquals(999.0, shark.getY());
        }

        @Test
        @DisplayName("Rama: timeElapsedMs negativo (trampa de timestamp) -> rechazado")
        void negativeTimeElapsed_Rejected() {
            // t_anterior = 1000, t_nuevo = 500 → elapsed = -500 < 0
            boolean result = validator.validateAndApplyMove(state, userId, 10.0, 0.0, 500L);
            assertFalse(result, "Timestamp retrocedido en el tiempo debe ser rechazado");
            assertEquals(0.0, shark.getX(), "Posición NO debe modificarse");
        }

        @Test
        @DisplayName("Rama: timeElapsedMs > 5000ms (desincronización grave) -> rechazado")
        void excessiveTimeElapsed_Rejected() {
            // t_anterior = 1000, t_nuevo = 7000 → elapsed = 6000 > 5000
            boolean result = validator.validateAndApplyMove(state, userId, 10.0, 0.0, 7000L);
            assertFalse(result, "Diferencia de tiempo > 5000ms debe ser rechazada");
        }

        @Test
        @DisplayName("Rama: Velocidad en el límite con buffer 20% (180 u/s exactos) -> aceptado")
        void exactBoundaryWithBuffer_Accepted() {
            // MAX = 150 u/s, elapsed = 1000ms = 1s, maxAllow = 150 * 1.2 = 180
            boolean result = validator.validateAndApplyMove(state, userId, 180.0, 0.0, 2000L);
            assertTrue(result, "Movimiento exactamente en el borde del buffer debe aceptarse");
            assertEquals(180.0, shark.getX());
        }

        @Test
        @DisplayName("Rama: Velocidad por encima del buffer (180.1 u/s) -> rechazado")
        void justAboveBoundary_Rejected() {
            boolean result = validator.validateAndApplyMove(state, userId, 180.1, 0.0, 2000L);
            assertFalse(result, "Movimiento justo arriba del límite debe ser rechazado");
            assertEquals(0.0, shark.getX(), "Posición NO debe cambiar al rechazar");
        }

        @Test
        @DisplayName("Rama: userId inexistente en el mapa -> rechazado")
        void unknownUserId_Rejected() {
            boolean result = validator.validateAndApplyMove(state, UUID.randomUUID(), 10.0, 0.0, 2000L);
            assertFalse(result);
        }

        @Test
        @DisplayName("Rama: Shark muerto no puede moverse")
        void deadShark_Rejected() {
            shark.setAlive(false);
            boolean result = validator.validateAndApplyMove(state, userId, 10.0, 0.0, 2000L);
            assertFalse(result);
        }
    }

    // =================== COLLISION DETECTOR ===================

    @Nested
    @DisplayName("CollisionDetector - Caja Blanca: efectos por tipo de recurso")
    class CollisionDetectorWhiteBox {

        private CollisionDetector detector;
        private ActiveGameState state;

        @BeforeEach
        void setUp() {
            detector = new CollisionDetector();
            state = new ActiveGameState();
        }

        @Test
        @DisplayName("Rama FISH: incrementa resourcesConsumed, size (+1) y score (+10)")
        void fishCollision_IncreasesAllStats() {
            UUID userId = UUID.randomUUID();
            SharkState shark = SharkState.builder()
                    .userId(userId).x(0.0).y(0.0).size(10.0)
                    .resourcesConsumed(0).score(0).maxSizeReached(10.0)
                    .alive(true).build();
            state.getSharks().put(userId, shark);
            state.getResources().add(new ResourceOnMap(UUID.randomUUID(), 0.0, 0.0, ResourceType.FISH));

            detector.checkResourceCollisions(state, userId);

            assertEquals(1, shark.getResourcesConsumed(), "Debe incrementar recursos consumidos");
            assertEquals(11.0, shark.getSize(), "Tamaño debe crecer +1");
            assertEquals(10, shark.getScore(), "Score debe sumar +10 por pez");
            assertEquals(11.0, shark.getMaxSizeReached(), "maxSizeReached debe actualizarse");
        }

        @Test
        @DisplayName("Rama ALGA: mismo efecto que FISH")
        void algaCollision_IncreasesStatsLikeFish() {
            UUID userId = UUID.randomUUID();
            SharkState shark = SharkState.builder()
                    .userId(userId).x(0.0).y(0.0).size(10.0)
                    .resourcesConsumed(0).score(0).maxSizeReached(10.0)
                    .alive(true).build();
            state.getSharks().put(userId, shark);
            state.getResources().add(new ResourceOnMap(UUID.randomUUID(), 0.0, 0.0, ResourceType.ALGA));

            detector.checkResourceCollisions(state, userId);

            assertEquals(1, shark.getResourcesConsumed());
            assertEquals(11.0, shark.getSize());
        }

        @Test
        @DisplayName("Rama POLLUTION_OBSTACLE: reduce size con mínimo 5.0")
        void pollutionCollision_ReducesSizeWithMinimumClamp() {
            UUID userId = UUID.randomUUID();
            SharkState shark = SharkState.builder()
                    .userId(userId).x(0.0).y(0.0).size(6.0)
                    .resourcesConsumed(0).alive(true).build();
            state.getSharks().put(userId, shark);
            state.getResources().add(new ResourceOnMap(UUID.randomUUID(), 0.0, 0.0, ResourceType.POLLUTION_OBSTACLE));

            detector.checkResourceCollisions(state, userId);

            assertEquals(5.0, shark.getSize(), "Size no debe bajar de 5.0 (clamp al mínimo)");
            assertEquals(0, shark.getResourcesConsumed(), "Contaminación NO suma resourcesConsumed");
            assertEquals(0, shark.getScore(), "Contaminación NO suma score");
        }

        @Test
        @DisplayName("Rama: Recurso fuera del radio de colisión (15u) -> no se consume")
        void resourceOutOfRange_NotConsumed() {
            UUID userId = UUID.randomUUID();
            SharkState shark = SharkState.builder()
                    .userId(userId).x(0.0).y(0.0).size(10.0)
                    .alive(true).build();
            state.getSharks().put(userId, shark);
            state.getResources().add(new ResourceOnMap(UUID.randomUUID(), 100.0, 100.0, ResourceType.FISH));

            detector.checkResourceCollisions(state, userId);

            assertEquals(0, shark.getResourcesConsumed());
            assertEquals(1, state.getResources().size(), "Recurso fuera de rango no debe removerse");
        }

        @Test
        @DisplayName("Combate: Tiburón más grande gana y absorbe 20% del tamaño del perdedor")
        void sharkCombat_BiggerWins_Absorbs20Percent() {
            UUID bigId = UUID.randomUUID();
            UUID smallId = UUID.randomUUID();

            SharkState big = SharkState.builder()
                    .userId(bigId).x(0.0).y(0.0).size(20.0)
                    .score(100).rivalsEliminated(0).maxSizeReached(20.0)
                    .alive(true).build();
            SharkState small = SharkState.builder()
                    .userId(smallId).x(1.0).y(0.0).size(10.0)
                    .score(50).alive(true).build();

            state.getSharks().put(bigId, big);
            state.getSharks().put(smallId, small);

            detector.checkSharkCollisions(state, bigId);

            assertTrue(big.isAlive());
            assertFalse(small.isAlive());
            assertEquals(22.0, big.getSize(), 0.001, "20 + (10 * 0.20) = 22");
            assertEquals(1, big.getRivalsEliminated());
            assertEquals(150, big.getScore(), "100 base + 50 bonus por eliminación = 150");
        }

        @Test
        @DisplayName("Combate empate de tamaño: desempata por score más alto (HU-05c/KAN-16)")
        void sharkCombat_SameSize_HigherScoreWins() {
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();

            SharkState s1 = SharkState.builder()
                    .userId(u1).x(0.0).y(0.0).size(15.0)
                    .score(200).rivalsEliminated(0).maxSizeReached(15.0)
                    .alive(true).build();
            SharkState s2 = SharkState.builder()
                    .userId(u2).x(1.0).y(0.0).size(15.0)
                    .score(100).alive(true).maxSizeReached(15.0).build();

            state.getSharks().put(u1, s1);
            state.getSharks().put(u2, s2);

            detector.checkSharkCollisions(state, u1);

            assertTrue(s1.isAlive(), "s1 con score 200 debe sobrevivir");
            assertFalse(s2.isAlive(), "s2 con score 100 debe morir");
        }
    }

    // =================== SCORE CALCULATOR ===================

    @Nested
    @DisplayName("ScoreCalculator - Caja Blanca: fórmula exacta documentada")
    class ScoreCalculatorWhiteBox {

        private final ScoreCalculator calc = new ScoreCalculator();

        @Test
        @DisplayName("Sin bonus: (resources*10) + (rivals*50) + maxSize")
        void formula_WithoutSurvivalBonus() {
            SharkState s = SharkState.builder()
                    .resourcesConsumed(8).rivalsEliminated(3).maxSizeReached(30.0)
                    .build();
            // 8*10 + 3*50 + 30 = 80 + 150 + 30 = 260
            assertEquals(260L, calc.calculateFinalScore(s, false));
        }

        @Test
        @DisplayName("Con bonus supervivencia: +100 al resultado")
        void formula_WithSurvivalBonus() {
            SharkState s = SharkState.builder()
                    .resourcesConsumed(8).rivalsEliminated(3).maxSizeReached(30.0)
                    .build();
            // 260 + 100 = 360
            assertEquals(360L, calc.calculateFinalScore(s, true));
        }

        @Test
        @DisplayName("Caso cero: sin recursos ni eliminaciones ni tamaño extra = 0")
        void formula_ZeroStats_ReturnsZero() {
            SharkState s = SharkState.builder()
                    .resourcesConsumed(0).rivalsEliminated(0).maxSizeReached(0.0)
                    .build();
            assertEquals(0L, calc.calculateFinalScore(s, false));
        }
    }

    // =================== END CONDITION CHECKER ===================

    @Nested
    @DisplayName("EndConditionChecker - Caja Blanca: condiciones de fin de partida")
    class EndConditionCheckerWhiteBox {

        private final EndConditionChecker checker = new EndConditionChecker();

        @Test
        @DisplayName("Tiempo NO expirado (10s de 300s) -> false")
        void timeLimit_NotExpired_ReturnsFalse() {
            ActiveGameState state = new ActiveGameState();
            state.setStartedAt(Instant.now().minusSeconds(10));
            state.setTimeLimitSeconds(300);
            assertFalse(checker.checkTimeLimit(state));
        }

        @Test
        @DisplayName("Tiempo expirado (310s de 300s) -> true")
        void timeLimit_Expired_ReturnsTrue() {
            ActiveGameState state = new ActiveGameState();
            state.setStartedAt(Instant.now().minusSeconds(310));
            state.setTimeLimitSeconds(300);
            assertTrue(checker.checkTimeLimit(state));
        }

        @Test
        @DisplayName("2 jugadores vivos -> false (partida continúa)")
        void lastPlayer_MultipleAlive_ReturnsFalse() {
            ActiveGameState state = new ActiveGameState();
            state.getSharks().put(UUID.randomUUID(), SharkState.builder().alive(true).build());
            state.getSharks().put(UUID.randomUUID(), SharkState.builder().alive(true).build());
            assertFalse(checker.checkLastPlayerStanding(state));
        }

        @Test
        @DisplayName("1 vivo + 2 muertos -> true (hay un ganador)")
        void lastPlayer_OnlyOneAlive_ReturnsTrue() {
            ActiveGameState state = new ActiveGameState();
            state.getSharks().put(UUID.randomUUID(), SharkState.builder().alive(true).build());
            state.getSharks().put(UUID.randomUUID(), SharkState.builder().alive(false).build());
            state.getSharks().put(UUID.randomUUID(), SharkState.builder().alive(false).build());
            assertTrue(checker.checkLastPlayerStanding(state));
        }

        @Test
        @DisplayName("0 jugadores vivos -> true (partida termina)")
        void lastPlayer_NoneAlive_ReturnsTrue() {
            ActiveGameState state = new ActiveGameState();
            state.getSharks().put(UUID.randomUUID(), SharkState.builder().alive(false).build());
            assertTrue(checker.checkLastPlayerStanding(state));
        }

        @Test
        @DisplayName("Mapa vacío -> true (partida sin jugadores termina inmediatamente)")
        void lastPlayer_EmptyMap_ReturnsTrue() {
            ActiveGameState state = new ActiveGameState();
            assertTrue(checker.checkLastPlayerStanding(state));
        }
    }
}
