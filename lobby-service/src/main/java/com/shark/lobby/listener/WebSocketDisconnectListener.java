package com.shark.lobby.listener;

import com.shark.lobby.dto.RoomListMessage;
import com.shark.lobby.dto.RoomStateMessage;
import com.shark.lobby.manager.LobbyManager;
import com.shark.lobby.model.PlayerInSession;
import com.shark.lobby.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class WebSocketDisconnectListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketDisconnectListener.class);

    private final LobbyManager lobbyManager;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketDisconnectListener(LobbyManager lobbyManager, SimpMessagingTemplate messagingTemplate) {
        this.lobbyManager = lobbyManager;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }

        UUID userId;
        try {
            userId = UUID.fromString(user.getName());
        } catch (IllegalArgumentException e) {
            return;
        }

        Optional<String> roomIdOpt = lobbyManager.getRoomIdForUser(userId);
        
        if (roomIdOpt.isPresent()) {
            String roomId = roomIdOpt.get();
            logger.info("Detectada desconexión WS del usuario {} en la sala {}", userId, roomId);
            
            Room room = lobbyManager.markDisconnected(roomId, userId);
            
            if (room != null && !room.getPlayers().isEmpty()) {
                broadcastRoomState(room);
            }
            broadcastRoomList();
        }
    }

    private void broadcastRoomState(Room room) {
        List<String> playerUsernames = room.getPlayers().values().stream()
                .filter(PlayerInSession::isConnected)
                .map(PlayerInSession::getUsername)
                .collect(Collectors.toList());

        RoomStateMessage msg = RoomStateMessage.builder()
                .roomId(room.getRoomId())
                .roomCode(room.getRoomCode())
                .hostUsername(room.getHostUsername())
                .status(room.getStatus())
                .currentPlayers(room.getPlayers().size())
                .maxPlayers(room.getMaxPlayers())
                .playerUsernames(playerUsernames)
                .build();
                
        messagingTemplate.convertAndSend("/topic/lobby/" + room.getRoomId(), msg);
    }

    private void broadcastRoomList() {
        List<RoomStateMessage> waitingRooms = lobbyManager.listWaitingRooms().stream()
                .map(r -> {
                    List<String> players = r.getPlayers().values().stream()
                            .filter(PlayerInSession::isConnected)
                            .map(PlayerInSession::getUsername)
                            .collect(Collectors.toList());
                    return new RoomStateMessage(r.getRoomId(), r.getRoomCode(), r.getHostUsername(), 
                            r.getStatus(), r.getPlayers().size(), r.getMaxPlayers(), players);
                })
                .collect(Collectors.toList());
        messagingTemplate.convertAndSend("/topic/lobby", new RoomListMessage(waitingRooms));
    }
}
