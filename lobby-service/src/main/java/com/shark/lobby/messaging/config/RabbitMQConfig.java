package com.shark.lobby.messaging.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "shark.events";

    /**
     * Declara el mismo exchange "shark.events" (topic, durable) que ya existe
     * del lado de auth-service. RabbitMQ es idempotente al declarar exchanges
     * idénticos desde múltiples servicios. El que arranque primero lo creará.
     */
    @Bean
    public TopicExchange sharkEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    /**
     * Converter para serializar/deserializar payloads a JSON usando Jackson.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
