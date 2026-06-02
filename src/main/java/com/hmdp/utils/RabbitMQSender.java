package com.hmdp.utils;

import com.hmdp.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * RabbitMQ消息发送器组件
 * 使用@Slf4j注解提供日志功能
 * 使用@Component注解将此类标记为Spring组件
 */
@Slf4j
@Component
public class RabbitMQSender {

    /**
     * 注入RabbitTemplate模板
     * RabbitTemplate是Spring AMQP提供的用于发送和接收消息的模板类
     */
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息方法
     * 使用默认的路由键发送消息
     * @param message 要发送的消息对象
     */
    public void send(Object message) {
        // 调用带路由键的发送方法，使用默认路由键
        send(RabbitMQConfig.ROUTING_KEY, message);
    }

    /**
     * 发送消息方法
     * 指定路由键发送消息到交换机
     * @param routingKey 消息的路由键
     * @param message 要发送的消息对象
     */
    public void send(String routingKey, Object message) {
        // 记录发送消息的调试日志，包含交换机名称和路由键
        log.debug("Sending message to exchange: {}, routingKey: {}", RabbitMQConfig.EXCHANGE_NAME, routingKey);
        // 使用RabbitTemplate发送消息到指定的交换机和路由键
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, message);
    }
}
