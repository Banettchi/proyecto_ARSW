package com.shark.lobby.messaging;

import com.shark.lobby.dto.events.RoomClosedEventPayload;
import com.shark.lobby.dto.events.RoomCreatedEventPayload;
import com.shark.lobby.messaging.config.RabbitMQConfig;
import com.shark.lobby.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class LobbyEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(LobbyEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public LobbyEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /*
     * IMPORTANTE DECISIÓN ARQUITECTÓNICA (Documentada en docs/event-contracts.md):
     * A diferencia de auth-service y profile-service, lobby-service NO usa Transactional
     * Outbox porque no tiene base de datos propia donde persistir el evento de forma 
     * transaccional junto con el cambio de estado (el estado vive en memoria). 
     * Por lo tanto, si RabbitMQ está caído en el momento exacto de crear o cerrar una sala, 
     * ese evento específico de auditoría se pierde — PERO la sala en sí sigue funcionando 
     * perfectamente en memoria y el broadcast STOMP a los jugadores conectados NO depende 
     * de RabbitMQ en absoluto (usa el broker STOMP simple interno de Spring, no AMQP).
     * Este es un trade-off consciente y aceptable: solo se pierde observabilidad/auditoría, 
     * nunca funcionalidad del juego.
     */
    public void publishRoomCreated(Room room) {
        try {
            RoomCreatedEventPayload payload = new RoomCreatedEventPayload(
                    room.getRoomId(),
                    room.getHostUsername(),
                    room.getCreatedAt()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "room.created", payload);
            logger.info("Evento room.created publicado para sala {}", room.getRoomId());
        } catch (AmqpException e) {
            logger.warn("No se pudo publicar el evento room.created para la sala {}. El juego continuará, se pierde la auditoría. Razón: {}", room.getRoomId(), e.getMessage());
        }
    }

    public void publishRoomClosed(Room room, String reason) {
        try {
            RoomClosedEventPayload payload = new RoomClosedEventPayload(
                    room.getRoomId(),
                    reason
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, "room.closed", payload);
            logger.info("Evento room.closed publicado para sala {}", room.getRoomId());
        } catch (AmqpException e) {
            logger.warn("No se pudo publicar el evento room.closed para la sala {}. El juego continuará, se pierde la auditoría. Razón: {}", room.getRoomId(), e.getMessage());
        }
    }
}
