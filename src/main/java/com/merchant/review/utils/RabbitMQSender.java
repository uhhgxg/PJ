package com.merchant.review.utils;

import com.merchant.review.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

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
        send(RabbitMQConfig.ROUTING_KEY, message);
    }

    public void send(String routingKey, Object message) {
        log.info("【MQ发送】发送消息到交换机：{}，路由键：{}，消息内容：{}", RabbitMQConfig.EXCHANGE_NAME, routingKey, message);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, message);
        log.info("【MQ发送】消息发送成功");
    }
}
