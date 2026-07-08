package com.shark.lobby.config;

import com.shark.lobby.security.JwtProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    
    private final JwtProvider jwtProvider;

    public WebSocketAuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Solo interceptamos el intento de CONNECT (el handshake del cliente)
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                logger.warn("Conexión WS rechazada: No se proporcionó token Bearer en el header Authorization");
                throw new IllegalArgumentException("Token requerido para conexión WS");
            }

            String token = authHeader.substring(7);

            if (!jwtProvider.validateToken(token)) {
                logger.warn("Conexión WS rechazada: Token inválido");
                throw new IllegalArgumentException("Token inválido");
            }

            UUID userId = jwtProvider.getUserIdFromToken(token);
            
            // Asignamos el principal a la sesión STOMP
            // Esto permite usar (Principal principal) en los controladores de @MessageMapping
            accessor.setUser(new UserPrincipal(userId.toString()));
            
            logger.info("Handshake WS exitoso para el userId: {}", userId);
        }

        return message;
    }
}

// Representación básica del usuario STOMP
class UserPrincipal implements Principal {
    private final String name;

    public UserPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
