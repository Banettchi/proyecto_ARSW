package com.shark.lobby.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    private String roomId;
    private String roomCode;
    private UUID hostUserId;
    private String hostUsername;
    private RoomStatus status;
    @Builder.Default
    private int maxPlayers = 30;
    @Builder.Default
    private ConcurrentHashMap<UUID, PlayerInSession> players = new ConcurrentHashMap<>();
    private Instant createdAt;
}
