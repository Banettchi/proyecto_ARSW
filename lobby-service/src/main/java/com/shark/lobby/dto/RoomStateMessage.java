package com.shark.lobby.dto;

import com.shark.lobby.model.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateMessage {
    private String roomId;
    private String roomCode;
    private String hostUsername;
    private RoomStatus status;
    private int currentPlayers;
    private int maxPlayers;
    private List<String> playerUsernames;
}
