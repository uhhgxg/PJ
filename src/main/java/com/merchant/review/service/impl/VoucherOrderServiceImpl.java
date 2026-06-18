package com.merchant.review.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.merchant.review.dto.Result;
import com.merchant.review.dto.UserDTO;
import com.merchant.review.entity.VoucherOrder;
import com.merchant.review.mapper.VoucherOrderMapper;
import com.merchant.review.service.IVoucherOrderService;
import com.merchant.review.utils.RabbitMQSender;
import com.merchant.review.utils.RedisIdWorker;
import com.merchant.review.utils.UserHolder;
import com.merchant.review.mq.VoucherOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static com.merchant.review.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.merchant.review.utils.RedisConstants.SECKILL_STOCK_KEY;
import static com.merchant.review.utils.RedisConstants.SECKILL_VOUCHER_KEY;

/**
 * 秒杀订单服务
 * <p>
 * 整体架构：
 * <pre>
 *   用户请求 → seckillVoucher()
 *        ↓ Redisson RLock 分布式锁（防并发）
 *        ↓ RAtomicLong 库存校验 + 原子扣减
 *        ↓ RSet 一人一单校验 + 记录
 *        ↓ RabbitMQ 消息异步落库
 * </pre>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>由 Redisson 分布式锁代替原始 Lua 脚本，利用 Redisson WatchDog 自动续期</li>
 *   <li>RLock + RAtomicLong + RSet 组合，以可读性换极致性能（仍维持万级 QPS）</li>
 *   <li>RabbitMQ 异步消费订单，利用 Spring AMQP 重试机制保证可靠性</li>
 *   <li>消费端通过 DB 唯一索引 / 业务去重保证订单幂等</li>
 * </ul>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private RabbitMQSender rabbitMQSender;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 1. 活动时间校验 — 从 Redisson RBucket 读取元数据
        RBucket<String> metaBucket = redissonClient.getBucket(SECKILL_VOUCHER_KEY + voucherId);
        String meta = metaBucket.get();
        if (meta != null) {
            String[] parts = meta.split(",");
            if (parts.length == 2) {
                long beginTime = Long.parseLong(parts[0]);
                long endTime = Long.parseLong(parts[1]);
                long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
                if (now < beginTime) {
                    log.debug("秒杀尚未开始 userId={} voucherId={}", userId, voucherId);
                    return Result.fail("秒杀尚未开始");
                }
                if (now > endTime) {
                    log.debug("秒杀已结束 userId={} voucherId={}", userId, voucherId);
                    return Result.fail("秒杀已结束");
                }
            }
        }

        // 预生成全局唯一订单 ID
        long orderId = redisIdWorker.nextId("order");

        // 2. 分布式锁 — 防止同时下单导致库存超卖 / 一人一单失效
        RLock lock = redissonClient.getLock("seckill:" + voucherId);
        try {
            // 尝试获取锁，等待 2 秒，最长持有 30 秒（WatchDog 自动续期）
            if (!lock.tryLock(2, 30, TimeUnit.SECONDS)) {
                log.warn("秒杀锁争抢超时 userId={} voucherId={}", userId, voucherId);
                return Result.fail("系统繁忙，请重试");
            }

            // 3. 库存校验
            RAtomicLong stock = redissonClient.getAtomicLong(SECKILL_STOCK_KEY + voucherId);
            long remain = stock.get();
            if (remain <= 0) {
                log.debug("库存不足 userId={} voucherId={}", userId, voucherId);
                return Result.fail("库存不足");
            }

            // 4. 一人一单校验
            RSet<String> orderSet = redissonClient.getSet(SECKILL_ORDER_KEY + voucherId);
            if (orderSet.contains(userId.toString())) {
                log.debug("重复下单 userId={} voucherId={}", userId, voucherId);
                return Result.fail("不能重复下单");
            }

            // 5. 原子操作：扣减库存 + 记录已购用户
            stock.decrementAndGet();
            orderSet.add(userId.toString());

            log.info("秒杀校验通过 userId={} voucherId={} orderId={}", userId, voucherId, orderId);

        } catch (InterruptedException e) {
            log.error("秒杀锁异常 userId={} voucherId={}", userId, voucherId, e);
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请重试");
        } finally {
            // 释放锁（当前线程持有才释放）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        // 6. RabbitMQ 异步落库（订单创建在事务中完成）
        rabbitMQSender.send(new VoucherOrderMessage(orderId, userId, voucherId));
        log.info("秒杀下单成功 userId={} voucherId={} orderId={}", userId, voucherId, orderId);

        return Result.ok(orderId);
    }
}
