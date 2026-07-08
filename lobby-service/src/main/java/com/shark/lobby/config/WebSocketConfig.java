package com.shark.lobby.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${frontend.url}")
    private String allowedOriginPattern;

    public WebSocketConfig(WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-lobby")
                .setAllowedOriginPatterns(allowedOriginPattern)
                .withSockJS(); // Fallback
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Destinos de broadcast hacia los clientes (ej. enviar lista de salas)
        registry.enableSimpleBroker("/topic");
        
        // Destino base para mensajes entrantes hacia los controladores (ej. /app/lobby/create)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Registramos el filtro de seguridad de JWT
        registration.interceptors(webSocketAuthInterceptor);
    }
}
