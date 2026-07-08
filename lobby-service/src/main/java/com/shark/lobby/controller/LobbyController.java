package com.shark.lobby.controller;

import com.shark.lobby.dto.*;
import com.shark.lobby.manager.LobbyManager;
import com.shark.lobby.messaging.LobbyEventPublisher;
import com.shark.lobby.model.PlayerInSession;
import com.shark.lobby.model.Room;
import com.shark.lobby.model.RoomStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class LobbyController {

    private static final Logger logger = LoggerFactory.getLogger(LobbyController.class);

    private final LobbyManager lobbyManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final LobbyEventPublisher eventPublisher;

    public LobbyController(LobbyManager lobbyManager, SimpMessagingTemplate messagingTemplate, LobbyEventPublisher eventPublisher) {
        this.lobbyManager = lobbyManager;
        this.messagingTemplate = messagingTemplate;
        this.eventPublisher = eventPublisher;
    }

    private UUID getUserId(SimpMessageHeaderAccessor accessor) {
        return UUID.fromString(accessor.getUser().getName());
    }

    private void broadcastRoomList() {
        List<RoomStateMessage> waitingRooms = lobbyManager.listWaitingRooms().stream()
                .map(this::mapToRoomStateMessage)
                .collect(Collectors.toList());
        messagingTemplate.convertAndSend("/topic/lobby", new RoomListMessage(waitingRooms));
    }

    private void broadcastRoomState(Room room) {
        messagingTemplate.convertAndSend("/topic/lobby/" + room.getRoomId(), mapToRoomStateMessage(room));
    }

    @MessageMapping("/lobby/create")
    public void createRoom(@Payload CreateRoomRequest req, SimpMessageHeaderAccessor accessor) {
        UUID userId = getUserId(accessor);
        // Para simplificar, usaremos el mismo userId como username (en un sistema real se traería del profile)
        String username = "User_" + userId.toString().substring(0, 5); 
        
        Room room = lobbyManager.createRoom(userId, username, req.getRoomName());
        logger.info("Room creada: {} por {}", room.getRoomId(), userId);
        
        // Cumpliendo HU-04a: el otro jugador ve aparecer la sala en < 2 segundos sin recargar
        broadcastRoomList();
        
        // Enviamos el estado de la sala específicamente al creador (para que su cliente se suscriba)
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/room", mapToRoomStateMessage(room));
        
        // Publicar evento en RabbitMQ DESPUÉS del broadcast (Patrón Simplificado, no Outbox)
        eventPublisher.publishRoomCreated(room);
    }

    @MessageMapping("/lobby/join")
    public void joinRoom(@Payload JoinRoomRequest req, SimpMessageHeaderAccessor accessor) {
        UUID userId = getUserId(accessor);
        String username = "User_" + userId.toString().substring(0, 5);
        
        Room room = lobbyManager.joinRoom(req.getRoomId(), userId, username);
        logger.info("Usuario {} se unió a la sala {}", userId, req.getRoomId());
        
        broadcastRoomState(room);
        broadcastRoomList();
    }

    @MessageMapping("/lobby/leave")
    public void leaveRoom(@Payload JoinRoomRequest req, SimpMessageHeaderAccessor accessor) {
        UUID userId = getUserId(accessor);
        Room room = lobbyManager.leaveRoom(req.getRoomId(), userId);
        logger.info("Usuario {} abandonó la sala {}", userId, req.getRoomId());
        
        if (room != null && !room.getPlayers().isEmpty()) {
            broadcastRoomState(room);
        }
        broadcastRoomList();
        
        // Si el anfitrión abandonó y la sala se canceló, publicar evento
        if (room != null && room.getStatus() == RoomStatus.CANCELLED) {
            eventPublisher.publishRoomClosed(room, "Host abandonó la sala antes de iniciar.");
        }
    }

    @MessageMapping("/lobby/start")
    public void startGame(@Payload JoinRoomRequest req, SimpMessageHeaderAccessor accessor) {
        UUID userId = getUserId(accessor);
        Room room = lobbyManager.startGame(req.getRoomId(), userId);
        logger.info("La sala {} ha sido iniciada por el host {}", req.getRoomId(), userId);
        
        broadcastRoomState(room);
        broadcastRoomList();
        
        logger.info("TODO: Transferir sala {} al motor de juego (game-engine-service)", room.getRoomId());
    }

    private RoomStateMessage mapToRoomStateMessage(Room room) {
        List<String> playerUsernames = room.getPlayers().values().stream()
                .filter(PlayerInSession::isConnected)
                .map(PlayerInSession::getUsername)
                .collect(Collectors.toList());

        return RoomStateMessage.builder()
                .roomId(room.getRoomId())
                .roomCode(room.getRoomCode())
                .hostUsername(room.getHostUsername())
                .status(room.getStatus())
                .currentPlayers(room.getPlayers().size())
                .maxPlayers(room.getMaxPlayers())
                .playerUsernames(playerUsernames)
                .build();
    }
}
