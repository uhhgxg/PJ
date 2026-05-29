package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis 的全局唯一 ID 生成器
 * <p>ID 结构（64 位 long）：</p>
 * <pre>
 *   1 bit 符号位（恒为 0）
 *  31 bit 时间戳（秒，距 2022-01-01 的差值，支持约 68 年）
 *  32 bit 序列号（Redis 每日自增计数器，支持单日约 42 亿次）
 * </pre>
 */
@Component
public class RedisIdWorker {

    /** 基准时间戳：2022-01-01T00:00:00 */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /** 序列号偏移位数（低 32 位） */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成下一个全局唯一 ID
     * <p>Redis key 格式为 {@code icr:<keyPrefix>:<yyyy:MM:dd>}，每日自动重置序列号，
     * 并设置 TTL 自动清理历史 key。</p>
     *
     * @param keyPrefix 业务前缀，如 "order"、"voucher"
     * @return 64 位全局唯一 ID
     */
    public long nextId(String keyPrefix) {
        // 1. 计算距基准时间戳的秒数（左移 32 位到高 31 位）
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2. 生成当天日期，用于区分每天的序列号
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        // 3. Redis 自增，获取当天序号（低 32 位）
        long count = stringRedisTemplate.opsForValue()
                .increment("icr:" + keyPrefix + ":" + date);

        // 4. 拼接并返回：高 31 位时间戳 | 低 32 位序列号
        return timestamp << COUNT_BITS | count;
    }
}
