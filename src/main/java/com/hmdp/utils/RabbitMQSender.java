package com.hmdp.utils;

import com.hmdp.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class RabbitMQSender {

    @Resource
    private RabbitTemplate rabbitTemplate;

    public void send(Object message) {
        send(RabbitMQConfig.ROUTING_KEY, message);
    }

    public void send(String routingKey, Object message) {
        log.debug("Sending message to exchange: {}, routingKey: {}", RabbitMQConfig.EXCHANGE_NAME, routingKey);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, message);
    }
}
