package com.shark.game.config;

import com.shark.game.controller.GameController;
import com.shark.game.manager.ActiveGameManager;
import com.shark.game.security.JwtProvider;
import com.shark.game.session.ActiveGameState;
import com.shark.game.session.SharkState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Component
public class GameWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketAuthInterceptor.class);
    
    private final JwtProvider jwtProvider;
    private final ActiveGameManager gameManager;
    private final GameController gameController;

    public GameWebSocketAuthInterceptor(JwtProvider jwtProvider, 
                                        @Lazy ActiveGameManager gameManager, 
                                        @Lazy GameController gameController) {
        this.jwtProvider = jwtProvider;
        this.gameManager = gameManager;
        this.gameController = gameController;
    }

    /*
     * TODO: Deuda Técnica - Extraer interceptor de seguridad STOMP
     * Este código es funcionalmente idéntico al WebSocketAuthInterceptor de lobby-service.
     * En un refactor futuro, se debería extraer a una librería compartida de seguridad 
     * (ej. shark-security-starter) para no duplicar este interceptor en cada microservicio 
     * que utilice WebSockets.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("Conexión Game WS rechazada: No se proporcionó token Bearer");
                throw new IllegalArgumentException("Token requerido para conexión Game WS");
            }

            String token = authHeader.substring(7);

            if (!jwtProvider.validateToken(token)) {
                logger.warn("Conexión Game WS rechazada: Token inválido");
                throw new IllegalArgumentException("Token inválido");
            }

            UUID userId = jwtProvider.getUserIdFromToken(token);
            
            // Asignamos el principal a la sesión STOMP para identificar al jugador
            accessor.setUser(new UserPrincipal(userId.toString()));
            
            logger.info("Handshake Game WS exitoso para el userId: {}", userId);
            
            // --- MANEJO DE RECONEXIÓN HU-07/KAN-21 ---
            // Documentación: Requiere que el interceptor tenga acceso al ActiveGameManager
            // y GameController para hacer esta verificación en el momento del handshake 
            // y restaurar connected=true antes de que el cliente envíe su primer tick.
            if (gameManager != null && gameController != null) {
                String roomId = gameManager.getRoomIdForUser(userId);
                if (roomId != null) {
                    ActiveGameState state = gameManager.getGameState(roomId);
                    if (state != null) {
                        SharkState shark = state.getSharks().get(userId);
                        if (shark != null && !shark.isConnected() && shark.isAlive()) {
                            shark.setConnected(true);
                            shark.setLastSeenAt(java.time.Instant.now());
                            logger.info("Usuario {} reconectado exitosamente a la sala {}.", userId, roomId);
                            // Broadcast para que los demás dejen de verlo "congelado"
                            gameController.broadcastState(roomId, state);
                        }
                    }
                }
            }
        }

        return message;
    }
}

class UserPrincipal implements Principal {
    private final String name;
    public UserPrincipal(String name) { this.name = name; }
    @Override public String getName() { return name; }
}
