package com.shark.auth.messaging.publisher;

import com.shark.auth.model.OutboxEvent;
import com.shark.auth.model.OutboxStatus;
import com.shark.auth.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/*
 * Cuando un usuario se registra, el evento UserRegisteredEvent se guarda en la misma transacción de BD 
 * que el User (ver AuthService en el siguiente prompt). Si RabbitMQ está caído en ese momento, el registro 
 * del usuario se completa exitosamente de todas formas. Este scheduler es el único responsable de intentar 
 * publicar los eventos pendientes, y lo hace de forma asíncrona y tolerante a fallos, sin bloquear jamás 
 * una petición HTTP de un usuario real.
 */
@Component
@EnableScheduling
public class OutboxRelayScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;

    public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository, EventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            boolean success = eventPublisher.publish(event);

            if (success) {
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                successCount++;
            } else {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() > 10) {
                    event.setStatus(OutboxStatus.FAILED);
                    logger.error("ALERTA: El evento {} falló más de 10 veces y ha sido marcado como FAILED. Requiere revisión manual.", event.getId());
                }
                failedCount++;
            }
            outboxEventRepository.save(event);
        }

        logger.info("Ciclo de OutboxRelayScheduler finalizado. Eventos procesados: {}, Éxitos: {}, Fallos: {}", 
                pendingEvents.size(), successCount, failedCount);
    }
}
