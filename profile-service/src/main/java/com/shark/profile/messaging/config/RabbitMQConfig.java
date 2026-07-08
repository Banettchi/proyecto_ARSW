package com.shark.profile.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/*
 * Documentación de Infraestructura:
 * Ambos servicios (auth-service y profile-service) deben declarar la misma topología de
 * RabbitMQ de forma idempotente, ya que quien arranque primero la crea en el broker.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "shark.events";
    public static final String DLX_NAME = "shark.events.dlx";

    public static final String QUEUE_PROFILE_USER_REGISTERED = "profile.user-registered.queue";
    public static final String QUEUE_PROFILE_GAME_FINISHED = "profile.game-finished.queue";

    public static final String DLQ_PROFILE_USER_REGISTERED = "profile.user-registered.dlq";
    public static final String DLQ_PROFILE_GAME_FINISHED = "profile.game-finished.dlq";

    public static final String ROUTING_KEY_USER_REGISTERED = "user.registered";
    public static final String ROUTING_KEY_GAME_FINISHED = "game.finished";
    
    public static final String DLQ_ROUTING_KEY_USER_REGISTERED = "user.registered.dlq";
    public static final String DLQ_ROUTING_KEY_GAME_FINISHED = "game.finished.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

    // --- User Registered Queue & Binding ---
    @Bean
    public Queue userRegisteredQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_NAME);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY_USER_REGISTERED);
        return new Queue(QUEUE_PROFILE_USER_REGISTERED, true, false, false, args);
    }

    @Bean
    public Queue userRegisteredDlq() {
        return new Queue(DLQ_PROFILE_USER_REGISTERED, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(userRegisteredQueue).to(eventsExchange).with(ROUTING_KEY_USER_REGISTERED);
    }

    @Bean
    public Binding userRegisteredDlqBinding(Queue userRegisteredDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(userRegisteredDlq).to(deadLetterExchange).with(DLQ_ROUTING_KEY_USER_REGISTERED);
    }

    // --- Game Finished Queue & Binding ---
    @Bean
    public Queue gameFinishedQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_NAME);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY_GAME_FINISHED);
        return new Queue(QUEUE_PROFILE_GAME_FINISHED, true, false, false, args);
    }

    @Bean
    public Queue gameFinishedDlq() {
        return new Queue(DLQ_PROFILE_GAME_FINISHED, true);
    }

    @Bean
    public Binding gameFinishedBinding(Queue gameFinishedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(gameFinishedQueue).to(eventsExchange).with(ROUTING_KEY_GAME_FINISHED);
    }

    @Bean
    public Binding gameFinishedDlqBinding(Queue gameFinishedDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(gameFinishedDlq).to(deadLetterExchange).with(DLQ_ROUTING_KEY_GAME_FINISHED);
    }

    // --- Converters & Factories ---
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        
        // Manual ack para confirmar nosotros mismos
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        
        // Un mensaje rechazado va directo a la DLQ en vez de reintentarse infinitamente en loop
        factory.setDefaultRequeueRejected(false);
        
        return factory;
    }
}
