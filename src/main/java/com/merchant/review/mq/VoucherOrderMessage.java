package com.merchant.review.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RabbitMQ 秒杀订单消息
 * <p>
 * 由秒杀生产者发送，消费者异步落库
 */
@Data // 使用Lombok注解，自动生成getter、setter、toString等方法
@NoArgsConstructor // 使用Lombok注解，自动生成无参构造方法
@AllArgsConstructor // 使用Lombok注解，自动生成包含所有参数的构造方法
public class VoucherOrderMessage implements Serializable { // 实现Serializable接口，支持序列化

    private static final long serialVersionUID = 1L; // 序列化版本号，用于控制版本兼容性

    private Long orderId; // 订单ID，标识唯一订单
    private Long userId; // 用户ID，标识下单用户
    private Long voucherId; // 优惠券ID，标识使用的优惠券
}
