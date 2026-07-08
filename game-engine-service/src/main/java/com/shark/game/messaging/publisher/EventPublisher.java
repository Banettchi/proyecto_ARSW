package com.shark.game.messaging.publisher;

import com.shark.game.messaging.config.RabbitMQConfig;
import com.shark.game.model.OutboxEvent;
import com.shark.game.repository.OutboxEventRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);
    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventRepository outboxRepository;

    public EventPublisher(RabbitTemplate rabbitTemplate, OutboxEventRepository outboxRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxRepository = outboxRepository;
    }

    @CircuitBreaker(name = "rabbitmq-circuit", fallbackMethod = "fallbackPublish")
    public void publishEvent(OutboxEvent event) {
        String routingKey = event.getEventType(); 
        
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, event.getPayload());

        event.setStatus(OutboxEvent.EventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        outboxRepository.save(event);
        
        logger.info("Evento publicado a RabbitMQ desde Game Engine: {}", event.getId());
    }

    public void fallbackPublish(OutboxEvent event, Throwable t) {
        logger.error("CircuitBreaker ABIERTO o fallo en RabbitMQ. Guardando como PENDING: {}. Razón: {}", event.getId(), t.getMessage());
        event.setStatus(OutboxEvent.EventStatus.PENDING);
        outboxRepository.save(event);
    }
}
