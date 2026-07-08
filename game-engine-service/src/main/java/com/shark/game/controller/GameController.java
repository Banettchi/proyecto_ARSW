package com.shark.game.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shark.game.dto.*;
import com.shark.game.dto.events.GameSessionFinishedEventPayload;
import com.shark.game.dto.events.PlayerResult;
import com.shark.game.engine.CollisionDetector;
import com.shark.game.engine.EndConditionChecker;
import com.shark.game.engine.MovementValidator;
import com.shark.game.engine.ScoreCalculator;
import com.shark.game.manager.ActiveGameManager;
import com.shark.game.model.GameSession;
import com.shark.game.model.OutboxEvent;
import com.shark.game.model.SessionResult;
import com.shark.game.model.SessionStatus;
import com.shark.game.repository.GameSessionRepository;
import com.shark.game.repository.OutboxEventRepository;
import com.shark.game.repository.SessionResultRepository;
import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
public class GameController {

    private static final Logger logger = LoggerFactory.getLogger(GameController.class);

    private final ActiveGameManager gameManager;
    private final MovementValidator movementValidator;
    private final CollisionDetector collisionDetector;
    private final EndConditionChecker endConditionChecker;
    private final ScoreCalculator scoreCalculator;
    private final SimpMessagingTemplate messagingTemplate;
    
    private final GameSessionRepository gameSessionRepository;
    private final SessionResultRepository sessionResultRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    
    // Para throttling de broadcasts (máximo 1 cada 50ms por sala)
    private final Map<String, Long> lastBroadcastTimes = new ConcurrentHashMap<>();

    public GameController(ActiveGameManager gameManager, MovementValidator movementValidator, 
                          CollisionDetector collisionDetector, EndConditionChecker endConditionChecker, 
                          ScoreCalculator scoreCalculator, SimpMessagingTemplate messagingTemplate,
                          GameSessionRepository gameSessionRepository, SessionResultRepository sessionResultRepository,
                          OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper,
                          TransactionTemplate transactionTemplate) {
        this.gameManager = gameManager;
        this.movementValidator = movementValidator;
        this.collisionDetector = collisionDetector;
        this.endConditionChecker = endConditionChecker;
        this.scoreCalculator = scoreCalculator;
        this.messagingTemplate = messagingTemplate;
        this.gameSessionRepository = gameSessionRepository;
        this.sessionResultRepository = sessionResultRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @MessageMapping("/game/{roomId}/move")
    public void processMove(@DestinationVariable String roomId, @Payload MoveRequest req, SimpMessageHeaderAccessor accessor) {
        UUID userId = UUID.fromString(accessor.getUser().getName());
        ActiveGameState state = gameManager.getGameState(roomId);
        
        if (state == null) {
            return; // Juego no existe o ya terminó
        }

        boolean isValidMove = movementValidator.validateAndApplyMove(state, userId, req.getX(), req.getY(), req.getTimestamp());
        
        if (isValidMove) {
            collisionDetector.checkResourceCollisions(state, userId);
            collisionDetector.checkSharkCollisions(state, userId);
            
            boolean timeEnded = endConditionChecker.checkTimeLimit(state);
            boolean lastStanding = endConditionChecker.checkLastPlayerStanding(state);
            
            if (timeEnded || lastStanding) {
                String trigger = timeEnded ? "TIME_LIMIT" : "LAST_PLAYER";
                finishGame(roomId, trigger, state);
            } else {
                throttledBroadcast(roomId, state);
            }
        }
    }

    /*
     * Decisión de rendimiento: Throttle
     * No hacemos broadcast en CADA mensaje individual si llegan 100 por segundo,
     * para no saturar la red. Se limita a máximo 1 broadcast cada 50ms por sala (aprox 20 TPS).
     */
    private void throttledBroadcast(String roomId, ActiveGameState state) {
        long now = System.currentTimeMillis();
        long lastBroadcast = lastBroadcastTimes.getOrDefault(roomId, 0L);
        
        if (now - lastBroadcast > 50) {
            lastBroadcastTimes.put(roomId, now);
            broadcastState(roomId, state);
        }
    }

    public void broadcastState(String roomId, ActiveGameState state) {
        List<SharkPositionDto> sharks = state.getSharks().values().stream()
                .map(s -> new SharkPositionDto(s.getUserId(), s.getX(), s.getY(), s.getSize(), s.isAlive()))
                .collect(Collectors.toList());
                
        List<ResourceDto> resources = state.getResources().stream()
                .map(r -> new ResourceDto(r.getResourceId(), r.getX(), r.getY(), r.getType().name()))
                .collect(Collectors.toList());
                
        int secondsRemaining = Math.max(0, state.getTimeLimitSeconds() - 
                (int) ChronoUnit.SECONDS.between(state.getStartedAt(), Instant.now()));

        messagingTemplate.convertAndSend("/topic/game/" + roomId, 
                new GameStateBroadcast(sharks, resources, secondsRemaining));
    }

    public void finishGame(String roomId, String trigger, ActiveGameState state) {
        // Bloquear doble ejecución si dos hilos detectan el fin al mismo tiempo
        if (gameManager.getGameState(roomId) == null) {
            return;
        }
        gameManager.removeGameState(roomId);
        lastBroadcastTimes.remove(roomId);

        logger.info("Finalizando juego en roomId {} por {}", roomId, trigger);
        Instant endedAt = Instant.now();

        // Calcular puntajes finales y ordenar
        List<SharkState> sortedSharks = state.getSharks().values().stream()
                .peek(s -> s.setScore(scoreCalculator.calculateFinalScore(s, s.isAlive())))
                .sorted((a, b) -> Long.compare(b.getScore(), a.getScore())) // Mayor score primero
                .toList();
                
        List<RankingEntryDto> finalRanking = new ArrayList<>();

        // Se usa TransactionTemplate porque Spring AOP @Transactional no funciona en llamadas "internas" de la misma instancia
        transactionTemplate.executeWithoutResult(status -> {
            // 2. Persiste GameSession
            GameSession session = GameSession.builder()
                    .roomId(roomId)
                    .startedAt(state.getStartedAt())
                    .endedAt(endedAt)
                    .status(SessionStatus.FINISHED)
                    .endTrigger(trigger)
                    .build();
            session = gameSessionRepository.save(session);

            List<PlayerResult> playerResults = new ArrayList<>();
            
            int position = 1;
            for (SharkState s : sortedSharks) {
                // Guardar SessionResult
                SessionResult result = SessionResult.builder()
                        .sessionId(session.getId())
                        .userId(s.getUserId())
                        .finalPosition(position)
                        .score(s.getScore())
                        .maxSizeReached((int) s.getMaxSizeReached())
                        .resourcesConsumed(s.getResourcesConsumed())
                        .rivalsEliminated(s.getRivalsEliminated())
                        .survived(s.isAlive())
                        .build();
                sessionResultRepository.save(result);
                
                finalRanking.add(new RankingEntryDto(s.getUserId(), s.getUsername(), position, s.getScore()));
                playerResults.add(new PlayerResult(s.getUserId(), position, s.getScore(), (int) s.getMaxSizeReached()));
                position++;
            }

            // 3. OutboxEvent - Misma transacción!
            GameSessionFinishedEventPayload payload = new GameSessionFinishedEventPayload(
                    session.getId(), roomId, playerResults, endedAt
            );

            try {
                String jsonPayload = objectMapper.writeValueAsString(payload);
                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .aggregateType("GameSession")
                        .aggregateId(session.getId().toString())
                        .eventType("game.finished")
                        .payload(jsonPayload)
                        .build();
                outboxEventRepository.save(outboxEvent);
            } catch (JsonProcessingException e) {
                logger.error("Error serializando evento de fin de partida", e);
            }
        });

        // 4. Broadcast de fin de partida
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/end", 
                new GameEndBroadcast(finalRanking, trigger));
    }
}
