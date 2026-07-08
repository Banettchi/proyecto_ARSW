package com.shark.profile.messaging.consumer;

import com.rabbitmq.client.Channel;
import com.shark.profile.dto.ProfileCreateDto;
import com.shark.profile.dto.events.UserRegisteredEventPayload;
import com.shark.profile.model.ProcessedEvent;
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
public class UserRegisteredListener {

    private static final Logger logger = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ProfileService profileService;

    public UserRegisteredListener(ProcessedEventRepository processedEventRepository, ProfileService profileService) {
        this.processedEventRepository = processedEventRepository;
        this.profileService = profileService;
    }

    @RabbitListener(queues = "profile.user-registered.queue")
    @Transactional
    public void onMessage(UserRegisteredEventPayload payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        
        try {
            String messageIdStr = message.getMessageProperties().getMessageId();
            if (messageIdStr == null || messageIdStr.isBlank()) {
                // Fallback determinístico si el publicador no asignó messageId
                messageIdStr = UUID.nameUUIDFromBytes((payload.userId().toString() + "user-registered").getBytes()).toString();
            }
            
            UUID messageId = UUID.fromString(messageIdStr);

            if (processedEventRepository.existsById(messageId)) {
                logger.info("Evento duplicado ignorado: messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            ProfileCreateDto createDto = new ProfileCreateDto(payload.userId(), payload.username(), "Tiburón Anónimo");
            profileService.createInitialProfile(createDto);

            ProcessedEvent processedEvent = new ProcessedEvent();
            processedEvent.setEventId(messageId);
            processedEvent.setEventType("UserRegisteredEvent");
            processedEvent.setProcessedAt(Instant.now());
            processedEventRepository.save(processedEvent);

            channel.basicAck(deliveryTag, false);
            logger.info("Evento procesado exitosamente: messageId={}, userId={}", messageId, payload.userId());

        } catch (Exception ex) {
            logger.error("Error al procesar el evento UserRegisteredEventPayload: payload={}", payload, ex);
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception e) {
                // Ignorar si no se puede hacer rollback
            }
            // El "false" final es clave, significa "no reencolar", así el mensaje va directo a la DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
