package com.shark.profile.messaging.consumer;

import com.rabbitmq.client.Channel;
import com.shark.profile.dto.events.GameSessionFinishedEventPayload;
import com.shark.profile.dto.events.PlayerResult;
import com.shark.profile.model.ProcessedEvent;
import com.shark.profile.repository.GameHistoryRepository;
import com.shark.profile.repository.ProcessedEventRepository;
import com.shark.profile.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
public class GameSessionFinishedListener {

    private static final Logger logger = LoggerFactory.getLogger(GameSessionFinishedListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final GameHistoryRepository gameHistoryRepository;
    private final ProfileService profileService;

    public GameSessionFinishedListener(ProcessedEventRepository processedEventRepository, 
                                       GameHistoryRepository gameHistoryRepository, 
                                       ProfileService profileService) {
        this.processedEventRepository = processedEventRepository;
        this.gameHistoryRepository = gameHistoryRepository;
        this.profileService = profileService;
    }

    @RabbitListener(queues = "profile.game-finished.queue")
    @Transactional
    public void onMessage(GameSessionFinishedEventPayload payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            String messageIdStr = message.getMessageProperties().getMessageId();
            if (messageIdStr == null || messageIdStr.isBlank()) {
                messageIdStr = UUID.nameUUIDFromBytes((payload.sessionId().toString() + "game-finished").getBytes()).toString();
            }

            UUID messageId = UUID.fromString(messageIdStr);

            if (processedEventRepository.existsById(messageId)) {
                logger.info("Evento duplicado ignorado: messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (payload.results() != null) {
                for (PlayerResult result : payload.results()) {
                    if (!gameHistoryRepository.existsByUserIdAndSessionId(result.userId(), payload.sessionId())) {
                        profileService.applyGameResult(payload.sessionId(), result, payload.finishedAt());
                    } else {
                        logger.debug("Idempotencia por session+user: Ignorando userId={} en sessionId={}", result.userId(), payload.sessionId());
                    }
                }
            }

            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(messageId);
            processedEvent.setEventType("GameSessionFinishedEvent");
            processedEvent.setProcessedAt(Instant.now());
            processedEventRepository.save(processedEvent);

            channel.basicAck(deliveryTag, false);
            logger.info("Evento procesado exitosamente: messageId={}, sessionId={}", messageId, payload.sessionId());

        } catch (Exception ex) {
            logger.error("Error al procesar el evento GameSessionFinishedEventPayload: payload={}", payload, ex);
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception e) {
                // ignorar
            }
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
