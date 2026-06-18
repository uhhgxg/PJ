package com.merchant.review.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.merchant.review.config.RabbitMQConfig;
import com.merchant.review.entity.VoucherOrder;
import com.merchant.review.mapper.VoucherOrderMapper;
import com.merchant.review.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * RabbitMQ 秒杀订单消费者
 * <p>
 * 异步消费秒杀订单消息，完成订单创建 + DB 库存扣减。
 * 利用 Spring AMQP 重试机制处理临时故障，超过重试次数后消息自动丢弃。
 * 非重试异常（库存耗尽、重复订单）直接抛出 {@link AmqpRejectAndDontRequeueException} 跳过重试。
 */
@Slf4j
@Component
public class VoucherOrderMQConsumer {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private TransactionTemplate transactionTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleOrderMessage(VoucherOrderMessage message) {
        Long orderId = message.getOrderId();
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();

        log.info("【MQ消费】收到秒杀订单消息：订单ID={}，用户ID={}，优惠券ID={}", orderId, userId, voucherId);

        // 1. DB 层去重（幂等保护 — 防止 Redis Set 丢失后产生重复订单）
        Long exists = voucherOrderMapper.selectCount(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId)
        );
        if (exists != null && exists > 0) {
            log.warn("【MQ消费】订单已存在，幂等拦截 userId={} voucherId={} orderId={}", userId, voucherId, orderId);
            return; // 已存在，直接 ACK
        }

        // 2. 构造订单对象
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 3. 事务内：保存订单 + 扣减 DB 库存（原子操作）
        try {
            transactionTemplate.executeWithoutResult(status -> {
                voucherOrderMapper.insert(order);

                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0)       // CAS 乐观锁：库存 > 0 才执行 UPDATE
                        .update();
                if (!success) {
                    status.setRollbackOnly();
                    log.warn("【MQ消费】库存已耗尽，订单回滚 voucherId={}", voucherId);
                    throw new AmqpRejectAndDontRequeueException("库存已耗尽 voucherId=" + voucherId);
                }
            });
            log.info("【MQ消费】订单落库成功 orderId={} userId={} voucherId={}", orderId, userId, voucherId);
        } catch (AmqpRejectAndDontRequeueException e) {
            log.warn("【MQ消费】非重试异常，消息丢弃：{}", e.getMessage());
            throw e; // 非重试异常，直接抛出，不重试
        } catch (Exception e) {
            // DB 连接超时、死锁等可重试异常 — 抛出异常触发 Spring AMQP 重试
            log.error("【MQ消费】订单创建失败（可重试） orderId={} userId={} voucherId={}", orderId, userId, voucherId, e);
            throw e;
        }
    }
}
