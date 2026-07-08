package com.shark.auth.messaging.publisher;

import com.shark.auth.model.OutboxEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @CircuitBreaker(name = "rabbitmq-publisher", fallbackMethod = "publishFallback")
    public boolean publish(OutboxEvent event) {
        String routingKey = determineRoutingKey(event.getEventType());
        // Se asume que event.getPayload() es una cadena JSON correcta que Jackson tratará adecuadamente,
        // o si se prefiere se configuraría el Message properties con application/json.
        rabbitTemplate.convertAndSend("shark.events", routingKey, event.getPayload());
        return true;
    }

    public boolean publishFallback(OutboxEvent event, Throwable t) {
        logger.warn("RabbitMQ no disponible, evento {} permanece PENDING para reintento posterior. Razón: {}", 
                event.getId(), t.getMessage());
        return false;
    }

    private String determineRoutingKey(String eventType) {
        if ("UserRegisteredEvent".equals(eventType)) {
            return "user.registered";
        }
        return "unknown"; 
    }
}
