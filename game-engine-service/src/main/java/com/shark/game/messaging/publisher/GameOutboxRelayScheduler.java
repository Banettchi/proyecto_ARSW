package com.shark.game.messaging.publisher;

import com.shark.game.model.OutboxEvent;
import com.shark.game.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameOutboxRelayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GameOutboxRelayScheduler.class);
    private final OutboxEventRepository outboxRepository;
    private final EventPublisher eventPublisher;

    public GameOutboxRelayScheduler(OutboxEventRepository outboxRepository, EventPublisher eventPublisher) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    public void relayPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.EventStatus.PENDING);
        
        if (!pendingEvents.isEmpty()) {
            logger.info("Outbox Relay (GameEngine): Encontrados {} eventos pendientes de envío.", pendingEvents.size());
            
            for (OutboxEvent event : pendingEvents) {
                event.setRetryCount(event.getRetryCount() + 1);
                
                if (event.getRetryCount() > 10) {
                    logger.error("Evento {} superó límite de reintentos. Marcado como FAILED.", event.getId());
                    event.setStatus(OutboxEvent.EventStatus.FAILED);
                    outboxRepository.save(event);
                    continue;
                }
                
                outboxRepository.save(event);
                
                try {
                    eventPublisher.publishEvent(event);
                } catch (Exception e) {
                    logger.error("Fallo inesperado re-publicando evento {}: {}", event.getId(), e.getMessage());
                }
            }
        }
    }
}
