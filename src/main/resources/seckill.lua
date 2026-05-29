-- 企业级秒杀 Lua 脚本
-- 原子性完成：活动时间校验 + 库存校验 + 一人一单 + 扣库存 + 已购记录 + 订单消息入队
--
-- KEYS[1] = 库存 key            seckill:stock:{voucherId}
-- KEYS[2] = 已购用户集合 key    seckill:order:{voucherId}
-- KEYS[3] = 订单 Stream key     stream:seckill:order
-- KEYS[4] = 秒杀元数据 key      seckill:voucher:{voucherId}  值格式 "beginEpochSecond,endEpochSecond"
-- ARGV[1] = 用户 ID
-- ARGV[2] = 订单 ID（由 Java RedisIdWorker 预生成，保证全局唯一）
-- ARGV[3] = 优惠券 ID
-- ARGV[4] = 当前时间戳（Unix 秒，由 Java 传入，避免跨节点时钟偏差）
--
-- 返回值：0=成功  1=秒杀尚未开始  2=秒杀已结束  3=库存不足  4=重复下单

-- ===================== 1. 活动时间校验 =====================
local meta = redis.call('get', KEYS[4])
if meta then
    local commaPos = string.find(meta, ',')
    if commaPos then
        local beginTime = tonumber(string.sub(meta, 1, commaPos - 1))
        local endTime   = tonumber(string.sub(meta, commaPos + 1))
        local now        = tonumber(ARGV[4])
        if now < beginTime then
            return 1  -- 秒杀尚未开始
        end
        if now > endTime then
            return 2  -- 秒杀已结束
        end
    end
end

-- ===================== 2. 库存校验 =====================
local stock = tonumber(redis.call('get', KEYS[1]))
if not stock or stock <= 0 then
    return 3  -- 库存不足
end

-- ===================== 3. 一人一单校验 =====================
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 4  -- 重复下单
end

-- ===================== 4. 扣减库存 =====================
redis.call('decr', KEYS[1])

-- ===================== 5. 记录已购用户（防重复下单） =====================
redis.call('sadd', KEYS[2], ARGV[1])

-- ===================== 6. 订单消息入 Stream 队 =====================
-- 消息体包含 orderId / userId / voucherId，供消费者解析落库
redis.call('xadd', KEYS[3], '*',
    'orderId',   ARGV[2],
    'userId',    ARGV[1],
    'voucherId', ARGV[3])

return 0
