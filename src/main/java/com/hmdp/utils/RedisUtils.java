package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis工具类，提供Redis操作的便捷方法
 * 包含基础操作、缓存穿透保护、分布式锁、逻辑过期、Hash操作等功能
 */
@Component
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造函数，注入StringRedisTemplate
     * @param stringRedisTemplate Redis模板
     */
    public RedisUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ==================== 基础操作 ====================

    /**
     * 设置键值对，自动将对象转换为JSON字符串存储
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value));
    }

    /**
     * 设置键值对并指定过期时间，自动将对象转换为JSON字符串存储
     * @param key 键
     * @param value 值
     * @param ttl 过期时间
     * @param unit 时间单位
     */
    public void set(String key, Object value, Long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl, unit);
    }

    /**
     * 设置字符串键值对
     * @param key 键
     * @param value 值
     */
    public void setPlain(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串键值对并指定过期时间
     * @param key 键
     * @param value 值
     * @param ttl 过期时间
     * @param unit 时间单位
     */
    public void setPlain(String key, String value, Long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    /**
     * 获取值并转换为指定类型
     * @param key 键
     * @param type 目标类型
     * @return 转换后的对象
     */
    public <T> T get(String key, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) return null;
        return JSONUtil.toBean(json, type);
    }

    /**
     * 获取字符串值
     * @param key 键
     * @return 字符串值
     */
    public String getPlain(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 获取列表类型的值
     * @param key 键
     * @param type 列表元素类型
     * @return 列表
     */
    public <T> List<T> getList(String key, Class<T> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) return null;
        return JSONUtil.toList(json, type);
    }

    /**
     * 删除键
     * @param key 键
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    // ==================== 缓存穿透保护 ====================

    /**
     * 设置空值，用于防止缓存穿透
     * @param key 键
     * @param ttl 过期时间
     * @param unit 时间单位
     */
    public void setNull(String key, Long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, "", ttl, unit);
    }

    /**
     * 判断是否为空值，用于防止缓存穿透
     * @param key 键
     * @return 是否为空值
     */
    public boolean isNull(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        return json != null && json.isEmpty();
    }

    // ==================== 分布式锁 ====================

    /**
     * 尝试获取分布式锁
     * @param key 锁键
     * @param ttl 锁过期时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String key, Long ttl, TimeUnit unit) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", ttl, unit);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放分布式锁
     * @param key 锁键
     */
    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    // ==================== 逻辑过期 ====================

    /**
     * 设置带有逻辑过期时间的值
     * @param key 键
     * @param value 值
     * @param time 过期时间
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 获取带有逻辑过期时间的值
     * @param key 键
     * @return RedisData对象
     */
    public RedisData getWithLogicalExpire(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) return null;
        return JSONUtil.toBean(json, RedisData.class);
    }

    // ==================== Hash操作 ====================

    /**
     * 设置Hash表
     * @param key 键
     * @param map Hash表
     */
    public void hashPutAll(String key, Map<String, String> map) {
        stringRedisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 设置Hash表并指定过期时间
     * @param key 键
     * @param map Hash表
     * @param ttl 过期时间
     * @param unit 时间单位
     */
    public void hashPutAll(String key, Map<String, String> map, Long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, ttl, unit);
    }

    /**
     * 获取Hash表的所有键值对
     * @param key 键
     * @return Hash表
     */
    public Map<Object, Object> hashEntries(String key) {
        return stringRedisTemplate.opsForHash().entries(key);
    }

    /**
     * 设置键的过期时间
     * @param key 键
     * @param ttl 过期时间
     * @param unit 时间单位
     */
    public void expire(String key, Long ttl, TimeUnit unit) {
        stringRedisTemplate.expire(key, ttl, unit);
    }
}
