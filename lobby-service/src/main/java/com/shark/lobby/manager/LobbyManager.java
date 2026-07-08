package com.shark.lobby.manager;

import com.shark.lobby.exception.NotHostException;
import com.shark.lobby.exception.RoomFullException;
import com.shark.lobby.exception.RoomNotFoundException;
import com.shark.lobby.exception.RoomNotJoinableException;
import com.shark.lobby.model.PlayerInSession;
import com.shark.lobby.model.Room;
import com.shark.lobby.model.RoomStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class LobbyManager {

    private final ConcurrentHashMap<String, Room> activeRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> userRoomMap = new ConcurrentHashMap<>();

    public Room createRoom(UUID hostUserId, String hostUsername, String roomName) {
        String roomId = UUID.randomUUID().toString();
        String roomCode;
        
        do {
            roomCode = generateRoomCode();
        } while (roomCodeExists(roomCode));

        Room room = Room.builder()
                .roomId(roomId)
                .roomCode(roomCode)
                .hostUserId(hostUserId)
                .hostUsername(hostUsername)
                .status(RoomStatus.WAITING)
                .maxPlayers(30)
                .createdAt(Instant.now())
                .build();

        PlayerInSession host = PlayerInSession.builder()
                .userId(hostUserId)
                .username(hostUsername)
                .joinedAt(Instant.now())
                .connected(true)
                .build();

        room.getPlayers().put(hostUserId, host);
        activeRooms.put(roomId, room);
        userRoomMap.put(hostUserId, roomId);

        return room;
    }

    public Room joinRoom(String roomId, UUID userId, String username) {
        Room room = getRoom(roomId).orElseThrow(RoomNotFoundException::new);

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RoomNotJoinableException();
        }

        if (room.getPlayers().size() >= room.getMaxPlayers() && !room.getPlayers().containsKey(userId)) {
            throw new RoomFullException();
        }

        PlayerInSession player = PlayerInSession.builder()
                .userId(userId)
                .username(username)
                .joinedAt(Instant.now())
                .connected(true)
                .build();

        room.getPlayers().put(userId, player);
        userRoomMap.put(userId, roomId);

        return room;
    }

    public Room leaveRoom(String roomId, UUID userId) {
        Room room = getRoom(roomId).orElseThrow(RoomNotFoundException::new);
        room.getPlayers().remove(userId);
        userRoomMap.remove(userId);

        if (room.getHostUserId().equals(userId) && room.getStatus() == RoomStatus.WAITING) {
            room.setStatus(RoomStatus.CANCELLED);
        }

        if (room.getPlayers().isEmpty()) {
            activeRooms.remove(roomId);
        }

        return room;
    }

    public Room markDisconnected(String roomId, UUID userId) {
        Room room = activeRooms.get(roomId);
        if (room != null) {
            PlayerInSession player = room.getPlayers().get(userId);
            if (player != null) {
                player.setConnected(false);
                if (room.getStatus() == RoomStatus.WAITING) {
                    return leaveRoom(roomId, userId);
                }
            }
        }
        return room;
    }

    public Room startGame(String roomId, UUID requesterId) {
        Room room = getRoom(roomId).orElseThrow(RoomNotFoundException::new);

        if (!room.getHostUserId().equals(requesterId)) {
            throw new NotHostException();
        }

        if (room.getPlayers().size() < 2) {
            throw new IllegalArgumentException("Se requieren al menos 2 jugadores para iniciar.");
        }

        room.setStatus(RoomStatus.IN_PROGRESS);
        return room;
    }

    public List<Room> listWaitingRooms() {
        return activeRooms.values().stream()
                .filter(r -> r.getStatus() == RoomStatus.WAITING)
                .collect(Collectors.toList());
    }

    public Optional<Room> getRoom(String roomId) {
        return Optional.ofNullable(activeRooms.get(roomId));
    }

    public Optional<String> getRoomIdForUser(UUID userId) {
        return Optional.ofNullable(userRoomMap.get(userId));
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private boolean roomCodeExists(String code) {
        return activeRooms.values().stream().anyMatch(r -> r.getRoomCode().equals(code));
    }
}
