package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 通用 Redis 缓存工具类
 * <p>基于 Spring Boot 的 StringRedisTemplate 封装，提供以下能力：</p>
 * <ul>
 *   <li>基础缓存存取（含 TTL 过期）</li>
 *   <li>逻辑过期策略（解决缓存击穿）</li>
 *   <li>缓存穿透防护（空值缓存）</li>
 *   <li>缓存击穿防护（逻辑过期 + 互斥锁）</li>
 *   <li>分布式锁</li>
 * </ul>
 */
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    /** 异步缓存重建线程池 */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /** 分布式锁默认超时时间（秒） */
    private static final Long DEFAULT_LOCK_TTL = 10L;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ================================================================
    //  方法1：基础存储
    // ================================================================

    /**
     * 将任意 Java 对象序列化为 JSON，存入 Redis 的 String 类型 key 中，并支持设置 TTL 过期时间
     * <p>适用场景：常规缓存写入，由 Redis TTL 自动淘汰过期缓存</p>
     *
     * @param key   Redis 键
     * @param value 要存储的 Java 对象（将被序列化为 JSON）
     * @param time  过期时间数值
     * @param unit  过期时间单位（如 TimeUnit.SECONDS、TimeUnit.MINUTES）
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // ================================================================
    //  方法2：逻辑过期存储
    // ================================================================

    /**
     * 将任意 Java 对象序列化为 JSON 存入 Redis，同时设置逻辑过期时间
     * <p>与普通 TTL 不同，此方法<b>不设置 Redis TTL</b>，而是在 value 中嵌入 {@link RedisData}，
     * 包含数据本身和逻辑过期时间。适用于缓存击穿场景：</p>
     * <ul>
     *   <li>缓存永不过期（无 Redis TTL），由业务代码判断逻辑时间</li>
     *   <li>过期后异步重建缓存，立即返回旧数据，保证高并发下响应速度</li>
     *   <li>需要配合 {@link #queryWithLogicalExpire} 使用</li>
     * </ul>
     *
     * @param key   Redis 键
     * @param value 要存储的 Java 对象
     * @param time  逻辑过期时间数值
     * @param unit  逻辑过期时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // ================================================================
    //  方法3：穿透防护查询（缓存空值）
    // ================================================================

    /**
     * 根据指定 key 查询缓存，反序列化为指定类型，通过缓存空值的方式解决缓存穿透问题
     * <p>缓存穿透指请求查询数据库中不存在的数据，导致每次请求都穿透缓存直达数据库。
     * 本方法在数据库查询结果为 null 时，将空字符串作为 "空值标记" 写入 Redis，
     * 后续相同请求直接命中空缓存返回，不再查询数据库。</p>
     *
     * <b>执行流程：</b>
     * <ol>
     *   <li>查询 Redis → 命中且非空 → 反序列化返回</li>
     *   <li>查询 Redis → 命中但为空值（空字符串）→ 返回 {@code null}</li>
     *   <li>查询 Redis → 未命中 → 调用 {@code dbFallback} 查询数据库</li>
     *   <li>数据库返回 {@code null} → 缓存空值（空字符串 + TTL），返回 {@code null}</li>
     *   <li>数据库返回数据 → 缓存数据（JSON + TTL），返回数据</li>
     * </ol>
     *
     * @param keyPrefix  缓存 key 前缀，如 "cache:shop:"
     * @param id         业务 ID，与 keyPrefix 拼接为完整缓存 key
     * @param type       目标类型的 Class 对象，用于 JSON 反序列化
     * @param dbFallback 数据库查询回调函数 {@code Function<Long, T>}，入参为 id，返回实体对象
     * @param time       缓存 TTL 数值
     * @param unit       缓存 TTL 时间单位
     * @param <T>        实体类型
     * @return 查询到的实体对象；未命中或数据不存在时返回 {@code null}
     */
    public <T> T queryWithPassThrough(String keyPrefix, Long id, Class<T> type,
                                      Function<Long, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 查询 Redis 缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存命中且非空字符串 → 直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 3. 缓存命中但为空字符串（空值标记）→ 返回 null
        if (json != null) {
            return null;
        }

        // 4. 缓存未命中 → 查询数据库
        T data = dbFallback.apply(id);

        // 5. 数据库未查到数据 → 缓存空值，防止穿透
        if (data == null) {
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            return null;
        }

        // 6. 数据库查到数据 → 写入缓存并返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data), time, unit);
        return data;
    }

    // ================================================================
    //  方法4：逻辑过期查询（缓存击穿防护）
    // ================================================================

    /**
     * 根据指定 key 查询缓存，利用逻辑过期方案解决缓存击穿问题
     * <p>缓存击穿指热点 key 在过期瞬间，大量并发请求同时穿透缓存直达数据库。
     * 本方案通过逻辑过期 + 互斥锁 + 异步重建来解决：</p>
     * <ul>
     *   <li>缓存永不过期（无 Redis TTL），数据内嵌逻辑过期时间</li>
     *   <li>未过期 → 直接返回，性能最优</li>
     *   <li>已过期 → 获取互斥锁</li>
     *   <li>获取锁成功 → 提交异步线程重建缓存，立即返回旧数据（不阻塞）</li>
     *   <li>获取锁失败 → 直接返回旧数据（由已获取锁的线程负责重建）</li>
     * </ul>
     *
     * <b>前置条件：</b>缓存必须已通过 {@link #setWithLogicalExpire} 预热，否则返回 {@code null}。
     *
     * @param keyPrefix  缓存 key 前缀，如 "cache:shop:"
     * @param id         业务 ID，与 keyPrefix 拼接为完整缓存 key
     * @param type       目标类型的 Class 对象，用于 JSON 反序列化
     * @param dbFallback 数据库查询回调函数 {@code Function<Long, T>}，用于异步重建缓存
     * @param time       逻辑过期时间数值（重建缓存时使用）
     * @param unit       逻辑过期时间单位
     * @param <T>        实体类型
     * @return 查询到的实体（可能为过期数据，未命中时返回 {@code null}）
     */
    public <T> T queryWithLogicalExpire(String keyPrefix, Long id, Class<T> type,
                                        Function<Long, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 1. 从 Redis 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 缓存不存在 → 返回 null（逻辑过期要求缓存预先存在）
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 3. 反序列化，获取数据和逻辑过期时间
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 逻辑过期时间未到 → 直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return data;
        }

        // 5. 已过期 → 尝试获取互斥锁
        String lockKey = "lock:" + keyPrefix + id;
        if (tryLock(lockKey)) {
            // 获取锁成功 → 异步重建缓存，释放锁
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 再次检查缓存是否已被其他线程重建（双重检查）
                    String refreshedJson = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(refreshedJson)) {
                        RedisData refreshed = JSONUtil.toBean(refreshedJson, RedisData.class);
                        if (refreshed.getExpireTime().isAfter(LocalDateTime.now())) {
                            return;
                        }
                    }

                    // 查询数据库
                    T newData = dbFallback.apply(id);
                    if (newData == null) {
                        // 数据已不存在，删除缓存
                        stringRedisTemplate.delete(key);
                        return;
                    }

                    // 重建缓存（写入新数据 + 新逻辑过期时间）
                    setWithLogicalExpire(key, newData, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException("缓存重建异常, key=" + key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 6. 返回旧数据（不管是获取锁成功等待异步重建，还是获取锁失败，都返回当前数据）
        return data;
    }

    // ================================================================
    //  分布式锁
    // ================================================================

    /**
     * 尝试获取分布式锁（基于 Redis SETNX），使用默认超时时间 10 秒
     *
     * @param key 锁的 key
     * @return true 获取锁成功；false 获取失败（锁已被其他线程持有）
     */
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", DEFAULT_LOCK_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 尝试获取分布式锁（自定义超时时间）
     *
     * @param key  锁的 key
     * @param ttl  锁超时时间
     * @param unit 时间单位
     * @return true 获取锁成功；false 获取失败
     */
    public boolean tryLock(String key, Long ttl, TimeUnit unit) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", ttl, unit);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放分布式锁
     *
     * @param key 锁的 key
     */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 删除指定 key 的缓存
     *
     * @param key 缓存 key
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}
