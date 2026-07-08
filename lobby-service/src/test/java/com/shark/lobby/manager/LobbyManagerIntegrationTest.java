package com.shark.lobby.manager;

import com.shark.lobby.exception.NotHostException;
import com.shark.lobby.exception.RoomFullException;
import com.shark.lobby.exception.RoomNotFoundException;
import com.shark.lobby.exception.RoomNotJoinableException;
import com.shark.lobby.model.Room;
import com.shark.lobby.model.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ======================== PRUEBAS DE INTEGRACIÓN / CAJA GRIS ========================
 * 
 * Tipo: Integración (Integration Tests) + Caja Gris (Gray-Box)
 * 
 * Justificación:
 * - Caja Gris porque conocemos la estructura interna (ConcurrentHashMap, userRoomMap)
 *   pero probamos a través de la interfaz pública del componente.
 * - Integración porque se prueba la interacción entre múltiples métodos del
 *   LobbyManager que colaboran entre sí (createRoom -> joinRoom -> startGame -> leaveRoom).
 * - No se usa Mockito; el componente real se instancia con su estado interno completo.
 */
class LobbyManagerIntegrationTest {

    private LobbyManager lobbyManager;

    @BeforeEach
    void setUp() {
        lobbyManager = new LobbyManager();
    }

    // =================== CREATE ROOM ===================

    @Nested
    @DisplayName("createRoom() - Integración con estado interno")
    class CreateRoomTests {

        @Test
        @DisplayName("Crear sala genera roomId, roomCode y agrega al host como jugador")
        void createRoom_ValidInput_CreatesRoomWithHost() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host_player", "Mi Sala");

            assertNotNull(room.getRoomId());
            assertNotNull(room.getRoomCode());
            assertEquals(6, room.getRoomCode().length());
            assertEquals(RoomStatus.WAITING, room.getStatus());
            assertEquals(hostId, room.getHostUserId());
            assertEquals(1, room.getPlayers().size());
            assertTrue(room.getPlayers().containsKey(hostId));
        }

        @Test
        @DisplayName("Crear dos salas genera códigos de sala distintos")
        void createRoom_TwoRooms_HaveDifferentCodes() {
            Room room1 = lobbyManager.createRoom(UUID.randomUUID(), "host1", "Sala 1");
            Room room2 = lobbyManager.createRoom(UUID.randomUUID(), "host2", "Sala 2");

            assertNotEquals(room1.getRoomCode(), room2.getRoomCode());
            assertNotEquals(room1.getRoomId(), room2.getRoomId());
        }
    }

    // =================== JOIN ROOM ===================

    @Nested
    @DisplayName("joinRoom() - Integración con createRoom()")
    class JoinRoomTests {

        @Test
        @DisplayName("Un jugador puede unirse a una sala existente en estado WAITING")
        void joinRoom_ValidRoom_AddsPlayer() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");
            UUID playerId = UUID.randomUUID();

            Room updated = lobbyManager.joinRoom(room.getRoomId(), playerId, "player2");

            assertEquals(2, updated.getPlayers().size());
            assertTrue(updated.getPlayers().containsKey(playerId));
        }

        @Test
        @DisplayName("Unirse a sala inexistente lanza RoomNotFoundException")
        void joinRoom_NonExistentRoom_Throws() {
            assertThrows(RoomNotFoundException.class,
                    () -> lobbyManager.joinRoom("fake-id", UUID.randomUUID(), "ghost"));
        }

        @Test
        @DisplayName("Unirse a sala IN_PROGRESS lanza RoomNotJoinableException")
        void joinRoom_InProgressRoom_Throws() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");
            lobbyManager.joinRoom(room.getRoomId(), UUID.randomUUID(), "p2");
            lobbyManager.startGame(room.getRoomId(), hostId);

            assertThrows(RoomNotJoinableException.class,
                    () -> lobbyManager.joinRoom(room.getRoomId(), UUID.randomUUID(), "late_player"));
        }
    }

    // =================== START GAME ===================

    @Nested
    @DisplayName("startGame() - Integración con validaciones de negocio")
    class StartGameTests {

        @Test
        @DisplayName("Host puede iniciar partida con 2+ jugadores")
        void startGame_EnoughPlayers_ChangesStatusToInProgress() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");
            lobbyManager.joinRoom(room.getRoomId(), UUID.randomUUID(), "player2");

            Room started = lobbyManager.startGame(room.getRoomId(), hostId);

            assertEquals(RoomStatus.IN_PROGRESS, started.getStatus());
        }

        @Test
        @DisplayName("No-host intentando iniciar lanza NotHostException")
        void startGame_NotHost_Throws() {
            UUID hostId = UUID.randomUUID();
            UUID impostorId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");
            lobbyManager.joinRoom(room.getRoomId(), impostorId, "impostor");

            assertThrows(NotHostException.class,
                    () -> lobbyManager.startGame(room.getRoomId(), impostorId));
        }

        @Test
        @DisplayName("Iniciar con un solo jugador lanza IllegalArgumentException")
        void startGame_OnlyOnePlayer_Throws() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");

            assertThrows(IllegalArgumentException.class,
                    () -> lobbyManager.startGame(room.getRoomId(), hostId));
        }
    }

    // =================== LEAVE ROOM ===================

    @Nested
    @DisplayName("leaveRoom() - Flujo completo de ciclo de vida")
    class LeaveRoomTests {

        @Test
        @DisplayName("Host abandona sala WAITING -> la sala se cancela")
        void leaveRoom_HostLeaves_CancelsRoom() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");
            lobbyManager.joinRoom(room.getRoomId(), UUID.randomUUID(), "player2");

            Room updated = lobbyManager.leaveRoom(room.getRoomId(), hostId);

            assertEquals(RoomStatus.CANCELLED, updated.getStatus());
        }

        @Test
        @DisplayName("Último jugador abandona -> sala se elimina de la memoria")
        void leaveRoom_LastPlayerLeaves_RoomRemovedFromMemory() {
            UUID hostId = UUID.randomUUID();
            Room room = lobbyManager.createRoom(hostId, "host", "Sala");

            lobbyManager.leaveRoom(room.getRoomId(), hostId);

            assertTrue(lobbyManager.getRoom(room.getRoomId()).isEmpty());
        }
    }

    // =================== LIST ROOMS ===================

    @Nested
    @DisplayName("listWaitingRooms() - Filtrado por estado")
    class ListRoomTests {

        @Test
        @DisplayName("Solo retorna salas en estado WAITING, excluye IN_PROGRESS")
        void listWaitingRooms_FiltersCorrectly() {
            UUID host1 = UUID.randomUUID();
            UUID host2 = UUID.randomUUID();
            lobbyManager.createRoom(host1, "host1", "Sala Activa");
            Room room2 = lobbyManager.createRoom(host2, "host2", "Sala En Juego");
            lobbyManager.joinRoom(room2.getRoomId(), UUID.randomUUID(), "p2");
            lobbyManager.startGame(room2.getRoomId(), host2);

            List<Room> waiting = lobbyManager.listWaitingRooms();

            assertEquals(1, waiting.size());
            assertEquals(RoomStatus.WAITING, waiting.get(0).getStatus());
        }
    }
}
