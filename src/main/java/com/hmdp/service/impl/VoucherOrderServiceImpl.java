package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 企业级秒杀订单服务
 * <p>
 * 整体架构：
 * <pre>
 *   用户请求 → seckillVoucher() [主线程，纯 Redis/Lua]
 *        ↓ Lua 原子操作：校验时间 → 校验库存 → 一人一单 → DECR 库存 → XADD 入 Stream
 *   Stream 消息 → consumeLoop() [单线程消费组]
 *        ↓ 解析消息 → DB 去重（幂等） → 事务写订单 + 扣 DB 库存 → ACK
 * </pre>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>主线程零 DB 查询，所有校验由 Lua 脚本在 Redis 内原子完成，支撑万级 QPS</li>
 *   <li>Redis Stream 消费组保证消息不丢失，宕机重启后自动拉取 pending 消息恢复</li>
 *   <li>消费端通过 DB 层唯一索引 / 业务去重保证订单幂等</li>
 *   <li>非重试异常直接 ACK 避免死循环；可重试异常保留消息等待重试，超过上限后移入死信</li>
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
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private TransactionTemplate transactionTemplate;

    // ======================== 常量 ========================

    private static final String SECKILL_STREAM_KEY   = "stream:seckill:order";
    private static final String CONSUMER_GROUP       = "seckill-group";
    private static final String CONSUMER_NAME         = "consumer-" + UUID.randomUUID();
    private static final String RETRY_COUNT_PREFIX    = "seckill:retry:";

    /** 单条消息最大重试次数，超过后 ACK 丢弃并告警 */
    private static final int    MAX_RETRIES           = 5;
    /** 重试计数 Key 的 TTL（小时），防止僵尸 Key 堆积 */
    private static final long   RETRY_KEY_TTL_HOURS   = 4;

    // ======================== Lua 脚本 ========================

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("seckill.lua"));
        script.setResultType(Long.class);
        SECKILL_SCRIPT = script;
    }

    // ======================== 线程池 ========================

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seckill-consumer");
        t.setDaemon(true);
        return t;
    });

    // ======================== 异常分类 ========================

    /** 非重试异常：重试也无法成功（库存耗尽、重复订单等），直接 ACK 终止 */
    private static class NonRetryableException extends RuntimeException {
        NonRetryableException(String message) {
            super(message);
        }
    }

    // ======================== 生命周期 ========================

    @PostConstruct
    private void init() {
        // 创建消费组（Stream 不存在时自动创建 MKSTREAM）
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(SECKILL_STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
            log.info("消费组创建/验证成功 stream={} group={}", SECKILL_STREAM_KEY, CONSUMER_GROUP);
        } catch (Exception e) {
            // BUSYGROUP — 消费组已存在
            log.info("消费组已存在 stream={} group={}", SECKILL_STREAM_KEY, CONSUMER_GROUP);
        }

        // 启���消费线程
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            log.info("秒杀消费线程启动 consumer={}", CONSUMER_NAME);
            handlePendingMessages();
            consumeLoop();
        });
    }

    @PreDestroy
    private void destroy() {
        log.info("秒杀消费线程关闭中...");
        SECKILL_ORDER_EXECUTOR.shutdown();
        try {
            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                SECKILL_ORDER_EXECUTOR.shutdownNow();
                log.warn("消费线程强制关闭");
            }
        } catch (InterruptedException e) {
            SECKILL_ORDER_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("秒杀消费线程已关闭");
    }

    // ======================== 生产者：秒杀下单入口 ========================

    /**
     * 秒杀下单 —— 纯 Redis 操作，零数据库查询
     * <p>
     * 所有校验（时间、库存、一人一单）由 Lua 脚本在 Redis 内原子完成。
     * 成功后订单消息写入 Redis Stream，由消费端异步落库。
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
                Arrays.asList(stockKey, orderKey, SECKILL_STREAM_KEY, metaKey),
                userId.toString(),
                String.valueOf(orderId),
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

    // ======================== 消费者：主消费循环 ========================

    /**
     * 主消费循环 — 持续从 Stream 拉取新消息
     * <p>
     * 使用 {@code >} 偏移量从最后消费位置读取，只处理新消息。
     * pending 消息由 {@link #handlePendingMessages()} 先行处理。
     */
    private void consumeLoop() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                        .read(
                                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                StreamOffset.create(SECKILL_STREAM_KEY, ReadOffset.lastConsumed())
                        );

                if (records != null && !records.isEmpty()) {
                    for (MapRecord<String, Object, Object> record : records) {
                        if (processRecord(record)) {
                            stringRedisTemplate.opsForStream()
                                    .acknowledge(SECKILL_STREAM_KEY, CONSUMER_GROUP, record.getId().getValue());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("消费 Stream 消息异常，1s 后重试", e);
                sleepSafe(1000);
            }
        }
    }

    // ======================== 消费者：pending 消息处理（宕机恢复） ========================

    /**
     * 处理 pending 消息 — 应对消费者宕机未 ACK 的场景
     * <p>
     * 使用 {@code 0} 偏移量读取已投递但未确认的消息（pending entries）。
     * 消费组保证了同一条消息不会被另一个消费者拿走，因此无需分布式锁。
     * 处理完毕后进入主消费循环。
     * <p>
     * 每条消息会检查重试计数，超过 {@link #MAX_RETRIES} 的消息直接 ACK 丢弃，
     * 避免因数据问题导致消息在 pending 队列中无限循环。
     */
    private void handlePendingMessages() {
        int handled = 0;
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> pending = stringRedisTemplate.opsForStream()
                        .read(
                                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                                StreamReadOptions.empty().count(1).block(Duration.ofMillis(500)),
                                StreamOffset.create(SECKILL_STREAM_KEY, ReadOffset.from("0"))
                        );
                if (pending == null || pending.isEmpty()) {
                    break;
                }
                for (MapRecord<String, Object, Object> record : pending) {
                    if (processRecord(record)) {
                        stringRedisTemplate.opsForStream()
                                .acknowledge(SECKILL_STREAM_KEY, CONSUMER_GROUP, record.getId().getValue());
                    }
                    handled++;
                }
            } catch (Exception e) {
                log.error("处理 pending 消息异常，1s 后重试", e);
                sleepSafe(1000);
            }
        }
        if (handled > 0) {
            log.info("pending 消息处理完毕，共处理 {} 条", handled);
        }
    }

    // ======================== 消费者：单条消息处理 ========================

    /**
     * 处理单条订单消息
     * <p>
     * 流程：解析消息体 → 重试次数校验 → DB 去重（幂等） → 事务写订单 + 扣库存 → 返回结果
     *
     * @param record Stream 消息记录
     * @return true 表明处理已完成（成功 / 永久失败），可 ACK；false 表示暂时失败，等待重试
     */
    private boolean processRecord(MapRecord<String, Object, Object> record) {
        String recordId = record.getId().getValue();
        Map<Object, Object> data = record.getValue();

        // 1. 解析消息体
        Long orderId;
        Long userId;
        Long voucherId;
        try {
            orderId   = Long.valueOf(data.get("orderId").toString());
            userId    = Long.valueOf(data.get("userId").toString());
            voucherId = Long.valueOf(data.get("voucherId").toString());
        } catch (Exception e) {
            log.error("Stream 消息体格式异常，丢弃 recordId={} data={}", recordId, data, e);
            return true; // 消息损坏，ACK 丢弃
        }

        // 2. 重试计数检���（防死循环）
        String retryKey = RETRY_COUNT_PREFIX + recordId;
        Long retryCount = stringRedisTemplate.opsForValue().increment(retryKey);
        if (retryCount == 1) {
            stringRedisTemplate.expire(retryKey, Duration.ofHours(RETRY_KEY_TTL_HOURS));
        }
        if (retryCount > MAX_RETRIES) {
            log.error("消息重试次数超限，进入死信（ACK 丢弃） recordId={} retryCount={} userId={} voucherId={}",
                    recordId, retryCount, userId, voucherId);
            stringRedisTemplate.delete(retryKey);
            return true; // ACK 后丢弃，避免无限循环
        }
        if (retryCount > 1) {
            log.warn("消息重试中 recordId={} retryCount={}/{}}", recordId, retryCount, MAX_RETRIES);
        }

        // 3. DB 层去重（幂等保护 — 防止 Redis Set 丢失后产生重复订单）
        Integer exists = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .oneOpt()
                .map(o -> 1)
                .orElse(0);
        if (exists > 0) {
            log.warn("订单已存在（幂等拦截） userId={} voucherId={} recordId={}", userId, voucherId, recordId);
            return true; // 已存在，ACK
        }

        // 4. 构造订单对象
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 5. 事务内：保存订单 + 扣减 DB 库存（原子操作）
        try {
            transactionTemplate.executeWithoutResult(status -> {
                save(order);

                boolean success = seckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0)       // CAS 条件：库存 > 0 才执行 UPDATE
                        .update();
                if (!success) {
                    status.setRollbackOnly();
                    // 库存为 0 时无论重试多少次都无意义，标记为非重试异常
                    throw new NonRetryableException("库存已耗尽 voucherId=" + voucherId);
                }
            });
            log.info("订单创建成功 orderId={} userId={} voucherId={}", orderId, userId, voucherId);
            return true;
        } catch (NonRetryableException e) {
            log.warn("订单创建失败（非重试异常，直接 ACK） orderId={} userId={} voucherId={}", orderId, userId, voucherId);
            return true;
        } catch (Exception e) {
            // DB 连接超时、死锁等可重试异常 — 不 ACK，等待重试
            log.error("订单创建失败（可重试异常，等待 pending 重试） orderId={} userId={} voucherId={} retryCount={}",
                    orderId, userId, voucherId, retryCount, e);
            return false;
        }
    }

    // ======================== 工具方法 ========================

    private static void sleepSafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
