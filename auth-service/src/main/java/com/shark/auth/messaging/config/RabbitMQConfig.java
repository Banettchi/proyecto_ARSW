package com.shark.auth.messaging.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String EXCHANGE_NAME = "shark.events";
    public static final String QUEUE_PROFILE_USER_REGISTERED = "profile.user-registered.queue";
    public static final String ROUTING_KEY_USER_REGISTERED = "user.registered";

    public static final String DLX_NAME = "shark.events.dlx";
    public static final String DLQ_PROFILE_USER_REGISTERED = "profile.user-registered.dlq";
    public static final String DLQ_ROUTING_KEY_USER_REGISTERED = "user.registered.dlq";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_NAME, true, false);
    }

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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        
        template.setReturnsCallback(returnedMessage -> {
            logger.warn("Mensaje no pudo ser enrutado: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returnedMessage.getExchange(),
                    returnedMessage.getRoutingKey(),
                    returnedMessage.getReplyCode(),
                    returnedMessage.getReplyText());
        });

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                logger.warn("Mensaje no confirmado por el broker. Causa: {}", cause);
            }
        });

        return template;
    }
}
