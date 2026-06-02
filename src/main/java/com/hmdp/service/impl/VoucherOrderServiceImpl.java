package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RabbitMQSender;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.mq.VoucherOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * 秒杀订单服务
 * <p>
 * 整体架构：
 * <pre>
 *   用户请求 → seckillVoucher() [主线程，纯 Redis/Lua]
 *        ↓ Lua 原子操作：校验时间 → 校验库存 → 一人一单 → DECR 库存 → SADD 已购
 *   Lua 返回 0 → RabbitMQ 消息异步落库
 * </pre>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>主线程零 DB 查询，所有校验由 Lua 脚本在 Redis 内原子完成，支撑万级 QPS</li>
 *   <li>RabbitMQ 异步消费订单，利用 Spring AMQP 重试机制保证可靠性</li>
 *   <li>消费端通过 DB 唯一索引 / 业务去重保证订单幂等</li>
 * </ul>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    // ======================== 依赖注入 ========================

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitMQSender rabbitMQSender;

    // ======================== Lua 脚本 ========================

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("seckill.lua"));
        script.setResultType(Long.class);
        SECKILL_SCRIPT = script;
    }

    // ======================== 生产者：秒杀下单入口 ========================

    /**
     * 秒杀下单 —— 纯 Redis 操作，零数据库查询
     * <p>
     * 所有校验（时间、库存、一人一单）由 Lua 脚本在 Redis 内原子完成。
     * 成功后发送 RabbitMQ 消息，由消费端异步落库。
     *
     * @param voucherId 优惠券 ID
     * @return 秒杀结果，成功时 data 为订单 ID
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 构建 Redis Key
        String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucherId;
        String orderKey = RedisConstants.SECKILL_ORDER_KEY + voucherId;
        String metaKey  = RedisConstants.SECKILL_VOUCHER_KEY + voucherId;

        // 预生成全局唯一订单 ID（雪花算法，Redis 自增）
        long orderId = redisIdWorker.nextId("order");

        // 当前 Unix 秒时间戳，传给 Lua 做时间校验（避免跨节点时钟偏差）
        long nowEpochSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        // 执行 Lua 脚本 — 单次 Redis 往返完成全部原子操作
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(stockKey, orderKey, metaKey),
                userId.toString(),
                String.valueOf(voucherId),
                String.valueOf(nowEpochSecond)
        );

        // Lua 返回 null = 脚本执行异常（Redis 故障等）
        if (result == null) {
            log.error("Lua 脚本执行异常返回 null voucherId={} userId={}", voucherId, userId);
            return Result.fail("系统繁忙，请重试");
        }

        int r = result.intValue();
        switch (r) {
            case 0:
                // 校验通过，发送 RabbitMQ 消息异步落库
                rabbitMQSender.send(new VoucherOrderMessage(orderId, userId, voucherId));
                log.info("秒杀下单成功 userId={} voucherId={} orderId={}", userId, voucherId, orderId);
                return Result.ok(orderId);
            case 1:
                log.debug("秒杀尚未开始 userId={} voucherId={}", userId, voucherId);
                return Result.fail("秒杀尚未开始");
            case 2:
                log.debug("秒杀已结束 userId={} voucherId={}", userId, voucherId);
                return Result.fail("秒杀已结束");
            case 3:
                log.debug("库存不足 userId={} voucherId={}", userId, voucherId);
                return Result.fail("库存不足");
            case 4:
                log.debug("重复下单 userId={} voucherId={}", userId, voucherId);
                return Result.fail("不能重复下单");
            default:
                log.error("Lua 脚本返回未知状态码 voucherId={} userId={} result={}", voucherId, userId, r);
                return Result.fail("秒杀失败，请重试");
        }
    }
}
